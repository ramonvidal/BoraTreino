package com.boratreino.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.Record
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.settings.TrainingSettings
import java.time.Clock
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Suggestion
import com.boratreino.core.model.LoggingRules
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.WorkoutSet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The set being fixed in the bottom sheet, with its own stepper values. [number] is its row number (1-based). */
internal data class SetEditor(val set: WorkoutSet, val number: Int, val weightKg: Double, val reps: Int)

/** A logged cardio set being fixed in the bottom sheet; [draft] is the stepper values. */
internal data class CardioEditor(val set: WorkoutSet, val number: Int, val draft: CardioDraft)

/** Which value the number pad is editing. */
internal enum class PadTarget { WEIGHT, REPS, EDIT_WEIGHT, EDIT_REPS }

/** The number pad dialog: what it edits and the text it starts with. */
internal data class NumberPad(val target: PadTarget, val initial: String)

/**
 * Everything the machine screen shows. [weightKg] and [reps] are the steppers' current values.
 * [sameAgain] is last time's matching set, or null (no button). [machine] is null while loading
 * ([loaded] false) or when the machine doesn't exist ([loaded] true).
 */
internal data class MachineUiState(
    val loaded: Boolean = false,
    val machine: Machine? = null,
    val lastTime: SetSummary? = null,
    val lastTimeAt: Instant? = null,
    val sets: List<WorkoutSet> = emptyList(),
    val weightKg: Double = 0.0,
    val reps: Int = LoggingRules.DEFAULT_REPS,
    val sameAgain: SetDraft? = null,
    val editor: SetEditor? = null,
    val pad: NumberPad? = null,
    /** This machine's target in the active routine, if it is in it. */
    val target: RoutineItem? = null,
    /** "Try 125 kg" (up) or the way back down; the weight stepper starts on it. */
    val suggestion: Suggestion? = null,
    /** The record just set, shown for a few seconds. */
    val record: Record? = null,
    /** Cardio machines: the steppers' current values (minutes, calories, distance). */
    val cardio: CardioDraft = CardioDraft(CardioRules.DEFAULT_MINUTES, 0, 0),
    /** Cardio: last time's matching set, or null (no button). */
    val cardioSameAgain: CardioDraft? = null,
    /** Cardio: the distance stepper is shown (last time had a distance, or "Add distance" was tapped). */
    val cardioDistanceShown: Boolean = false,
    val cardioEditor: CardioEditor? = null,
    /** Whole seconds of rest left; null = no timer running. */
    val restSeconds: Int? = null,
    /** Length of the running rest in seconds (for the progress bar); 0 when unknown. */
    val restTotalSeconds: Int = 0,
)

internal sealed interface MachineEvent {
    /** The set is written; the screen gives the haptic tick (a stronger one when [record] is set). */
    data class Logged(val record: Record? = null) : MachineEvent

    /** The set was deleted; the screen offers Undo. */
    data class SetRemoved(val set: WorkoutSet) : MachineEvent
    data object Failed : MachineEvent
}

private const val RECORD_SHOWN_MILLIS = 4_000L

@HiltViewModel
class MachineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val training: TrainingRepository,
    private val restTimer: RestTimer,
    private val settings: TrainingSettings,
    private val clock: Clock,
) : ViewModel() {

    private val machineId: Long = checkNotNull(savedStateHandle.get<Long>(MACHINE_ID_ARG)) { "machineId missing" }

    private val eventChannel = Channel<MachineEvent>(Channel.BUFFERED)
    internal val events: Flow<MachineEvent> = eventChannel.receiveAsFlow()

    /** What the user dialled in; null = follow [LoggingRules.prefill]. Cleared after each logged set. */
    private val draftOverride = MutableStateFlow<SetDraft?>(null)
    private val editor = MutableStateFlow<SetEditor?>(null)
    private val cardioOverride = MutableStateFlow<CardioDraft?>(null)
    private val distanceRevealed = MutableStateFlow(false)
    private val cardioEditor = MutableStateFlow<CardioEditor?>(null)
    private val pad = MutableStateFlow<NumberPad?>(null)
    private val record = MutableStateFlow<Record?>(null)

    /** Fires once when a rest countdown reaches zero; the screen gives a haptic. */
    internal val restFinished: Flow<Unit> = restTimer.finished

    /** The seconds left, ticking once a second while a timer runs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val restSeconds: Flow<Pair<Int, Int>?> = restTimer.state.flatMapLatest { timer ->
        val endsAt = timer.endsAt
        val total = timer.durationSeconds
        if (endsAt == null) {
            flowOf(null)
        } else {
            flow {
                while (true) {
                    val left = RestTimerRules.remainingSeconds(endsAt, clock.instant())
                    if (left <= 0) {
                        emit(null)
                        break
                    }
                    emit(left to maxOf(total, left))
                    delay(1_000)
                }
            }
        }
    }

    private data class CardioInput(
        val override: CardioDraft?,
        val revealed: Boolean,
        val editor: CardioEditor?,
    )

    private val cardioInput: Flow<CardioInput> = combine(cardioOverride, distanceRevealed, cardioEditor, ::CardioInput)

    private data class Inputs(
        val override: SetDraft?,
        val editing: SetEditor?,
        val padding: NumberPad?,
        val newRecord: Record?,
        val cardio: CardioInput,
    )

    private val inputs: Flow<Inputs> = combine(draftOverride, editor, pad, record, cardioInput, ::Inputs)

    private val logState: Flow<MachineUiState> = combine(
        training.observeMachineLog(machineId),
        inputs,
    ) { log, input ->
        val override = input.override
        val editing = input.editing
        val padding = input.padding
        val newRecord = input.newRecord
        val cardio = input.cardio
        if (log == null) {
            MachineUiState(loaded = true)
        } else if (log.machine.kind == MachineKind.CARDIO) {
            val prefill = CardioRules.prefill(log.lastTime, log.thisSession)
            MachineUiState(
                loaded = true,
                machine = log.machine,
                lastTime = SetSummary.of(log.lastTime),
                lastTimeAt = log.lastTimeAt,
                sets = log.thisSession,
                target = log.target,
                cardio = cardio.override ?: prefill,
                cardioSameAgain = CardioRules.sameAgain(log.lastTime, log.thisSession),
                cardioDistanceShown = cardio.revealed || prefill.distanceM > 0,
                cardioEditor = cardio.editor,
            )
        } else {
            val draft = override ?: LoggingRules.prefill(log.lastTime, log.thisSession, log.suggestion)
            MachineUiState(
                loaded = true,
                machine = log.machine,
                lastTime = SetSummary.of(log.lastTime),
                lastTimeAt = log.lastTimeAt,
                sets = log.thisSession,
                weightKg = draft.weightKg,
                reps = draft.reps,
                sameAgain = LoggingRules.sameAgain(log.lastTime, log.thisSession),
                editor = editing,
                pad = padding,
                target = log.target,
                suggestion = log.suggestion,
                record = newRecord,
            )
        }
    }

    internal val state: StateFlow<MachineUiState> = combine(logState, restSeconds) { ui, rest ->
        ui.copy(restSeconds = rest?.first, restTotalSeconds = rest?.second ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachineUiState())

    // Logging: the write finishes first, then the event (haptic) and the fresh prefill.

    internal fun logSet() {
        val s = state.value
        write { log(SetDraft(s.weightKg, s.reps)) }
    }

    internal fun logSameAgain() {
        val draft = state.value.sameAgain ?: return
        write { log(draft) }
    }

    private suspend fun log(draft: SetDraft) {
        val machine = state.value.machine
        val logged = training.logSet(machineId, draft)
        draftOverride.value = null
        eventChannel.send(MachineEvent.Logged(logged.record))
        startRest(machine)
        val newRecord = logged.record ?: return
        record.value = newRecord
        viewModelScope.launch {
            delay(RECORD_SHOWN_MILLIS)
            if (record.value == newRecord) record.value = null
        }
    }

    /** After the write: starts the rest countdown when it is on. Never lets a failure here touch logging. */
    private suspend fun startRest(machine: Machine?) {
        if (machine == null) return
        try {
            val rest = settings.restTimer.first()
            if (rest.enabled) restTimer.start(RestTimerRules.durationFor(machine, rest.defaultSeconds))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The timer is a comfort; the set is already saved.
        }
    }

    // Cardio: minutes, calories and (optional) distance steppers. No rest timer, no records.

    internal fun logCardio() {
        val draft = state.value.cardio
        if (!CardioRules.canLog(draft)) return
        write { logCardioDraft(draft) }
    }

    internal fun logCardioSameAgain() {
        val draft = state.value.cardioSameAgain ?: return
        write { logCardioDraft(draft) }
    }

    private suspend fun logCardioDraft(draft: CardioDraft) {
        training.logCardio(machineId, draft)
        cardioOverride.value = null
        eventChannel.send(MachineEvent.Logged())
    }

    internal fun stepMinutes(up: Boolean) {
        val d = state.value.cardio
        cardioOverride.value = d.copy(minutes = CardioRules.stepMinutes(d.minutes, up))
    }

    internal fun stepCalories(up: Boolean) {
        val d = state.value.cardio
        cardioOverride.value = d.copy(calories = CardioRules.stepCalories(d.calories, up))
    }

    internal fun stepDistance(up: Boolean) {
        val d = state.value.cardio
        cardioOverride.value = d.copy(distanceM = CardioRules.stepDistance(d.distanceM, up))
    }

    internal fun showDistance() {
        distanceRevealed.value = true
    }

    internal fun openCardioSet(set: WorkoutSet) {
        val number = state.value.sets.indexOfFirst { it.id == set.id } + 1
        cardioEditor.value = CardioEditor(set, number, CardioRules.toDraft(set))
    }

    internal fun closeCardioEditor() {
        cardioEditor.value = null
    }

    internal fun stepEditorMinutes(up: Boolean) = editCardio { it.copy(minutes = CardioRules.stepMinutes(it.minutes, up)) }

    internal fun stepEditorCalories(up: Boolean) = editCardio { it.copy(calories = CardioRules.stepCalories(it.calories, up)) }

    internal fun stepEditorDistance(up: Boolean) = editCardio { it.copy(distanceM = CardioRules.stepDistance(it.distanceM, up)) }

    private fun editCardio(change: (CardioDraft) -> CardioDraft) {
        cardioEditor.value = cardioEditor.value?.let { it.copy(draft = change(it.draft)) }
    }

    internal fun saveCardioEditor() {
        val e = cardioEditor.value ?: return
        cardioEditor.value = null
        val s = e.set
        write { training.updateSet(CardioRules.toSet(s.id, s.sessionId, s.machineId, s.position, e.draft, s.loggedAt)) }
    }

    internal fun deleteCardioEditor() {
        val e = cardioEditor.value ?: return
        cardioEditor.value = null
        write {
            training.deleteSet(e.set)
            eventChannel.send(MachineEvent.SetRemoved(e.set))
        }
    }

    internal fun skipRest() = restTimer.skip()

    internal fun extendRest() = restTimer.extend()

    internal fun stepWeight(up: Boolean) {
        val s = state.value
        val machine = s.machine ?: return
        draftOverride.value = SetDraft(LoggingRules.stepWeight(s.weightKg, machine.stepKg, up), s.reps)
    }

    internal fun stepReps(up: Boolean) {
        val s = state.value
        draftOverride.value = SetDraft(s.weightKg, LoggingRules.stepReps(s.reps, up))
    }

    // Number pad (first-time setup; the steppers stay the main path).

    internal fun openPad(target: PadTarget) {
        val s = state.value
        val (weight, reps) = s.editor?.let { it.weightKg to it.reps } ?: (s.weightKg to s.reps)
        pad.value = NumberPad(
            target,
            when (target) {
                PadTarget.WEIGHT, PadTarget.EDIT_WEIGHT -> weight.toBigDecimal().stripTrailingZeros().toPlainString()
                PadTarget.REPS, PadTarget.EDIT_REPS -> reps.toString()
            },
        )
    }

    internal fun dismissPad() {
        pad.value = null
    }

    internal fun confirmPad(text: String) {
        val target = pad.value?.target ?: return
        pad.value = null
        val s = state.value
        when (target) {
            PadTarget.WEIGHT -> parseWeight(text)?.let { draftOverride.value = SetDraft(it, s.reps) }
            PadTarget.REPS -> parseReps(text)?.let { draftOverride.value = SetDraft(s.weightKg, it) }
            PadTarget.EDIT_WEIGHT -> parseWeight(text)?.let { w -> editor.value = editor.value?.copy(weightKg = w) }
            PadTarget.EDIT_REPS -> parseReps(text)?.let { r -> editor.value = editor.value?.copy(reps = r) }
        }
    }

    private fun parseWeight(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, LoggingRules.MAX_WEIGHT_KG)

    private fun parseReps(text: String): Int? =
        text.trim().toIntOrNull()?.coerceIn(1, LoggingRules.MAX_REPS)

    // Fixing a logged set.

    internal fun openSet(set: WorkoutSet) {
        val number = state.value.sets.indexOfFirst { it.id == set.id } + 1
        editor.value = SetEditor(set, number, set.weightKg ?: 0.0, set.reps ?: LoggingRules.DEFAULT_REPS)
    }

    internal fun closeEditor() {
        editor.value = null
    }

    internal fun stepEditorWeight(up: Boolean) {
        val machine = state.value.machine ?: return
        editor.value = editor.value?.let { it.copy(weightKg = LoggingRules.stepWeight(it.weightKg, machine.stepKg, up)) }
    }

    internal fun stepEditorReps(up: Boolean) {
        editor.value = editor.value?.let { it.copy(reps = LoggingRules.stepReps(it.reps, up)) }
    }

    internal fun saveEditor() {
        val e = editor.value ?: return
        editor.value = null
        write { training.updateSet(e.set.copy(weightKg = e.weightKg, reps = e.reps)) }
    }

    internal fun deleteEditor() {
        val e = editor.value ?: return
        editor.value = null
        write {
            training.deleteSet(e.set)
            eventChannel.send(MachineEvent.SetRemoved(e.set))
        }
    }

    internal fun restoreSet(set: WorkoutSet) {
        write { training.restoreSet(set) }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                eventChannel.send(MachineEvent.Failed)
            }
        }
    }
}

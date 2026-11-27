package com.boratreino.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RemovedSets
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioField
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
import com.boratreino.core.model.SetMeasure
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.SetType
import com.boratreino.core.model.WarmupRules
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.model.measure
import com.boratreino.core.model.working
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
internal data class SetEditor(
    val set: WorkoutSet, val number: Int, val weightKg: Double, val reps: Int, val type: SetType = set.type,
    val seconds: Int = LoggingRules.DEFAULT_SECONDS, val distanceM: Int = LoggingRules.DEFAULT_DISTANCE_M,
)

/** A logged cardio set being fixed in the bottom sheet; [draft] is the stepper values. */
internal data class CardioEditor(val set: WorkoutSet, val number: Int, val draft: CardioDraft)

/** Which value the number pad is editing. */
internal enum class PadTarget { WEIGHT, REPS, SECONDS, DISTANCE, EDIT_WEIGHT, EDIT_REPS, EDIT_SECONDS, EDIT_DISTANCE, CARDIO, EDIT_CARDIO }

/** The number pad dialog: what it edits and the text it starts with. */
internal data class NumberPad(val target: PadTarget, val initial: String, val field: CardioField? = null)

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
    /** The sets of the previous session on this machine, in order (for the Previous column). */
    val lastTimeSets: List<WorkoutSet> = emptyList(),
    val sets: List<WorkoutSet> = emptyList(),
    val weightKg: Double = 0.0,
    val reps: Int = LoggingRules.DEFAULT_REPS,
    /** Time kinds: the seconds stepper's value. */
    val seconds: Int = LoggingRules.DEFAULT_SECONDS,
    /** Distance kinds: the metres stepper's value. */
    val distanceM: Int = LoggingRules.DEFAULT_DISTANCE_M,
    val sameAgain: SetDraft? = null,
    /** The Warm-up chip is on: the next logged set is a warm-up. Turns itself off after each log. */
    val warmupOn: Boolean = false,
    /** The type the next logged set gets (chosen under "Set type"); [SetType.NORMAL] unless changed. Resets after each log. */
    val logType: SetType = SetType.NORMAL,
    /** The optional warm-up sets offered for the current stepper weight; empty = no offer. */
    val warmupPlan: List<SetDraft> = emptyList(),
    /** The "Warm-up sets" dialog is open (only while [warmupPlan] is non-empty). */
    val warmupDialog: Boolean = false,
    val editor: SetEditor? = null,
    val pad: NumberPad? = null,
    /** This machine's target in the active routine, if it is in it. */
    val target: RoutineItem? = null,
    /** "Try 125 kg" (up) or the way back down; the weight stepper starts on it. */
    val suggestion: Suggestion? = null,
    /** The record just set, shown for a few seconds. */
    val record: Record? = null,
    /** Cardio machines: the steppers' current values (minutes, calories, distance, intensity). */
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
    /** Working sets this machine is programmed for in the active routine; null = not in it. */
    val programmedSets: Int? = null,
    /** Working sets (warm-ups excluded) of this machine in the open session. */
    val doneSets: Int = 0,
    /** The remaining exercises of the active routine (not complete, not skipped, not this one), in order. */
    val nextExercises: List<NextExerciseUi> = emptyList(),
    /** "Add an extra set" was tapped on this visit: the Log panel is back although the exercise is complete. */
    val extraSet: Boolean = false,
) {
    /** The exercise is in the routine and has all its programmed working sets. */
    val exerciseComplete: Boolean get() = programmedSets != null && doneSets >= programmedSets
}

/** One of the exercises still to do in the active routine, offered when this one is complete. */
internal data class NextExerciseUi(val machine: Machine, val target: RoutineItem)

/** At most this many next exercises are offered. */
private const val NEXT_EXERCISES_SHOWN = 4

internal sealed interface MachineEvent {
    /** The set is written; the screen gives the haptic tick (a stronger one when [record] is set). */
    data class Logged(val record: Record? = null) : MachineEvent

    /** The set was deleted; the screen offers Undo. */
    data class SetRemoved(val removed: RemovedSets) : MachineEvent

    /** The warm-up sets are written; the screen offers Undo with [outcome]. */
    data class WarmupsLogged(val outcome: RepeatOutcome) : MachineEvent
    data object Failed : MachineEvent

    /** The logged set completed the whole routine and the training finished by itself; the screen goes back to Today. */
    data object RoutineFinished : MachineEvent
}

private const val RECORD_SHOWN_MILLIS = 4_000L

@HiltViewModel
class MachineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val training: TrainingRepository,
    machines: MachineRepository,
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
    private val warmupOn = MutableStateFlow(false)
    private val logType = MutableStateFlow(SetType.NORMAL)
    private val warmupDialog = MutableStateFlow(false)
    private val extraSet = MutableStateFlow(false)

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

    private data class Toggles(val warmupOn: Boolean, val warmupDialog: Boolean, val extraSet: Boolean, val logType: SetType)

    private val toggles: Flow<Toggles> = combine(warmupOn, warmupDialog, extraSet, logType, ::Toggles)

    /** The routine's other exercises still to do, in order; empty when the machine is not in the routine. */
    private val nextExercises: Flow<List<NextExerciseUi>> = combine(
        training.observeTodayPlan(),
        training.observeOpenSession(),
        machines.observeMachines(includeArchived = true),
    ) { plan, open, all ->
        val done = open?.machines.orEmpty().associate { it.machine.id to it.sets.working().size }
        val byId = all.associateBy { it.id }
        plan.active?.items.orEmpty()
            .sortedBy { it.position }
            .filter { it.machineId != machineId && it.machineId !in plan.skipped && (done[it.machineId] ?: 0) < it.targetSets }
            .mapNotNull { item -> byId[item.machineId]?.let { NextExerciseUi(it, item) } }
            .take(NEXT_EXERCISES_SHOWN)
    }

    private val logState: Flow<MachineUiState> = combine(
        training.observeMachineLog(machineId),
        inputs,
        toggles,
        settings.warmupCalculator,
        nextExercises,
    ) { log, input, toggle, calculator, next ->
        val warmup = toggle.warmupOn
        val dialog = toggle.warmupDialog
        val override = input.override
        val editing = input.editing
        val padding = input.padding
        val newRecord = input.newRecord
        val cardio = input.cardio
        val base = if (log == null) {
            MachineUiState(loaded = true)
        } else if (log.machine.kind == MachineKind.CARDIO) {
            val prefill = CardioRules.prefill(log.lastTime, log.thisSession)
            MachineUiState(
                loaded = true,
                machine = log.machine,
                lastTime = SetSummary.of(log.lastTime),
                lastTimeAt = log.lastTimeAt,
                lastTimeSets = log.lastTime,
                sets = log.thisSession,
                target = log.target,
                cardio = cardio.override ?: prefill,
                cardioSameAgain = CardioRules.sameAgain(log.lastTime, log.thisSession),
                cardioDistanceShown = cardio.revealed || prefill.distanceM > 0,
                cardioEditor = cardio.editor,
                pad = padding,
            )
        } else {
            val kind = log.machine.kind
            val draft = override ?: LoggingRules.prefill(log.lastTime, log.thisSession, log.suggestion, kind)
            val warmupPlan = if (calculator && kind.measure == SetMeasure.REPS && log.thisSession.isEmpty() && draft.weightKg > 0.0) {
                WarmupRules.plan(draft.weightKg, log.machine.stepKg)
            } else {
                emptyList()
            }
            MachineUiState(
                loaded = true,
                machine = log.machine,
                lastTime = SetSummary.of(log.lastTime),
                lastTimeAt = log.lastTimeAt,
                lastTimeSets = log.lastTime,
                sets = log.thisSession,
                weightKg = draft.weightKg,
                reps = draft.reps,
                seconds = draft.seconds,
                distanceM = draft.distanceM,
                sameAgain = LoggingRules.sameAgain(log.lastTime, log.thisSession, kind),
                warmupOn = warmup,
                logType = toggle.logType,
                warmupPlan = warmupPlan,
                warmupDialog = dialog && warmupPlan.isNotEmpty(),
                editor = editing,
                pad = padding,
                target = log.target,
                suggestion = log.suggestion,
                record = newRecord,
            )
        }
        base.copy(
            programmedSets = log?.target?.targetSets,
            doneSets = log?.thisSession.orEmpty().working().size,
            nextExercises = if (log?.target != null) next else emptyList(),
            extraSet = toggle.extraSet,
        )
    }

    internal val state: StateFlow<MachineUiState> = combine(logState, restSeconds) { ui, rest ->
        ui.copy(restSeconds = rest?.first, restTotalSeconds = rest?.second ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachineUiState())

    // Logging: the write finishes first, then the event (haptic) and the fresh prefill.

    internal fun logSet() {
        val s = state.value
        val machine = s.machine ?: return
        if (!LoggingRules.canLog(draftOf(s), machine.kind)) return
        write { log(draftOf(s).copy(type = typeFor(s))) }
    }

    internal fun logSameAgain() {
        val draft = state.value.sameAgain ?: return
        write { log(draft.copy(type = typeFor(state.value))) }
    }

    internal fun toggleWarmup() {
        warmupOn.value = !warmupOn.value
    }

    /** "Set type" sheet: the next logged set gets [type]. */
    internal fun setLogType(type: SetType) {
        logType.value = type
        warmupOn.value = false
    }

    /** The "Try 125 kg" chip: the steppers jump to the suggestion (still optional; nothing is logged). */
    internal fun applySuggestion() {
        val suggestion = state.value.suggestion ?: return
        draftOverride.value = SetDraft(suggestion.weightKg, suggestion.reps)
    }

    // Optional warm-up sets: one tap in the dialog logs them all; Undo goes through undoRepeat.

    internal fun openWarmups() {
        if (state.value.warmupPlan.isNotEmpty()) warmupDialog.value = true
    }

    internal fun closeWarmups() {
        warmupDialog.value = false
    }

    internal fun logWarmups() {
        val plan = state.value.warmupPlan
        warmupDialog.value = false
        if (plan.isEmpty()) return
        write {
            val outcome = training.logWarmups(machineId, plan)
            eventChannel.send(MachineEvent.WarmupsLogged(outcome))
        }
    }

    internal fun undoWarmups(outcome: RepeatOutcome) {
        write { training.undoRepeat(outcome) }
    }

    private fun typeFor(s: MachineUiState): SetType =
        if (s.logType != SetType.NORMAL) s.logType else if (s.warmupOn) SetType.WARMUP else SetType.NORMAL

    /** The working set about to be logged is the one that completes (or follows) the programmed sets of this exercise. */
    private fun completesExercise(working: Boolean): Boolean {
        val s = state.value
        val programmed = s.programmedSets ?: return false
        return working && s.doneSets + 1 >= programmed
    }

    /** Stops a rest still running from the previous set. Never lets a failure here touch logging. */
    private fun stopRest() {
        try {
            restTimer.skip()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The timer is a comfort; the set is already saved.
        }
    }

    internal fun showExtraSet() {
        extraSet.value = true
    }

    private suspend fun log(draft: SetDraft) {
        val machine = state.value.machine
        // Decided from the state before the write.
        val completes = completesExercise(draft.type != SetType.WARMUP)
        val logged = training.logSet(machineId, draft)
        draftOverride.value = null
        warmupOn.value = false
        logType.value = SetType.NORMAL
        eventChannel.send(MachineEvent.Logged(logged.record))
        if (completes) stopRest() else startRest(machine)
        if (draft.type != SetType.WARMUP) finishIfComplete()
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
        val completes = completesExercise(working = true)
        training.logCardio(machineId, draft)
        cardioOverride.value = null
        eventChannel.send(MachineEvent.Logged())
        if (completes) stopRest()
        finishIfComplete()
    }

    /**
     * After a working set is written: when it completed every item of the routine the repository finishes the
     * training (as Done does), the rest timer stops and the screen goes back to Today. A failure here is reported
     * by [write]; the set is already saved.
     */
    private suspend fun finishIfComplete() {
        training.finishIfRoutineComplete() ?: return
        try {
            restTimer.skip()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Finishing the session matters more than the timer.
        }
        eventChannel.send(MachineEvent.RoutineFinished)
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

    internal fun stepIntensity(up: Boolean) {
        val d = state.value.cardio
        cardioOverride.value = d.copy(intensity = CardioRules.stepIntensity(d.intensity, up))
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

    internal fun stepEditorIntensity(up: Boolean) = editCardio { it.copy(intensity = CardioRules.stepIntensity(it.intensity, up)) }

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
            eventChannel.send(MachineEvent.SetRemoved(training.deleteSet(e.set)))
        }
    }

    internal fun skipRest() = restTimer.skip()

    internal fun extendRest() = restTimer.extend()

    /** The steppers' current values as a draft (type left NORMAL; [logSet] adds the chosen type). */
    private fun draftOf(s: MachineUiState): SetDraft = SetDraft(s.weightKg, s.reps, SetType.NORMAL, s.seconds, s.distanceM)

    internal fun stepWeight(up: Boolean) {
        val s = state.value
        val machine = s.machine ?: return
        draftOverride.value = draftOf(s).copy(weightKg = LoggingRules.stepWeight(s.weightKg, machine.stepKg, up))
    }

    internal fun stepReps(up: Boolean) {
        val s = state.value
        draftOverride.value = draftOf(s).copy(reps = LoggingRules.stepReps(s.reps, up))
    }

    internal fun stepSeconds(up: Boolean) {
        val s = state.value
        draftOverride.value = draftOf(s).copy(seconds = LoggingRules.stepSeconds(s.seconds, up))
    }

    internal fun stepMetres(up: Boolean) {
        val s = state.value
        draftOverride.value = draftOf(s).copy(distanceM = LoggingRules.stepDistance(s.distanceM, up))
    }

    // Number pad (first-time setup; the steppers stay the main path).

    internal fun openPad(target: PadTarget) {
        val s = state.value
        val e = s.editor
        val weight = e?.weightKg ?: s.weightKg
        val reps = e?.reps ?: s.reps
        val seconds = e?.seconds ?: s.seconds
        val distance = e?.distanceM ?: s.distanceM
        pad.value = NumberPad(
            target,
            when (target) {
                PadTarget.WEIGHT, PadTarget.EDIT_WEIGHT -> weight.toBigDecimal().stripTrailingZeros().toPlainString()
                PadTarget.REPS, PadTarget.EDIT_REPS -> reps.toString()
                PadTarget.SECONDS, PadTarget.EDIT_SECONDS -> seconds.toString()
                PadTarget.DISTANCE, PadTarget.EDIT_DISTANCE -> distance.toString()
                PadTarget.CARDIO, PadTarget.EDIT_CARDIO -> ""
            },
        )
    }

    /** Opens the pad on a cardio stepper value; [inEditor] is true for the logged-set sheet's draft. */
    internal fun openCardioPad(field: CardioField, inEditor: Boolean = false) {
        val draft = (if (inEditor) cardioEditor.value?.draft else state.value.cardio) ?: return
        pad.value = NumberPad(
            if (inEditor) PadTarget.EDIT_CARDIO else PadTarget.CARDIO,
            CardioRules.fieldText(draft, field),
            field,
        )
    }

    internal fun dismissPad() {
        pad.value = null
    }

    internal fun confirmPad(text: String) {
        val open = pad.value ?: return
        val target = open.target
        pad.value = null
        val s = state.value
        when (target) {
            PadTarget.CARDIO -> open.field?.let { field ->
                CardioRules.withField(s.cardio, field, text)?.let {
                    cardioOverride.value = it
                    if (field == CardioField.DISTANCE_KM && it.distanceM > 0) distanceRevealed.value = true
                }
            }
            PadTarget.EDIT_CARDIO -> open.field?.let { field ->
                editCardio { d -> CardioRules.withField(d, field, text) ?: d }
            }
            PadTarget.WEIGHT -> parseWeight(text)?.let { draftOverride.value = draftOf(s).copy(weightKg = it) }
            PadTarget.REPS -> parseReps(text)?.let { draftOverride.value = draftOf(s).copy(reps = it) }
            PadTarget.SECONDS -> parseWhole(text, LoggingRules.MAX_SECONDS)?.let { draftOverride.value = draftOf(s).copy(seconds = it) }
            PadTarget.DISTANCE -> parseWhole(text, LoggingRules.MAX_DISTANCE_M)?.let { draftOverride.value = draftOf(s).copy(distanceM = it) }
            PadTarget.EDIT_WEIGHT -> parseWeight(text)?.let { w -> editor.value = editor.value?.copy(weightKg = w) }
            PadTarget.EDIT_REPS -> parseReps(text)?.let { r -> editor.value = editor.value?.copy(reps = r) }
            PadTarget.EDIT_SECONDS -> parseWhole(text, LoggingRules.MAX_SECONDS)?.let { v -> editor.value = editor.value?.copy(seconds = v) }
            PadTarget.EDIT_DISTANCE -> parseWhole(text, LoggingRules.MAX_DISTANCE_M)?.let { v -> editor.value = editor.value?.copy(distanceM = v) }
        }
    }

    private fun parseWeight(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, LoggingRules.MAX_WEIGHT_KG)

    /** A whole number clamped to 1..[max]; null when [text] isn't one. */
    private fun parseWhole(text: String, max: Int): Int? =
        text.trim().toIntOrNull()?.coerceIn(1, max)

    private fun parseReps(text: String): Int? =
        text.trim().toIntOrNull()?.coerceIn(1, LoggingRules.MAX_REPS)

    // Fixing a logged set.

    internal fun openSet(set: WorkoutSet) {
        val number = state.value.sets.indexOfFirst { it.id == set.id } + 1
        val kind = state.value.machine?.kind ?: MachineKind.WEIGHT_REPS
        val draft = LoggingRules.draftOf(set, kind)
        editor.value = SetEditor(set, number, draft.weightKg, draft.reps, set.type, draft.seconds, draft.distanceM)
    }

    internal fun setEditorType(type: SetType) {
        editor.value = editor.value?.copy(type = type)
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

    internal fun stepEditorSeconds(up: Boolean) {
        editor.value = editor.value?.let { it.copy(seconds = LoggingRules.stepSeconds(it.seconds, up)) }
    }

    internal fun stepEditorMetres(up: Boolean) {
        editor.value = editor.value?.let { it.copy(distanceM = LoggingRules.stepDistance(it.distanceM, up)) }
    }

    internal fun saveEditor() {
        val e = editor.value ?: return
        val kind = state.value.machine?.kind ?: MachineKind.WEIGHT_REPS
        editor.value = null
        val draft = SetDraft(e.weightKg, e.reps, e.type, e.seconds, e.distanceM)
        write { training.updateSet(LoggingRules.applyDraft(e.set, draft, kind)) }
    }

    internal fun deleteEditor() {
        val e = editor.value ?: return
        editor.value = null
        write {
            eventChannel.send(MachineEvent.SetRemoved(training.deleteSet(e.set)))
        }
    }

    /** Long-press menu on a logged set row: deletes it at once; the screen offers Undo. */
    internal fun deleteSet(set: WorkoutSet) {
        write {
            eventChannel.send(MachineEvent.SetRemoved(training.deleteSet(set)))
        }
    }

    internal fun restoreSets(removed: RemovedSets) {
        write { training.restoreSets(removed) }
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

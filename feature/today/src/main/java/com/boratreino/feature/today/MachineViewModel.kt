package com.boratreino.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.Record
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The set being fixed in the bottom sheet, with its own stepper values. [number] is its row number (1-based). */
internal data class SetEditor(val set: WorkoutSet, val number: Int, val weightKg: Double, val reps: Int)

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
) : ViewModel() {

    private val machineId: Long = checkNotNull(savedStateHandle.get<Long>(MACHINE_ID_ARG)) { "machineId missing" }

    private val eventChannel = Channel<MachineEvent>(Channel.BUFFERED)
    internal val events: Flow<MachineEvent> = eventChannel.receiveAsFlow()

    /** What the user dialled in; null = follow [LoggingRules.prefill]. Cleared after each logged set. */
    private val draftOverride = MutableStateFlow<SetDraft?>(null)
    private val editor = MutableStateFlow<SetEditor?>(null)
    private val pad = MutableStateFlow<NumberPad?>(null)
    private val record = MutableStateFlow<Record?>(null)

    internal val state: StateFlow<MachineUiState> = combine(
        training.observeMachineLog(machineId),
        draftOverride,
        editor,
        pad,
        record,
    ) { log, override, editing, padding, newRecord ->
        if (log == null) {
            MachineUiState(loaded = true)
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
        val logged = training.logSet(machineId, draft)
        draftOverride.value = null
        eventChannel.send(MachineEvent.Logged(logged.record))
        val newRecord = logged.record ?: return
        record.value = newRecord
        viewModelScope.launch {
            delay(RECORD_SHOWN_MILLIS)
            if (record.value == newRecord) record.value = null
        }
    }

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

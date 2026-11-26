package com.boratreino.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RemovedSets
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioField
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.LoggingRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import com.boratreino.core.model.WorkoutSet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The set being fixed in the bottom sheet, with its own stepper values. [number] is its row number (1-based). */
internal data class SetEdit(
    val set: WorkoutSet,
    val machine: Machine,
    val number: Int,
    val weightKg: Double,
    val reps: Int,
    /** Cardio machines edit these instead of [weightKg] and [reps]; 0 = not logged. */
    val minutes: Int = 0,
    val calories: Int = 0,
    val distanceM: Int = 0,
    /** Cardio: the intensity (e.g. 0.79), null = not logged. */
    val intensity: Double? = null,
    val type: SetType = SetType.NORMAL,
) {
    val isCardio: Boolean get() = machine.kind == MachineKind.CARDIO

    val cardioDraft: CardioDraft get() = CardioDraft(minutes, calories, distanceM, intensity)

    /** A cardio set with nothing logged is not a set; Save waits until there is something. */
    val canSave: Boolean get() = !isCardio || CardioRules.canLog(cardioDraft)
}

/** Which value the number pad is editing. */
internal enum class PadTarget(val cardioField: CardioField? = null) {
    EDIT_WEIGHT,
    EDIT_REPS,
    EDIT_MINUTES(CardioField.MINUTES),
    EDIT_DISTANCE_KM(CardioField.DISTANCE_KM),
    EDIT_INTENSITY(CardioField.INTENSITY),
    EDIT_CALORIES(CardioField.CALORIES),
}

/** The number pad dialog: what it edits and the text it starts with. */
internal data class NumberPad(val target: PadTarget, val initial: String)

/**
 * Everything the session editor shows. [detail] is null while loading ([loaded] false) or when the
 * session doesn't exist ([loaded] true). [routines] are the routine chips; [machines] are the
 * machines the picker offers (not archived), shown when [picking] is set.
 */
internal data class SessionEditorUiState(
    val loaded: Boolean = false,
    val detail: SessionDetail? = null,
    val date: LocalDate? = null,
    val routines: List<Routine> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val picking: Boolean = false,
    val editor: SetEdit? = null,
    val pad: NumberPad? = null,
)

internal sealed interface SessionEditorEvent {
    /** A set was added (the write is done); the screen gives the haptic tick. */
    data object SetAdded : SessionEditorEvent

    /** The set was deleted; the screen offers Undo. */
    data class SetRemoved(val removed: RemovedSets) : SessionEditorEvent
    /** Repeat found nothing to copy (no earlier session of the routine, or nothing left to fill). */
    data object NothingToRepeat : SessionEditorEvent
    data object Failed : SessionEditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val history: HistoryRepository,
    private val training: TrainingRepository,
    routineRepository: RoutineRepository,
    machineRepository: MachineRepository,
    private val clock: Clock,
) : ViewModel() {

    private val handle = savedStateHandle

    // "New" mode: no session id yet. Date, routine and extra live in the SavedStateHandle and nothing is
    // written until the first set is added; the id then switches to the session that write created.
    private val sessionIdFlow: StateFlow<Long?> = handle.getStateFlow<Long?>(SESSION_ID_ARG, null)
    private val newRoutineId: StateFlow<Long?> = handle.getStateFlow<Long?>(NEW_ROUTINE_KEY, null)
    private val newExtra: StateFlow<Boolean> = handle.getStateFlow(NEW_EXTRA_KEY, false)
    private val newDate: StateFlow<String?> = handle.getStateFlow<String?>(DATE_ARG, null)

    /** The session being edited; null in new mode. */
    private val sessionId: Long? get() = sessionIdFlow.value

    private fun enterNewMode(date: LocalDate, routineId: Long?, extra: Boolean) {
        handle[DATE_ARG] = date.toString()
        handle[NEW_ROUTINE_KEY] = routineId
        handle[NEW_EXTRA_KEY] = extra && routineId != null
        handle[SESSION_ID_ARG] = null
    }

    private fun newModeDate(): LocalDate = newDate.value?.let(LocalDate::parse) ?: LocalDate.now(clock)

    private val eventChannel = Channel<SessionEditorEvent>(Channel.BUFFERED)
    internal val events: Flow<SessionEditorEvent> = eventChannel.receiveAsFlow()

    private val picking = MutableStateFlow(false)
    private val editor = MutableStateFlow<SetEdit?>(null)
    private val pad = MutableStateFlow<NumberPad?>(null)

    private val storedDetail: Flow<SessionDetail?> = sessionIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else history.observeSession(id)
    }

    private val sessionAndRoutines: Flow<SessionEditorUiState> = combine(
        storedDetail,
        routineRepository.observePlan().map { plan -> plan.routines.map { it.routine } },
        machineRepository.observeMachines(includeArchived = false),
        sessionIdFlow,
        combine(newDate, newRoutineId, newExtra) { d, r, e -> Triple(d, r, e) },
    ) { stored, routines, machines, id, draft ->
        val detail = if (id == null) {
            val date = draft.first?.let(LocalDate::parse) ?: LocalDate.now(clock)
            val routineId = draft.second
            // Not a stored session: an empty placeholder with the chosen date, routine and extra flag.
            SessionDetail(
                session = Session(
                    id = 0L,
                    startedAt = date.atStartOfDay(clock.zone).toInstant(),
                    endedAt = null,
                    routineId = routineId,
                    note = null,
                    isExtra = draft.third && routineId != null,
                ),
                routine = routines.firstOrNull { it.id == routineId },
                machines = emptyList(),
            )
        } else {
            stored
        }
        SessionEditorUiState(
            loaded = true,
            detail = detail,
            date = detail?.session?.startedAt?.atZone(clock.zone)?.toLocalDate(),
            routines = routines,
            machines = machines.filter { !it.archived },
        )
    }

    internal val state: StateFlow<SessionEditorUiState> = combine(
        sessionAndRoutines,
        picking,
        editor,
        pad,
    ) { base, isPicking, editing, padding ->
        base.copy(picking = isPicking, editor = editing, pad = padding)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionEditorUiState())

    // Routine and sets. Every change is written at once.

    /** Null = Free. */
    internal fun chooseRoutine(routineId: Long?) {
        val id = sessionId
        if (id == null) {
            handle[NEW_ROUTINE_KEY] = routineId
            if (routineId == null) handle[NEW_EXTRA_KEY] = false
            return
        }
        val wasExtra = state.value.detail?.session?.isExtra == true
        write {
            history.setSessionRoutine(id, routineId)
            // A workout without a routine cannot be an extra one: the switch is hidden, so it is cleared too.
            if (routineId == null && wasExtra) history.setSessionExtra(id, false)
        }
    }

    /** An extra workout does not move the rotation on. */
    internal fun setExtra(extra: Boolean) {
        val id = sessionId
        if (id == null) {
            handle[NEW_EXTRA_KEY] = extra
            return
        }
        write { history.setSessionExtra(id, extra) }
    }

    /** A copy of the machine's last set in this session. */
    internal fun addSet(machineId: Long) {
        val sets = setsOf(machineId)
        write {
            addPrefilledSet(machineId, lastTime = emptyList(), sets = sets)
            eventChannel.send(SessionEditorEvent.SetAdded)
        }
    }

    /** Fills the empty session from the last session of its routine (one write, in the repository). */
    internal fun repeatLast(mode: RepeatMode) {
        write {
            val id = sessionId
            val added = if (id != null) {
                history.repeatLastSession(id, mode)
            } else {
                history.addSessionRepeating(newModeDate(), newRoutineId.value, newExtra.value, mode).also {
                    it.firstOrNull()?.let { first -> handle[SESSION_ID_ARG] = first.sessionId }
                }
            }
            if (added.isEmpty()) {
                eventChannel.send(SessionEditorEvent.NothingToRepeat)
            } else {
                eventChannel.send(SessionEditorEvent.SetAdded)
            }
        }
    }

    internal fun openPicker() {
        picking.value = true
    }

    internal fun closePicker() {
        picking.value = false
    }

    /** Adds a first set prefilled from the machine's most recent earlier session. */
    internal fun addMachine(machineId: Long) {
        val sets = setsOf(machineId)
        write {
            // New mode has no session yet: look before the chosen date instead.
            val lastTime = sessionId?.let { history.lastTimeBefore(it, machineId) }
                ?: history.lastTimeBeforeDate(newModeDate(), machineId)
            addPrefilledSet(machineId, lastTime, sets)
            picking.value = false
            eventChannel.send(SessionEditorEvent.SetAdded)
        }
    }

    /** Writes the next set of [machineId]: a cardio draft for cardio machines, a weight draft otherwise. */
    private suspend fun addPrefilledSet(machineId: Long, lastTime: List<WorkoutSet>, sets: List<WorkoutSet>) {
        val machine = state.value.detail?.machines?.firstOrNull { it.machine.id == machineId }?.machine
            ?: state.value.machines.firstOrNull { it.id == machineId }
        val id = sessionId
        val cardio = machine?.kind == MachineKind.CARDIO
        if (id != null) {
            if (cardio) {
                history.addCardioSet(id, machineId, CardioRules.prefill(lastTime, sets))
            } else {
                val draft: SetDraft = LoggingRules.prefill(lastTime = lastTime, thisSession = sets)
                history.addSet(id, machineId, draft)
            }
        } else {
            // First set of a new session: the session and the set are written together.
            val date = newModeDate()
            val routineId = newRoutineId.value
            val extra = newExtra.value
            val added = if (cardio) {
                history.addSessionWithCardio(date, routineId, extra, machineId, CardioRules.prefill(lastTime, sets))
            } else {
                val draft: SetDraft = LoggingRules.prefill(lastTime = lastTime, thisSession = sets)
                history.addSessionWithSet(date, routineId, extra, machineId, draft)
            }
            handle[SESSION_ID_ARG] = added.sessionId
        }
    }

    private fun setsOf(machineId: Long): List<WorkoutSet> =
        state.value.detail?.machines?.firstOrNull { it.machine.id == machineId }?.sets.orEmpty()

    // Fixing a set.

    internal fun openSet(set: WorkoutSet) {
        val entry = state.value.detail?.machines?.firstOrNull { it.machine.id == set.machineId } ?: return
        val number = entry.sets.indexOfFirst { it.id == set.id } + 1
        val cardio = CardioRules.toDraft(set)
        editor.value = SetEdit(
            set = set,
            machine = entry.machine,
            number = number,
            weightKg = set.weightKg ?: 0.0,
            reps = set.reps ?: LoggingRules.DEFAULT_REPS,
            minutes = cardio.minutes,
            calories = cardio.calories,
            distanceM = cardio.distanceM,
            intensity = cardio.intensity,
            type = if (entry.machine.kind == MachineKind.CARDIO) SetType.NORMAL else set.type,
        )
    }

    internal fun setEditorType(type: SetType) {
        editor.value = editor.value?.let { if (it.isCardio) it else it.copy(type = type) }
    }

    internal fun closeEditor() {
        editor.value = null
        pad.value = null
    }

    internal fun stepEditorWeight(up: Boolean) {
        editor.value = editor.value?.let {
            it.copy(weightKg = LoggingRules.stepWeight(it.weightKg, it.machine.stepKg, up))
        }
    }

    internal fun stepEditorReps(up: Boolean) {
        editor.value = editor.value?.let { it.copy(reps = LoggingRules.stepReps(it.reps, up)) }
    }

    internal fun stepEditorMinutes(up: Boolean) {
        editor.value = editor.value?.let { it.copy(minutes = CardioRules.stepMinutes(it.minutes, up)) }
    }

    internal fun stepEditorCalories(up: Boolean) {
        editor.value = editor.value?.let { it.copy(calories = CardioRules.stepCalories(it.calories, up)) }
    }

    internal fun stepEditorDistance(up: Boolean) {
        editor.value = editor.value?.let { it.copy(distanceM = CardioRules.stepDistance(it.distanceM, up)) }
    }

    internal fun stepEditorIntensity(up: Boolean) {
        editor.value = editor.value?.let { it.copy(intensity = CardioRules.stepIntensity(it.intensity, up)) }
    }

    internal fun saveEditor() {
        val e = editor.value ?: return
        if (!e.canSave) return
        editor.value = null
        val updated = if (e.isCardio) {
            val s = e.set
            CardioRules.toSet(s.id, s.sessionId, s.machineId, s.position, e.cardioDraft, s.loggedAt)
        } else {
            e.set.copy(weightKg = e.weightKg, reps = e.reps, type = e.type)
        }
        write { training.updateSet(updated) }
    }

    internal fun deleteEditor() {
        val e = editor.value ?: return
        editor.value = null
        val detail = state.value.detail
        val previousId = sessionId
        val isLastSet = detail != null && detail.machines.sumOf { it.sets.size } <= 1
        // The last set takes its session with it: go back to new mode right away, keeping date, routine and extra.
        if (isLastSet && detail != null) {
            enterNewMode(
                detail.session.startedAt.atZone(clock.zone).toLocalDate(),
                detail.session.routineId,
                detail.session.isExtra,
            )
        }
        write(onFailure = { if (isLastSet) handle[SESSION_ID_ARG] = previousId }) {
            val removed = training.deleteSet(e.set)
            removed.session?.let { gone ->
                enterNewMode(gone.startedAt.atZone(clock.zone).toLocalDate(), gone.routineId, gone.isExtra)
            }
            eventChannel.send(SessionEditorEvent.SetRemoved(removed))
        }
    }

    internal fun restoreSets(removed: RemovedSets) {
        write {
            training.restoreSets(removed)
            // The deleted session is back with its old id: leave new mode, unless a set was added meanwhile.
            val back = removed.session
            if (back != null && sessionId == null) handle[SESSION_ID_ARG] = back.id
        }
    }

    // Number pad (typing is allowed here; the steppers stay the main path).

    internal fun openPad(target: PadTarget) {
        val e = editor.value ?: return
        pad.value = NumberPad(
            target,
            when (target) {
                PadTarget.EDIT_WEIGHT -> e.weightKg.toBigDecimal().stripTrailingZeros().toPlainString()
                PadTarget.EDIT_REPS -> e.reps.toString()
                else -> target.cardioField?.let { CardioRules.fieldText(e.cardioDraft, it) }.orEmpty()
            },
        )
    }

    internal fun dismissPad() {
        pad.value = null
    }

    internal fun confirmPad(text: String) {
        val target = pad.value?.target ?: return
        pad.value = null
        when (target) {
            PadTarget.EDIT_WEIGHT -> parseWeight(text)?.let { w -> editor.value = editor.value?.copy(weightKg = w) }
            PadTarget.EDIT_REPS -> parseReps(text)?.let { r -> editor.value = editor.value?.copy(reps = r) }
            else -> target.cardioField?.let { field -> applyCardioField(field, text) }
        }
    }

    /** Typed cardio value: ignored when it is not a number, clamped by the rules otherwise. */
    private fun applyCardioField(field: CardioField, text: String) {
        val e = editor.value ?: return
        val d = CardioRules.withField(e.cardioDraft, field, text) ?: return
        editor.value = e.copy(
            minutes = d.minutes,
            calories = d.calories,
            distanceM = d.distanceM,
            intensity = d.intensity,
        )
    }

    private fun parseWeight(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, LoggingRules.MAX_WEIGHT_KG)

    private fun parseReps(text: String): Int? =
        text.trim().toIntOrNull()?.coerceIn(1, LoggingRules.MAX_REPS)

    private fun write(onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onFailure()
                eventChannel.send(SessionEditorEvent.Failed)
            }
        }
    }
}

private const val NEW_ROUTINE_KEY = "newRoutineId"
private const val NEW_EXTRA_KEY = "newExtra"

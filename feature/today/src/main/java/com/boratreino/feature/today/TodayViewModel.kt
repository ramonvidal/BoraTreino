package com.boratreino.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import kotlinx.coroutines.flow.MutableStateFlow
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.model.SetSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One card of the open session: a machine and what was logged on it. */
internal data class SessionMachineUi(val machine: Machine, val summary: SetSummary?)

/** The open session as the Today tab shows it. [machines] has the most recent one first. */
internal data class SessionUi(val machineCount: Int, val setCount: Int, val machines: List<SessionMachineUi>)

/** One routine chip; [selected] is the active one. */
internal data class RoutineChipUi(val id: Long, val shortName: String, val selected: Boolean)

/** A machine of the active routine with its target and this session's progress. [highlighted]: the first one not done. */
internal data class RoutineMachineUi(
    val machine: Machine,
    val target: RoutineItem,
    val setCount: Int,
    val done: Boolean,
    val highlighted: Boolean,
    /** No sets yet: the row offers skipping or removing it. */
    val canSkip: Boolean = false,
    /** This session's totals on a cardio machine (shown instead of the sets x reps target); null otherwise. */
    val cardioSummary: SetSummary? = null,
)

/** "Skip [machine] today?" for the active routine [routineName]. */
internal data class SkipDialog(val machine: Machine, val routineName: String)

/**
 * What Today adds when the owner has routines; [TodayUiState.routines] is null without any, and
 * Today then looks as it did before routines.
 * [activeName] is the active routine's name (null = Free or a rest day); [machines] are its machines in order;
 * [alsoThisSession] are the session's other machines.
 */
internal data class RoutineUi(
    val chips: List<RoutineChipUi>,
    val freeSelected: Boolean,
    val activeName: String? = null,
    val restDay: Boolean = false,
    /** The active routine's id; needed to remove a machine from it. */
    val activeId: Long? = null,
    val machines: List<RoutineMachineUi> = emptyList(),
    val alsoThisSession: List<SessionMachineUi> = emptyList(),
)

/** [loaded] is false until the first database read, so the screen doesn't flash the empty state. */
internal data class TodayUiState(
    val loaded: Boolean = false,
    val session: SessionUi? = null,
    val recent: List<Machine> = emptyList(),
    val routines: RoutineUi? = null,
    val skipDialog: SkipDialog? = null,
)

internal sealed interface TodayEvent {
    /** Done was tapped and the session closed; the screen offers Undo. */
    data class Finished(val summary: SessionSummary) : TodayEvent
    /** Skipped for this training; the screen offers Undo. */
    data class Skipped(val machine: Machine) : TodayEvent

    /** Taken out of the ficha [routineName]; Undo puts [item] back. */
    data class Removed(val routineName: String, val item: RoutineItem) : TodayEvent
    data object Failed : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val training: TrainingRepository,
    machines: MachineRepository,
    private val routineRepository: RoutineRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    private val eventChannel = Channel<TodayEvent>(Channel.BUFFERED)
    internal val events: Flow<TodayEvent> = eventChannel.receiveAsFlow()

    private val skipDialog = MutableStateFlow<SkipDialog?>(null)

    internal val state: StateFlow<TodayUiState> = combine(
        training.observeOpenSession(),
        machines.observeRecentMachines(),
        training.observeTodayPlan(),
        machines.observeMachines(includeArchived = true),
        skipDialog,
    ) { open, recent, plan, allMachines, dialog ->
        val byId = allMachines.associateBy { it.id }
        val routines = if (plan.routines.isEmpty()) {
            null
        } else {
            val active = plan.active
            val setCounts = open?.machines.orEmpty().associate { it.machine.id to it.sets.size }
            val setsByMachine = open?.machines.orEmpty().associate { it.machine.id to it.sets }
            val itemIds = active?.items?.map { it.machineId }.orEmpty().toSet()
            var highlightedGiven = false
            val rows = active?.items.orEmpty().mapNotNull { item ->
                val machine = byId[item.machineId] ?: return@mapNotNull null
                val count = setCounts[item.machineId] ?: 0
                val done = count >= item.targetSets
                val highlight = !done && !highlightedGiven
                if (highlight) highlightedGiven = true
                RoutineMachineUi(
                    machine, item, count, done, highlight,
                    canSkip = count == 0,
                    cardioSummary = if (machine.kind == MachineKind.CARDIO) setsByMachine[machine.id]?.let { SetSummary.of(it) } else null,
                )
            }
            RoutineUi(
                chips = plan.routines.map { RoutineChipUi(it.id, it.shortName, it.id == active?.routine?.id) },
                freeSelected = active == null && (!plan.restDay || plan.choice is RoutineChoice.Free),
                activeName = active?.routine?.name,
                restDay = plan.restDay,
                activeId = active?.routine?.id,
                machines = rows,
                alsoThisSession = open?.machines.orEmpty()
                    .filter { it.machine.id !in itemIds }
                    .map { SessionMachineUi(it.machine, SetSummary.of(it.sets)) },
            )
        }
        TodayUiState(
            routines = routines,
            skipDialog = dialog,
            loaded = true,
            session = open?.let { s ->
                SessionUi(
                    machineCount = s.machines.size,
                    setCount = s.machines.sumOf { it.sets.size },
                    machines = s.machines.map { SessionMachineUi(it.machine, SetSummary.of(it.sets)) },
                )
            },
            recent = recent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    init {
        viewModelScope.launch {
            try {
                training.closeStaleSession()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to show: the session just stays open until the next try.
            }
        }
    }

    internal fun askSkip(machineId: Long) {
        val routines = state.value.routines ?: return
        val row = routines.machines.firstOrNull { it.machine.id == machineId } ?: return
        skipDialog.value = SkipDialog(row.machine, routines.activeName.orEmpty())
    }

    internal fun dismissSkip() {
        skipDialog.value = null
    }

    /** Hides the machine for this training only. */
    internal fun skipToday() {
        val machine = skipDialog.value?.machine ?: return
        skipDialog.value = null
        launchSafely {
            training.skipMachineToday(machine.id)
            eventChannel.send(TodayEvent.Skipped(machine))
        }
    }

    internal fun unskip(machineId: Long) = launchSafely { training.unskipMachineToday(machineId) }

    /** Takes the machine out of the ficha itself. */
    internal fun removeFromRoutine() {
        val dialog = skipDialog.value ?: return
        val routineId = state.value.routines?.activeId ?: return
        skipDialog.value = null
        launchSafely {
            val item = routineRepository.removeMachine(routineId, dialog.machine.id) ?: return@launchSafely
            eventChannel.send(TodayEvent.Removed(dialog.routineName, item))
        }
    }

    internal fun restoreItem(item: RoutineItem) = launchSafely { routineRepository.restoreItem(item) }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                eventChannel.send(TodayEvent.Failed)
            }
        }
    }

    internal fun choose(choice: RoutineChoice) {
        viewModelScope.launch {
            try {
                training.chooseRoutine(choice)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                eventChannel.send(TodayEvent.Failed)
            }
        }
    }

    internal fun finish() {
        viewModelScope.launch {
            try {
                try {
                    restTimer.skip()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Finishing the session matters more than the timer.
                }
                val summary = training.finishSession() ?: return@launch
                eventChannel.send(TodayEvent.Finished(summary))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                eventChannel.send(TodayEvent.Failed)
            }
        }
    }

    internal fun reopen(sessionId: Long) {
        viewModelScope.launch {
            try {
                training.reopenSession(sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                eventChannel.send(TodayEvent.Failed)
            }
        }
    }
}

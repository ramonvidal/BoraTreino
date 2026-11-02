package com.boratreino.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
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
)

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
    val machines: List<RoutineMachineUi> = emptyList(),
    val alsoThisSession: List<SessionMachineUi> = emptyList(),
)

/** [loaded] is false until the first database read, so the screen doesn't flash the empty state. */
internal data class TodayUiState(
    val loaded: Boolean = false,
    val session: SessionUi? = null,
    val recent: List<Machine> = emptyList(),
    val routines: RoutineUi? = null,
)

internal sealed interface TodayEvent {
    /** Done was tapped and the session closed; the screen offers Undo. */
    data class Finished(val summary: SessionSummary) : TodayEvent
    data object Failed : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val training: TrainingRepository,
    machines: MachineRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    private val eventChannel = Channel<TodayEvent>(Channel.BUFFERED)
    internal val events: Flow<TodayEvent> = eventChannel.receiveAsFlow()

    internal val state: StateFlow<TodayUiState> = combine(
        training.observeOpenSession(),
        machines.observeRecentMachines(),
        training.observeTodayPlan(),
        machines.observeMachines(includeArchived = true),
    ) { open, recent, plan, allMachines ->
        val byId = allMachines.associateBy { it.id }
        val routines = if (plan.routines.isEmpty()) {
            null
        } else {
            val active = plan.active
            val setCounts = open?.machines.orEmpty().associate { it.machine.id to it.sets.size }
            val itemIds = active?.items?.map { it.machineId }.orEmpty().toSet()
            var highlightedGiven = false
            val rows = active?.items.orEmpty().mapNotNull { item ->
                val machine = byId[item.machineId] ?: return@mapNotNull null
                val count = setCounts[item.machineId] ?: 0
                val done = count >= item.targetSets
                val highlight = !done && !highlightedGiven
                if (highlight) highlightedGiven = true
                RoutineMachineUi(machine, item, count, done, highlight)
            }
            RoutineUi(
                chips = plan.routines.map { RoutineChipUi(it.id, it.shortName, it.id == active?.routine?.id) },
                freeSelected = active == null && (!plan.restDay || plan.choice is RoutineChoice.Free),
                activeName = active?.routine?.name,
                restDay = plan.restDay,
                machines = rows,
                alsoThisSession = open?.machines.orEmpty()
                    .filter { it.machine.id !in itemIds }
                    .map { SessionMachineUi(it.machine, SetSummary.of(it.sets)) },
            )
        }
        TodayUiState(
            routines = routines,
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

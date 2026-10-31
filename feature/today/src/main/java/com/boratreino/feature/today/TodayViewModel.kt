package com.boratreino.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.SessionSummary
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

/** [loaded] is false until the first database read, so the screen doesn't flash the empty state. */
internal data class TodayUiState(
    val loaded: Boolean = false,
    val session: SessionUi? = null,
    val recent: List<Machine> = emptyList(),
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
) : ViewModel() {

    private val eventChannel = Channel<TodayEvent>(Channel.BUFFERED)
    internal val events: Flow<TodayEvent> = eventChannel.receiveAsFlow()

    internal val state: StateFlow<TodayUiState> = combine(
        training.observeOpenSession(),
        machines.observeRecentMachines(),
    ) { open, recent ->
        TodayUiState(
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

    internal fun finish() {
        viewModelScope.launch {
            try {
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

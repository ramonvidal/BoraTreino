package com.boratreino.feature.today

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.ZonedDateTime
import java.time.Duration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.model.DayKind
import com.boratreino.core.model.EnergyRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.SessionSets
import com.boratreino.core.settings.BodySettings
import java.time.Clock
import java.time.LocalDate
import java.time.Instant
import com.boratreino.core.model.MachineKind
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import kotlinx.coroutines.flow.MutableStateFlow
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.working
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

/** "Same as last time" is on offer: [machineCount] machines and [setCount] sets would be logged. */
internal data class RepeatOfferUi(val machineCount: Int, val setCount: Int)

/** A second past midnight, so the new day's date is surely the one read. */
private const val MIDNIGHT_MARGIN_MS = 1_000L
private const val RECHECK_MS = 60_000L
private const val MAX_UNCHANGED_WAKES = 2

/** The quiet energy line: today's estimate in [kcal] (rounded to 10) and what kind of day it is. */
internal data class DayEnergyUi(val kcal: Int, val kind: DayKind)

/** The UI-only flows of the state, merged so the main combine stays within five flows. */
private data class SideState(
    val dialog: SkipDialog?,
    val editing: Boolean,
    val offer: RepeatOfferUi?,
    val energy: DayEnergyUi?,
)

/** [loaded] is false until the first database read, so the screen doesn't flash the empty state. */
internal data class TodayUiState(
    val loaded: Boolean = false,
    val session: SessionUi? = null,
    val recent: List<Machine> = emptyList(),
    val routines: RoutineUi? = null,
    val skipDialog: SkipDialog? = null,
    /** The training card is in edit mode (reorder / remove ficha machines). True only while a ficha is active (with or without an open session). */
    val editing: Boolean = false,
    /** The routine card can log the whole routine like last time; null when nothing is on offer. */
    val repeatOffer: RepeatOfferUi? = null,
    /** Estimated energy need of today; null without a profile or when the owner switched it off. */
    val energy: DayEnergyUi? = null,
)

internal sealed interface TodayEvent {
    /** Done was tapped and the session closed; the screen offers Undo. */
    data class Finished(val summary: SessionSummary) : TodayEvent
    /** Skipped for this training; the screen offers Undo. */
    data class Skipped(val machine: Machine) : TodayEvent

    /** Taken out of the ficha [routineName]; Undo puts [item] back. */
    data class Removed(val routineName: String, val item: RoutineItem) : TodayEvent
    /** The whole routine was logged like last time; the screen offers Undo. */
    data class Repeated(val outcome: RepeatOutcome) : TodayEvent
    data object Failed : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val training: TrainingRepository,
    machines: MachineRepository,
    private val routineRepository: RoutineRepository,
    private val restTimer: RestTimer,
    history: HistoryRepository,
    body: BodySettings,
    private val clock: Clock,
) : ViewModel() {

    private val eventChannel = Channel<TodayEvent>(Channel.BUFFERED)
    internal val events: Flow<TodayEvent> = eventChannel.receiveAsFlow()

    private val skipDialog = MutableStateFlow<SkipDialog?>(null)
    private val editing = MutableStateFlow(false)

    /**
     * Today's date, re-emitted after the next midnight so the energy line follows the day when the app stays open.
     * If the date has not changed after waking up (a clock that does not move, as in tests, or an early wake-up),
     * it checks once more a minute later and then stops, so it never spins.
     */
    private val today: Flow<LocalDate> = flow {
        var date = LocalDate.now(clock)
        emit(date)
        var unchanged = 0
        while (unchanged < MAX_UNCHANGED_WAKES) {
            val now = ZonedDateTime.now(clock)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val wait = if (unchanged == 0) Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_MARGIN_MS else RECHECK_MS
            delay(wait)
            val again = LocalDate.now(clock)
            if (again != date) {
                date = again
                emit(date)
                unchanged = 0
            } else {
                unchanged++
            }
        }
    }.distinctUntilChanged()

    /**
     * Today's energy estimate. Read-only on the sessions flows: logging a set updates it without
     * any work on the logging path. Null without a profile or when the switch is off.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val energy: Flow<DayEnergyUi?> = today.flatMapLatest { date ->
        combine(
            body.profile,
            body.showEnergyEstimate,
            history.observeDay(date),
            training.observeOpenSession(),
            training.observeTodayPlan(),
        ) { profile, show, day, open, plan ->
            if (profile == null || !show) return@combine null
            val sessions = day.map { detail -> SessionSets(detail.session, detail.machines.flatMap { it.sets }) }.toMutableList()
            if (open != null && sessions.none { it.session.id == open.session.id }) {
                sessions += SessionSets(open.session, open.machines.flatMap { it.sets })
            }
            val result = EnergyRules.day(
                profile = profile,
                date = date,
                now = Instant.now(clock),
                sessions = sessions,
                trainingExpected = plan.suggested != null || open != null,
            )
            DayEnergyUi(result.kcal, result.kind)
        }
    }

    internal val state: StateFlow<TodayUiState> = combine(
        training.observeOpenSession(),
        machines.observeRecentMachines(),
        training.observeTodayPlan(),
        machines.observeMachines(includeArchived = true),
        // combine() takes at most five flows with a typed lambda: the UI-only flows, the repeat offer and the energy line go in as one.
        combine(skipDialog, editing, training.observeRepeatOffer(), energy) { dialog, editRequested, offer, energyUi ->
            SideState(dialog, editRequested, offer?.let { RepeatOfferUi(it.machineCount, it.setCount) }, energyUi)
        },
    ) { open, recent, plan, allMachines, uiOnly ->
        val dialog = uiOnly.dialog
        val editRequested = uiOnly.editing
        val byId = allMachines.associateBy { it.id }
        val routines = if (plan.routines.isEmpty()) {
            null
        } else {
            val active = plan.active
            val setCounts = open?.machines.orEmpty().associate { it.machine.id to it.sets.working().size }
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
            editing = editRequested && plan.active != null,
            loaded = true,
            repeatOffer = uiOnly.offer,
            energy = uiOnly.energy,
            session = open?.let { s ->
                SessionUi(
                    machineCount = s.machines.size,
                    setCount = s.machines.sumOf { it.sets.working().size },
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

    internal fun startEditing() {
        if (state.value.routines?.activeId != null) editing.value = true
    }

    internal fun finishEditing() {
        editing.value = false
    }

    /** Moves a machine one place up or down in the active ficha; saved at once. */
    internal fun moveMachine(machineId: Long, up: Boolean) {
        val routineId = state.value.routines?.activeId ?: return
        launchSafely { routineRepository.moveItem(routineId, machineId, up) }
    }

    /** Edit mode's remove (x): takes the machine out of the ficha at once; the screen offers Undo. */
    internal fun removeMachine(machineId: Long) {
        val routines = state.value.routines ?: return
        val routineId = routines.activeId ?: return
        val name = routines.activeName.orEmpty()
        launchSafely {
            val item = routineRepository.removeMachine(routineId, machineId) ?: return@launchSafely
            eventChannel.send(TodayEvent.Removed(name, item))
        }
    }

    /** The shortcut: logs the active ficha's remaining machines like its last session. */
    internal fun repeatLast(mode: RepeatMode) = launchSafely {
        val outcome = training.repeatLastTraining(mode) ?: return@launchSafely
        eventChannel.send(TodayEvent.Repeated(outcome))
    }

    internal fun undoRepeat(outcome: RepeatOutcome) = launchSafely { training.undoRepeat(outcome) }

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
        editing.value = false
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
        editing.value = false
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

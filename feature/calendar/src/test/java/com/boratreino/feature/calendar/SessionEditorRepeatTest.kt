package com.boratreino.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** The session editor's "Same as last time" buttons: what the view model asks and which event follows. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionEditorRepeatTest {
    private val started = Instant.parse("2026-11-02T18:05:00Z")
    private val session = Session(id = 20L, startedAt = started, endedAt = null, routineId = 2L, note = null)
    private val legPress = Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)

    private class RepeatHistory : HistoryRepository {
        val detail = MutableStateFlow<SessionDetail?>(null)
        val requests = mutableListOf<Pair<Long, RepeatMode>>()
        var result: List<WorkoutSet> = emptyList()
        var fail = false

        override fun observeSession(sessionId: Long): Flow<SessionDetail?> = detail

        override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> {
            requests += sessionId to mode
            if (fail) error("disk full")
            return result
        }

        override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> = emptyList()
        override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet = error("not used")
        override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet = error("not used")
        override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = emptyFlow()
        override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = emptyFlow()
        override fun observeTrainingDates(): Flow<Set<LocalDate>> = emptyFlow()
        override suspend fun addSession(date: LocalDate, routineId: Long?): Long = 0L
        override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) = Unit
        override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
        override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
        override suspend fun deleteSession(sessionId: Long): SessionDetail? = null
        override suspend fun restoreSession(detail: SessionDetail) = Unit
    }

    private class RepeatTraining : TrainingRepository {
        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override fun observeOpenSession(): Flow<OpenSession?> = emptyFlow()
        override fun observeTodayPlan(): Flow<TodayPlan> = emptyFlow()
        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override suspend fun closeStaleSession() = Unit
        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used")
        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = error("not used")
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = emptyFlow()
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
    }

    private class RepeatRoutines : RoutineRepository {
        override fun observePlan(): Flow<RoutinePlanWithRoutines> =
            flowOf(RoutinePlanWithRoutines(RoutinePlan(1L, ScheduleMode.IN_TURN), emptyList()))

        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = flowOf(null)
        override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
        override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = 0L
        override suspend fun deleteRoutine(id: Long) = Unit
        override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
        override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit
        override suspend fun duplicateRoutine(id: Long): Long = 0L
        override suspend fun createRoutine(): Long = 0L
        override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
        override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? = null
        override suspend fun restoreItem(item: RoutineItem) = Unit
    }

    private class RepeatMachines(private val all: List<Machine>) : MachineRepository {
        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(all)
        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(all.firstOrNull { it.id == id })
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(all)
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = error("not used")
        override suspend fun delete(machineId: Long): Boolean = false
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private lateinit var history: RepeatHistory
    private lateinit var viewModel: SessionEditorViewModel
    private val events = mutableListOf<SessionEditorEvent>()

    private fun newSet(id: Long) =
        WorkoutSet(id, session.id, legPress.id, (id - 1).toInt(), 40.0, 10, null, null, null, started.plusSeconds(id))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        history = RepeatHistory()
        events.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.open() {
        history.detail.value = SessionDetail(session, null, emptyList())
        viewModel = SessionEditorViewModel(
            SavedStateHandle(mapOf(SESSION_ID_ARG to session.id)),
            history,
            RepeatTraining(),
            RepeatRoutines(),
            RepeatMachines(listOf(legPress)),
            Clock.fixed(started, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
    }

    @Test
    fun `repeating with sets added sends SetAdded`() = runTest {
        history.result = listOf(newSet(1), newSet(2))
        open()

        viewModel.repeatLast(RepeatMode.SAME)
        runCurrent()

        assertEquals(listOf(session.id to RepeatMode.SAME), history.requests)
        assertEquals(listOf<SessionEditorEvent>(SessionEditorEvent.SetAdded), events)
    }

    @Test
    fun `the suggested button passes its mode on`() = runTest {
        history.result = listOf(newSet(1))
        open()

        viewModel.repeatLast(RepeatMode.SUGGESTED)
        runCurrent()

        assertEquals(listOf(session.id to RepeatMode.SUGGESTED), history.requests)
        assertEquals(listOf<SessionEditorEvent>(SessionEditorEvent.SetAdded), events)
    }

    @Test
    fun `repeating with nothing to copy sends NothingToRepeat`() = runTest {
        history.result = emptyList()
        open()

        viewModel.repeatLast(RepeatMode.SAME)
        runCurrent()

        assertEquals(listOf<SessionEditorEvent>(SessionEditorEvent.NothingToRepeat), events)
    }

    @Test
    fun `a failing repeat sends Failed and does not crash`() = runTest {
        history.fail = true
        open()

        viewModel.repeatLast(RepeatMode.SAME)
        runCurrent()

        assertEquals(listOf<SessionEditorEvent>(SessionEditorEvent.Failed), events)
    }
}

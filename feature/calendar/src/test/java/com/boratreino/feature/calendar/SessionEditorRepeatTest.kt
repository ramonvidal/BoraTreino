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
import com.boratreino.core.testing.StubHistoryRepository
import com.boratreino.core.testing.StubMachineRepository
import com.boratreino.core.testing.StubRoutineRepository
import com.boratreino.core.testing.StubTrainingRepository
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
import com.boratreino.core.data.RemovedSets

/** The session editor's "Same as last time" buttons: what the view model asks and which event follows. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionEditorRepeatTest {
    private val started = Instant.parse("2026-11-02T18:05:00Z")
    private val session = Session(id = 20L, startedAt = started, endedAt = null, routineId = 2L, note = null)
    private val legPress = Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)

    private class RepeatHistory : StubHistoryRepository() {
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

        override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = emptyFlow()
        override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = emptyFlow()
        override fun observeTrainingDates(): Flow<Set<LocalDate>> = emptyFlow()
        override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
        override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
    }

    private class RepeatTraining : StubTrainingRepository() {
        override fun observeOpenSession(): Flow<OpenSession?> = emptyFlow()
        override fun observeTodayPlan(): Flow<TodayPlan> = emptyFlow()
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = emptyFlow()
    }

    private class RepeatRoutines : StubRoutineRepository() {
        override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = 0L
    }

    private class RepeatMachines(private val all: List<Machine>) : StubMachineRepository(all) {
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(all)
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

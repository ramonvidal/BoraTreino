package com.boratreino.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.TrainingSummary
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.notify.RestTimerState
import com.boratreino.core.testing.StubMachineRepository
import com.boratreino.core.testing.StubRoutineRepository
import com.boratreino.core.testing.StubTrainingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import com.boratreino.core.data.RemovedSets

/**
 * The "Training complete" sheet's state in the Today view model: Done opens it with the loaded summary, Share
 * asks for the picture, "Not finished yet" reopens the session, Close dismisses. Written from the public surface.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayViewModelSummaryTest {
    private val sessionId = 12L
    private val finished = SessionSummary(sessionId = sessionId, machineCount = 2, setCount = 6, recordCount = 1)

    /** A finished training: [summary] is what the sheet loads (null = the session is gone). */
    private class SummaryTraining(var summary: TrainingSummary?) : StubTrainingRepository() {
        val reopened = mutableListOf<Long>()
        var finishedSessions = 0
        var summaryLoads = 0
        private val pending = MutableStateFlow<Long?>(null)
        override val pendingSummarySessionId: Flow<Long?> = pending
        val pendingNow: Long? get() = pending.value

        /** The repository sets the pending session itself, as an automatic finish does. */
        fun announce(id: Long) {
            pending.value = id
        }

        override fun observeTodayPlan(): Flow<TodayPlan> = MutableStateFlow(
            TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Free, null, restDay = false),
        )
        override suspend fun finishSession(): SessionSummary? {
            finishedSessions++
            pending.value = 12L
            return SessionSummary(sessionId = 12L, machineCount = 2, setCount = 6, recordCount = 1)
        }
        override suspend fun consumePendingSummary() {
            pending.value = null
        }
        override suspend fun reopenSession(sessionId: Long) {
            reopened += sessionId
        }
        override suspend fun trainingSummary(sessionId: Long): TrainingSummary? {
            summaryLoads++
            return summary
        }
    }

    private class NoRoutines : StubRoutineRepository() {
        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
    }

    private class NoMachines : StubMachineRepository()

    private class IdleRest : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
    }

    private val loaded = TrainingSummary(
        sessionId = sessionId,
        routineName = "Routine B",
        date = LocalDate.of(2026, 11, 5),
        durationMinutes = 48,
        workingSets = 6,
        volumeKg = 2400.0,
        machineCount = 2,
        records = emptyList(),
        previous = null,
    )

    private lateinit var training: SummaryTraining
    private val events = mutableListOf<TodayEvent>()
    private var latest: TodayUiState = TodayUiState()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = SummaryTraining(loaded)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): TodayViewModel {
        val vm = TodayViewModel(training, NoMachines(), NoRoutines(), IdleRest(), FakeHistory(), FakeBodySettings(), TodayTestClock)
        backgroundScope.launch { vm.events.collect { events += it } }
        backgroundScope.launch { vm.state.collect { latest = it } }
        runCurrent()
        return vm
    }

    @Test
    fun `Done opens the sheet with the loaded summary of the finished session`() = runTest {
        val vm = viewModel()

        vm.finish()
        runCurrent()

        assertEquals(1, training.finishedSessions)
        assertEquals(loaded, latest.summary?.summary)
        assertEquals(false, latest.summary?.canUpdateRoutine)
        assertEquals(listOf<TodayEvent>(TodayEvent.Finished(finished)), events)
    }

    @Test
    fun `Done leaves the sheet closed when the session cannot be loaded`() = runTest {
        training.summary = null
        val vm = viewModel()

        vm.finish()
        runCurrent()

        assertNull(latest.summary)
    }

    @Test
    fun `dismissSummary closes the sheet and does not reopen the session`() = runTest {
        val vm = viewModel()
        vm.finish()
        runCurrent()

        vm.dismissSummary()
        runCurrent()

        assertNull(latest.summary)
        assertTrue(training.reopened.isEmpty())
    }

    @Test
    fun `Not finished yet closes the sheet and reopens the session it showed`() = runTest {
        val vm = viewModel()
        vm.finish()
        runCurrent()

        vm.reopenFromSummary()
        runCurrent()

        assertNull(latest.summary)
        assertEquals(listOf(sessionId), training.reopened)
        assertNull(training.pendingNow)
    }

    @Test
    fun `Not finished yet with no sheet open reopens nothing`() = runTest {
        val vm = viewModel()

        vm.reopenFromSummary()
        runCurrent()

        assertTrue(training.reopened.isEmpty())
    }

    @Test
    fun `Share asks for the picture of the open summary`() = runTest {
        val vm = viewModel()
        vm.finish()
        runCurrent()

        vm.shareSummary()
        runCurrent()

        assertEquals(listOf<TodayEvent>(TodayEvent.Finished(finished), TodayEvent.Share(requireNotNull(latest.summary))), events)
        assertEquals(loaded, (events.last() as TodayEvent.Share).summary.summary)
    }

    @Test
    fun `Share with no sheet open asks for nothing`() = runTest {
        val vm = viewModel()

        vm.shareSummary()
        runCurrent()

        assertTrue(events.none { it is TodayEvent.Share })
    }

    @Test
    fun `a failed share is reported as a failure`() = runTest {
        val vm = viewModel()

        vm.shareFailed()
        runCurrent()

        assertEquals(listOf<TodayEvent>(TodayEvent.Failed), events)
    }

    @Test
    fun `a routine update for a free session changes nothing in the open sheet`() = runTest {
        val vm = viewModel()
        vm.finish()
        runCurrent()

        vm.applyRoutineUpdate()
        runCurrent()
        vm.undoRoutineUpdate()
        runCurrent()

        assertEquals(loaded, latest.summary?.summary)
        assertNull(latest.summary?.routineUpdate)
        assertEquals(listOf<TodayEvent>(TodayEvent.Finished(finished)), events)
    }

    @Test
    fun `a pending session from the repository opens its sheet without Done`() = runTest {
        viewModel()

        training.announce(sessionId)
        runCurrent()

        assertEquals(loaded, latest.summary?.summary)
        assertEquals(0, training.finishedSessions)
        assertTrue(events.none { it is TodayEvent.Finished })
    }

    @Test
    fun `the same pending session opens the sheet once`() = runTest {
        viewModel()

        training.announce(sessionId)
        runCurrent()
        training.announce(sessionId)
        runCurrent()

        assertEquals(1, training.summaryLoads)
    }

    @Test
    fun `Done loads the summary once, through the pending session`() = runTest {
        val vm = viewModel()

        vm.finish()
        runCurrent()

        assertEquals(1, training.summaryLoads)
        assertEquals(loaded, latest.summary?.summary)
    }

    @Test
    fun `dismissing the sheet consumes the pending session`() = runTest {
        val vm = viewModel()
        training.announce(sessionId)
        runCurrent()

        vm.dismissSummary()
        runCurrent()

        assertNull(training.pendingNow)
        assertNull(latest.summary)
    }

    @Test
    fun `a pending session that cannot be loaded opens no sheet`() = runTest {
        training.summary = null
        viewModel()

        training.announce(sessionId)
        runCurrent()

        assertNull(latest.summary)
        assertTrue(events.none { it is TodayEvent.Finished })
    }
}

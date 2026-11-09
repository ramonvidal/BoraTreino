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
    private class SummaryTraining(var summary: TrainingSummary?) : TrainingRepository {
        val reopened = mutableListOf<Long>()
        var finishedSessions = 0

        override fun observeOpenSession(): Flow<OpenSession?> = MutableStateFlow(null)
        override fun observeTodayPlan(): Flow<TodayPlan> = MutableStateFlow(
            TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Free, null, restDay = false),
        )
        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = flowOf(null)
        override suspend fun closeStaleSession() = Unit
        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used by this test")
        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = error("not used by this test")
        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override suspend fun finishSession(): SessionSummary? {
            finishedSessions++
            return SessionSummary(sessionId = 12L, machineCount = 2, setCount = 6, recordCount = 1)
        }
        override suspend fun reopenSession(sessionId: Long) {
            reopened += sessionId
        }
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = flowOf(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = error("not used by this test")
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
        override suspend fun routineUpdateOffer(sessionId: Long): RoutineUpdate? = null
        override suspend fun sessionRoutineId(sessionId: Long): Long? = null
        override suspend fun trainingSummary(sessionId: Long): TrainingSummary? = summary
    }

    private class NoRoutines : RoutineRepository {
        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = flowOf(null)
        override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
        override suspend fun setTrainingDays(days: Set<DayOfWeek>) = Unit
        override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = routine.id
        override suspend fun deleteRoutine(id: Long) = Unit
        override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
        override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit
        override suspend fun duplicateRoutine(id: Long): Long = 0L
        override suspend fun createRoutine(): Long = 0L
        override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
        override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? = null
        override suspend fun restoreItem(item: RoutineItem) = Unit
        override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): RoutineUpdate = RoutineUpdate(emptyList(), emptyList())
        override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<RoutineItem>) = Unit
        override fun observePlanned(): Flow<List<PlannedWorkout>> = flowOf(emptyList())
        override suspend fun planWorkout(date: LocalDate, routineId: Long) = Unit
        override suspend fun unplanWorkout(date: LocalDate, routineId: Long) = Unit
    }

    private class NoMachines : MachineRepository {
        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(null)
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = MachineUsage(0, emptyList())
        override suspend fun delete(machineId: Long): Boolean = false
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun addFromLibrary(exercise: LibraryExercise, portuguese: Boolean): Long = 0L
        override fun observeLibraryIdsInUse(): Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

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
}

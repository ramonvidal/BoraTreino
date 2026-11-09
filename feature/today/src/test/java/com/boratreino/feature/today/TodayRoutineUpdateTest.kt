package com.boratreino.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.Addition
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
import com.boratreino.core.model.SetsChange
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
 * The routine update in the "Training complete" sheet: offered only when it would change the routine, applied
 * by "Update your routine", and undone by the sheet's Undo. Written from the view model's public surface
 * (finish, state.summary, applyRoutineUpdate, undoRoutineUpdate), not from its code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayRoutineUpdateTest {
    private val sessionId = 3L
    private val routineId = 7L
    private val previous = listOf(RoutineItem(routineId, 1L, 0, 3, 8, 12))
    private val change = RoutineUpdate(
        setChanges = listOf(SetsChange(1L, from = 3, to = 4)),
        additions = listOf(Addition(9L, 2)),
        previousItems = previous,
    )
    private val finished = SessionSummary(sessionId = sessionId, machineCount = 3, setCount = 9, recordCount = 0)

    /** Training whose finished session has [sessionRoutine] and an offer of [offer] (null when nothing would change). */
    private class UpdateTraining(
        var sessionRoutine: Long? = 7L,
        var offer: RoutineUpdate? = null,
    ) : TrainingRepository {
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
        override suspend fun finishSession(): SessionSummary? = SessionSummary(sessionId = 3L, machineCount = 3, setCount = 9, recordCount = 0)
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = flowOf(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = error("not used by this test")
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
        override suspend fun routineUpdateOffer(sessionId: Long): RoutineUpdate? = offer
        override suspend fun sessionRoutineId(sessionId: Long): Long? = sessionRoutine
        override suspend fun trainingSummary(sessionId: Long): TrainingSummary? =
            TrainingSummary(
                sessionId = sessionId,
                routineName = "Routine A",
                date = LocalDate.of(2026, 11, 5),
                durationMinutes = 50,
                workingSets = 9,
                volumeKg = 3000.0,
                machineCount = 3,
                records = emptyList(),
                previous = null,
            )
    }

    /** Records what the view model asks of the routines; the update answers [answer]. */
    private class UpdateRoutines(var answer: RoutineUpdate) : RoutineRepository {
        val updated = mutableListOf<Pair<Long, Long>>()
        val undone = mutableListOf<Pair<Long, List<RoutineItem>>>()

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
        override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): RoutineUpdate {
            updated += routineId to sessionId
            return answer
        }
        override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<RoutineItem>) {
            undone += routineId to previousItems
        }
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

    private lateinit var training: UpdateTraining
    private lateinit var routines: UpdateRoutines
    private val events = mutableListOf<TodayEvent>()
    private var latest: TodayUiState = TodayUiState()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = UpdateTraining()
        routines = UpdateRoutines(change)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): TodayViewModel {
        val vm = TodayViewModel(training, NoMachines(), routines, IdleRest(), FakeHistory(), FakeBodySettings(), TodayTestClock)
        backgroundScope.launch { vm.events.collect { events += it } }
        backgroundScope.launch { vm.state.collect { latest = it } }
        runCurrent()
        return vm
    }

    /** Done: the session closes and the sheet opens with its offer. */
    private fun TestScope.finishTraining(vm: TodayViewModel) {
        vm.finish()
        runCurrent()
    }

    // The offer in the sheet

    @Test
    fun `a routine that would change is offered an update in the sheet`() = runTest {
        training.offer = change
        val vm = viewModel()

        finishTraining(vm)

        assertTrue(requireNotNull(latest.summary).canUpdateRoutine)
        assertNull(latest.summary?.routineUpdate)
    }

    @Test
    fun `no offer when the routine would not change`() = runTest {
        training.offer = null
        val vm = viewModel()

        finishTraining(vm)

        assertEquals(false, latest.summary?.canUpdateRoutine)
    }

    @Test
    fun `a free session, with no routine, is never offered an update`() = runTest {
        training.sessionRoutine = null
        training.offer = change
        val vm = viewModel()

        finishTraining(vm)

        assertEquals(false, latest.summary?.canUpdateRoutine)
    }

    @Test
    fun `the sheet shows the finished training it was opened with`() = runTest {
        val vm = viewModel()

        finishTraining(vm)

        assertEquals(sessionId, latest.summary?.summary?.sessionId)
        assertEquals("Routine A", latest.summary?.summary?.routineName)
    }

    // The update

    @Test
    fun `Update your routine applies the update from the session and the sheet shows it as updated`() = runTest {
        training.offer = change
        val vm = viewModel()
        finishTraining(vm)

        vm.applyRoutineUpdate()
        runCurrent()

        assertEquals(listOf(routineId to sessionId), routines.updated)
        assertEquals(change, latest.summary?.routineUpdate)
        assertEquals(false, latest.summary?.canUpdateRoutine)
    }

    @Test
    fun `an update that changes nothing shows no updated state`() = runTest {
        training.offer = change
        routines.answer = RoutineUpdate(emptyList(), emptyList())
        val vm = viewModel()
        finishTraining(vm)

        vm.applyRoutineUpdate()
        runCurrent()

        assertEquals(listOf(routineId to sessionId), routines.updated)
        assertNull(latest.summary?.routineUpdate)
        assertEquals(false, latest.summary?.canUpdateRoutine)
    }

    // Undo

    @Test
    fun `Undo puts back the routine's previous items and offers the update again`() = runTest {
        training.offer = change
        val vm = viewModel()
        finishTraining(vm)
        vm.applyRoutineUpdate()
        runCurrent()

        vm.undoRoutineUpdate()
        runCurrent()

        assertEquals(listOf(routineId to previous), routines.undone)
        assertNull(latest.summary?.routineUpdate)
        assertTrue(requireNotNull(latest.summary).canUpdateRoutine)
    }

    @Test
    fun `Undo does nothing when no update was applied`() = runTest {
        training.offer = change
        val vm = viewModel()
        finishTraining(vm)

        vm.undoRoutineUpdate()
        runCurrent()

        assertTrue(routines.undone.isEmpty())
    }

    @Test
    fun `the routine update announces nothing as an event, the sheet carries it`() = runTest {
        training.offer = change
        val vm = viewModel()
        finishTraining(vm)
        vm.applyRoutineUpdate()
        runCurrent()
        vm.undoRoutineUpdate()
        runCurrent()

        assertEquals(listOf<TodayEvent>(TodayEvent.Finished(finished)), events)
    }
}

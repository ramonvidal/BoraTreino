package com.boratreino.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Today shortcut "Same as last time": the offer in the state, repeatLast and its Undo. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayViewModelRepeatTest {
    private val sample = TodaySamples

    private class RepeatTraining : TrainingRepository {
        val plan = MutableStateFlow(
            TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false),
        )
        val offer = MutableStateFlow<RepeatOffer?>(null)
        val repeatModes = mutableListOf<RepeatMode>()
        val undone = mutableListOf<RepeatOutcome>()
        var outcome: RepeatOutcome? = null
        var failRepeat = false
        var failUndo = false

        override fun observeOpenSession(): Flow<OpenSession?> = MutableStateFlow(null)
        override fun observeTodayPlan(): Flow<TodayPlan> = plan
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = offer

        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? {
            repeatModes += mode
            if (failRepeat) error("disk full")
            return outcome
        }

        override suspend fun undoRepeat(outcome: RepeatOutcome) {
            if (failUndo) error("disk full")
            undone += outcome
        }

        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override suspend fun closeStaleSession() = Unit
        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used")
        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = error("not used")
        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
    }

    private class RepeatMachines(private val all: List<Machine>) : MachineRepository {
        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = MutableStateFlow(all)
        override fun observeMachine(id: Long): Flow<Machine?> = MutableStateFlow(all.firstOrNull { it.id == id })
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = MutableStateFlow(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = MutableStateFlow(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = MachineUsage(0, emptyList())
        override suspend fun delete(machineId: Long): Boolean = true
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private class RepeatRoutines : RoutineRepository {
        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = emptyFlow()
        override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
        override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = routine.id
        override suspend fun deleteRoutine(id: Long) = Unit
        override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
        override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit
        override suspend fun duplicateRoutine(id: Long): Long = 0L
        override suspend fun createRoutine(): Long = 0L
        override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
        override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? = null
        override suspend fun restoreItem(item: RoutineItem) = Unit
    }

    private class RepeatRestTimer : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
    }

    private lateinit var training: RepeatTraining
    private lateinit var viewModel: TodayViewModel
    private val events = mutableListOf<TodayEvent>()
    private val outcome = RepeatOutcome(sessionId = 7L, sessionCreated = true, setIds = listOf(1L, 2L, 3L), machineCount = 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = RepeatTraining()
        events.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.open() {
        training.plan.value = TodayPlan(
            mode = ScheduleMode.IN_TURN,
            routines = listOf(sample.routineA, sample.routineB, sample.routineC),
            suggested = sample.routineB,
            choice = RoutineChoice.Suggested,
            active = RoutineWithItems(sample.routineB, sample.routineBItems),
            restDay = false,
        )
        viewModel = TodayViewModel(
            training,
            RepeatMachines(listOf(sample.legPress, sample.legExtension, sample.latPulldown, sample.chestPress)),
            RepeatRoutines(),
            RepeatRestTimer(),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
    }

    // The offer in the state

    @Test
    fun `without an offer the state has none`() = runTest {
        open()

        assertNull(viewModel.state.value.repeatOffer)
    }

    @Test
    fun `the offer shows in the state with its counts`() = runTest {
        training.offer.value = RepeatOffer(routineId = 2L, machineCount = 4, setCount = 12)
        open()

        assertEquals(RepeatOfferUi(machineCount = 4, setCount = 12), viewModel.state.value.repeatOffer)
    }

    @Test
    fun `the offer follows the repository`() = runTest {
        open()
        training.offer.value = RepeatOffer(2L, 3, 9)
        runCurrent()
        assertEquals(RepeatOfferUi(3, 9), viewModel.state.value.repeatOffer)

        training.offer.value = null
        runCurrent()
        assertNull(viewModel.state.value.repeatOffer)
    }

    // repeatLast

    @Test
    fun `repeating the same calls the repository with SAME and sends Repeated`() = runTest {
        training.outcome = outcome
        open()

        viewModel.repeatLast(RepeatMode.SAME)
        runCurrent()

        assertEquals(listOf(RepeatMode.SAME), training.repeatModes)
        assertEquals(listOf<TodayEvent>(TodayEvent.Repeated(outcome)), events)
    }

    @Test
    fun `repeating with suggested weights calls the repository with SUGGESTED and sends Repeated`() = runTest {
        training.outcome = outcome
        open()

        viewModel.repeatLast(RepeatMode.SUGGESTED)
        runCurrent()

        assertEquals(listOf(RepeatMode.SUGGESTED), training.repeatModes)
        assertEquals(listOf<TodayEvent>(TodayEvent.Repeated(outcome)), events)
    }

    @Test
    fun `a null outcome sends nothing`() = runTest {
        training.outcome = null
        open()

        viewModel.repeatLast(RepeatMode.SAME)
        runCurrent()

        assertEquals(listOf(RepeatMode.SAME), training.repeatModes)
        assertEquals(emptyList<TodayEvent>(), events)
    }

    @Test
    fun `a failing repeat sends Failed`() = runTest {
        training.failRepeat = true
        open()

        viewModel.repeatLast(RepeatMode.SAME)
        runCurrent()

        assertEquals(listOf<TodayEvent>(TodayEvent.Failed), events)
    }

    // Undo

    @Test
    fun `undo hands the whole outcome to the repository`() = runTest {
        open()

        viewModel.undoRepeat(outcome)
        runCurrent()

        assertEquals(listOf(outcome), training.undone)
        assertEquals(emptyList<TodayEvent>(), events)
    }

    @Test
    fun `a failing undo sends Failed`() = runTest {
        training.failUndo = true
        open()

        viewModel.undoRepeat(outcome)
        runCurrent()

        assertEquals(listOf<TodayEvent>(TodayEvent.Failed), events)
    }
}

package com.boratreino.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
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
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionMachine
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** Edit mode of the Today training card: when it is on, moving and removing ficha machines, when it ends. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayViewModelEditTest {
    private val sample = TodaySamples
    private val t0: Instant = Instant.parse("2026-10-31T18:00:00Z")

    private class FakeTraining : TrainingRepository {
    override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
    override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        val plan = MutableStateFlow(
            TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false),
        )
        val open = MutableStateFlow<OpenSession?>(null)
        val chosen = mutableListOf<RoutineChoice>()
        var finished = 0

        override fun observeOpenSession(): Flow<OpenSession?> = open
        override fun observeTodayPlan(): Flow<TodayPlan> = plan
        override suspend fun chooseRoutine(choice: RoutineChoice) {
            chosen += choice
        }
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override suspend fun closeStaleSession() = Unit
        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used")
        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = error("not used")
        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override suspend fun finishSession(): SessionSummary? {
            finished++
            return SessionSummary(sessionId = 1L, machineCount = 1, setCount = 3)
        }
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = MutableStateFlow(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
    }

    private class FakeMachines(private val all: List<Machine>) : MachineRepository {
    override suspend fun addFromLibrary(exercise: com.boratreino.core.model.LibraryExercise, portuguese: Boolean): Long = error("not used by this test")
    override fun observeLibraryIdsInUse(): kotlinx.coroutines.flow.Flow<Set<String>> = kotlinx.coroutines.flow.flowOf(emptySet())

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

    private class FakeRoutines : RoutineRepository {
    override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): com.boratreino.core.model.RoutineUpdate =
        com.boratreino.core.model.RoutineUpdate(emptyList(), emptyList())
    override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<com.boratreino.core.model.RoutineItem>) {}

        val moves = mutableListOf<Triple<Long, Long, Boolean>>()
        val removals = mutableListOf<Pair<Long, Long>>()
        val restored = mutableListOf<RoutineItem>()
        var removeResult: RoutineItem? = null
        var failMove = false

        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = emptyFlow()
        override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
        override suspend fun setTrainingDays(days: Set<java.time.DayOfWeek>) = Unit
        override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = routine.id
        override suspend fun deleteRoutine(id: Long) = Unit
        override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
        override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) {
            if (failMove) error("disk full")
            moves += Triple(routineId, machineId, up)
        }
        override suspend fun duplicateRoutine(id: Long): Long = 0L
        override suspend fun createRoutine(): Long = 0L
        override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
        override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? {
            removals += routineId to machineId
            return removeResult
        }
        override fun observePlanned(): kotlinx.coroutines.flow.Flow<List<com.boratreino.core.model.PlannedWorkout>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun planWorkout(date: java.time.LocalDate, routineId: Long) = Unit
        override suspend fun unplanWorkout(date: java.time.LocalDate, routineId: Long) = Unit
        override suspend fun restoreItem(item: RoutineItem) {
            restored += item
        }
    }

    private class FakeRestTimer : RestTimer {
        var skips = 0
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
        override fun extend(seconds: Int) = Unit
        override fun skip() {
            skips++
        }
    }

    private lateinit var training: FakeTraining
    private lateinit var routines: FakeRoutines
    private lateinit var rest: FakeRestTimer
    private lateinit var viewModel: TodayViewModel
    private val events = mutableListOf<TodayEvent>()

    private val ficha = RoutineWithItems(sample.routineB, sample.routineBItems)

    private fun planWith(active: RoutineWithItems?, choice: RoutineChoice = RoutineChoice.Suggested) = TodayPlan(
        mode = ScheduleMode.IN_TURN,
        routines = listOf(sample.routineA, sample.routineB, sample.routineC),
        suggested = sample.routineB,
        choice = choice,
        active = active,
        restDay = false,
    )

    private fun session() = OpenSession(
        Session(id = 1L, startedAt = t0, endedAt = null, routineId = sample.routineB.id, note = null),
        listOf(SessionMachine(sample.legPress, listOf(sample.set(1, sample.legPress, 0, 120.0, 12, t0)))),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = FakeTraining()
        routines = FakeRoutines()
        rest = FakeRestTimer()
        events.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.open(active: RoutineWithItems? = ficha, withSession: Boolean = false): TodayViewModel {
        training.plan.value = planWith(active, if (active == null) RoutineChoice.Free else RoutineChoice.Suggested)
        training.open.value = if (withSession) session() else null
        val machines = FakeMachines(
            listOf(sample.legPress, sample.legExtension, sample.latPulldown, sample.chestPress, sample.seatedRow),
        )
        viewModel = TodayViewModel(training, machines, routines, rest, FakeHistory(), FakeBodySettings(), TodayTestClock)
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
        return viewModel
    }

    private val editing: Boolean get() = viewModel.state.value.editing

    // Entering and leaving

    @Test
    fun `edit mode is off until it is asked for`() = runTest {
        open()

        assertFalse(editing)
    }

    @Test
    fun `startEditing turns it on when a ficha is active and no session is open`() = runTest {
        open()

        viewModel.startEditing()
        runCurrent()

        assertTrue(editing)
    }

    @Test
    fun `startEditing turns it on during an open session too`() = runTest {
        open(withSession = true)

        viewModel.startEditing()
        runCurrent()

        assertTrue(editing)
    }

    @Test
    fun `startEditing does nothing in Free`() = runTest {
        open(active = null)

        viewModel.startEditing()
        runCurrent()

        assertFalse(editing)
    }

    @Test
    fun `finishEditing turns it off`() = runTest {
        open()
        viewModel.startEditing()
        runCurrent()

        viewModel.finishEditing()
        runCurrent()

        assertFalse(editing)
    }

    @Test
    fun `choosing a routine or Free ends edit mode`() = runTest {
        open()
        viewModel.startEditing()
        runCurrent()
        assertTrue(editing)

        viewModel.choose(RoutineChoice.Free)
        runCurrent()

        assertFalse(editing)
        assertEquals(listOf<RoutineChoice>(RoutineChoice.Free), training.chosen)
    }

    @Test
    fun `choosing another routine ends edit mode`() = runTest {
        open(withSession = true)
        viewModel.startEditing()
        runCurrent()

        viewModel.choose(RoutineChoice.Chosen(sample.routineA.id))
        runCurrent()

        assertFalse(editing)
    }

    @Test
    fun `finishing the training ends edit mode`() = runTest {
        open(withSession = true)
        viewModel.startEditing()
        runCurrent()

        viewModel.finish()
        runCurrent()

        assertFalse(editing)
        assertEquals(1, training.finished)
    }

    @Test
    fun `edit mode is off when the active ficha goes away`() = runTest {
        open()
        viewModel.startEditing()
        runCurrent()

        training.plan.value = planWith(active = null, choice = RoutineChoice.Free)
        runCurrent()

        assertFalse(editing)
    }

    // Moving

    @Test
    fun `moveMachine moves the machine in the active ficha`() = runTest {
        open()

        viewModel.moveMachine(sample.legExtension.id, up = true)
        viewModel.moveMachine(sample.legPress.id, up = false)
        runCurrent()

        assertEquals(
            listOf(
                Triple(sample.routineB.id, sample.legExtension.id, true),
                Triple(sample.routineB.id, sample.legPress.id, false),
            ),
            routines.moves,
        )
    }

    @Test
    fun `moveMachine does nothing without a ficha`() = runTest {
        open(active = null)

        viewModel.moveMachine(sample.legPress.id, up = true)
        runCurrent()

        assertTrue(routines.moves.isEmpty())
    }

    @Test
    fun `a failed move shows the generic failure`() = runTest {
        open()
        routines.failMove = true

        viewModel.moveMachine(sample.legPress.id, up = false)
        runCurrent()

        assertEquals(listOf<TodayEvent>(TodayEvent.Failed), events)
    }

    // Removing

    @Test
    fun `removeMachine takes the machine out of the ficha and offers Undo`() = runTest {
        val item = sample.routineBItems[1]
        routines.removeResult = item
        open()

        viewModel.removeMachine(sample.legExtension.id)
        runCurrent()

        assertEquals(listOf(sample.routineB.id to sample.legExtension.id), routines.removals)
        assertEquals(listOf<TodayEvent>(TodayEvent.Removed(sample.routineB.name, item)), events)
    }

    @Test
    fun `removeMachine sends nothing when the machine was not in the ficha`() = runTest {
        routines.removeResult = null
        open()

        viewModel.removeMachine(sample.seatedRow.id)
        runCurrent()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `removing a machine keeps edit mode on`() = runTest {
        routines.removeResult = sample.routineBItems[0]
        open()
        viewModel.startEditing()
        runCurrent()

        viewModel.removeMachine(sample.legPress.id)
        runCurrent()

        assertTrue(editing)
    }

    @Test
    fun `restoring an item puts it back through the repository`() = runTest {
        open()
        val item = sample.routineBItems[2]

        viewModel.restoreItem(item)
        runCurrent()

        assertEquals(listOf(item), routines.restored)
    }
}

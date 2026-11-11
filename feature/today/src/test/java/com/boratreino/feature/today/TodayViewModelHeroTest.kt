package com.boratreino.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.BodyProfile
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
import com.boratreino.core.model.Sex
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.notify.RestTimerState
import java.time.Instant
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

/**
 * The hero's inputs on the Today view model: the mascot's sex from the body profile, the next exercise the
 * hero opens, and which action the hero's one button takes (START, CONTINUE, FINISH or ADD).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayViewModelHeroTest {
    private val sample = TodaySamples
    private val at = Instant.parse("2026-11-05T10:05:00Z")
    private val maleProfile = BodyProfile(Sex.MALE, birthYear = 1990, heightCm = 180, weightKg = 80.0)
    private val femaleProfile = BodyProfile(Sex.FEMALE, birthYear = 1992, heightCm = 165, weightKg = 60.0)
    private val routineMachines = listOf(sample.legPress, sample.legExtension, sample.latPulldown, sample.chestPress)

    private class HeroTraining : TrainingRepository {
        override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
        override suspend fun trainingSummary(sessionId: Long): com.boratreino.core.model.TrainingSummary? = null
        override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        val plan = MutableStateFlow(TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false))
        val open = MutableStateFlow<OpenSession?>(null)

        override fun observeOpenSession(): Flow<OpenSession?> = open
        override fun observeTodayPlan(): Flow<TodayPlan> = plan
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = MutableStateFlow(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override suspend fun closeStaleSession() = Unit
        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used")
        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = error("not used")
        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun finishIfRoutineComplete(): SessionSummary? = null
        override val pendingSummarySessionId: Flow<Long?> = MutableStateFlow(null)
        override suspend fun consumePendingSummary() = Unit
        override suspend fun discardMachineToday(machineId: Long): List<WorkoutSet> = emptyList()
        override suspend fun undoDiscardMachineToday(machineId: Long, sets: List<WorkoutSet>) = Unit
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
    }

    private class HeroMachines(private val all: List<Machine>) : MachineRepository {
        override suspend fun addFromLibrary(exercise: com.boratreino.core.model.LibraryExercise, portuguese: Boolean): Long = error("not used by this test")
        override fun observeLibraryIdsInUse(): Flow<Set<String>> = MutableStateFlow(emptySet())
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

    private class HeroRoutines : RoutineRepository {
        override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): com.boratreino.core.model.RoutineUpdate =
            com.boratreino.core.model.RoutineUpdate(emptyList(), emptyList())
        override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<RoutineItem>) {}
        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = emptyFlow()
        override fun observeLastRoutineId(): Flow<Long?> = kotlinx.coroutines.flow.flowOf(null)
        override fun observeLastTrained(): Flow<Map<Long, java.time.Instant>> = kotlinx.coroutines.flow.flowOf(emptyMap())
        override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
        override suspend fun setTrainingDays(days: Set<java.time.DayOfWeek>) = Unit
        override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = routine.id
        override suspend fun deleteRoutine(id: Long) = Unit
        override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
        override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit
        override suspend fun duplicateRoutine(id: Long): Long = 0L
        override suspend fun createRoutine(): Long = 0L
        override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
        override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? = null
        override fun observePlanned(): Flow<List<com.boratreino.core.model.PlannedWorkout>> = emptyFlow()
        override suspend fun planWorkout(date: java.time.LocalDate, routineId: Long) = Unit
        override suspend fun unplanWorkout(date: java.time.LocalDate, routineId: Long) = Unit
        override suspend fun reorderPlanned(date: java.time.LocalDate, routineIds: List<Long>) = Unit
        override suspend fun restoreItem(item: RoutineItem) = Unit
    }

    private class HeroRest : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
    }

    private lateinit var training: HeroTraining
    private lateinit var history: FakeHistory
    private lateinit var body: FakeBodySettings
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = HeroTraining()
        training.plan.value = routineBPlan()
        history = FakeHistory()
        body = FakeBodySettings()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        viewModel = TodayViewModel(
            training,
            HeroMachines(routineMachines),
            HeroRoutines(),
            HeroRest(),
            history,
            body,
            TodayTestClock,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private val state get() = viewModel.state.value

    private fun routineBPlan() = TodayPlan(
        mode = ScheduleMode.IN_TURN,
        routines = listOf(sample.routineA, sample.routineB, sample.routineC),
        suggested = sample.routineB,
        choice = RoutineChoice.Suggested,
        active = RoutineWithItems(sample.routineB, sample.routineBItems),
        restDay = false,
    )

    /** One working set of [machine] at [position] in session 1; ids are unique per machine and position. */
    private fun setOn(machine: Machine, position: Int) =
        TodaySamples.set(machine.id * 100L + position, machine, position, 100.0, 10, at)

    /** [count] working sets on [machine] in the open session. */
    private fun machineWithSets(machine: Machine, count: Int) =
        SessionMachine(machine, (0 until count).map { setOn(machine, it) })

    private fun openSession(vararg machines: SessionMachine) {
        training.open.value = OpenSession(
            Session(id = 1L, startedAt = at, endedAt = null, routineId = sample.routineB.id, note = null),
            machines.toList(),
        )
    }

    @Test
    fun `the mascot sex is null without a body profile`() = runTest {
        start()

        assertNull(state.mascotSex)
    }

    @Test
    fun `the mascot sex follows a male profile`() = runTest {
        body.profileFlow.value = maleProfile
        start()

        assertEquals(Sex.MALE, state.mascotSex)
    }

    @Test
    fun `the mascot sex follows a female profile`() = runTest {
        body.profileFlow.value = femaleProfile
        start()

        assertEquals(Sex.FEMALE, state.mascotSex)
    }

    @Test
    fun `the next exercise is the first one of the routine when nothing is logged`() = runTest {
        start()

        assertEquals(sample.legPress.id, state.nextExerciseId)
        assertEquals(HeroAction.START, state.heroPrimary)
    }

    @Test
    fun `the next exercise moves on once the first one has all its sets`() = runTest {
        openSession(machineWithSets(sample.legPress, 3))
        start()

        assertEquals(sample.legExtension.id, state.nextExerciseId)
        assertEquals(HeroAction.CONTINUE, state.heroPrimary)
    }

    @Test
    fun `a part-done exercise stays the next one and the hero continues`() = runTest {
        openSession(machineWithSets(sample.legPress, 1))
        start()

        assertEquals(sample.legPress.id, state.nextExerciseId)
        assertEquals(HeroAction.CONTINUE, state.heroPrimary)
    }

    @Test
    fun `with every exercise done there is no next one and the hero finishes`() = runTest {
        openSession(
            machineWithSets(sample.legPress, 3),
            machineWithSets(sample.legExtension, 3),
            machineWithSets(sample.latPulldown, 3),
            machineWithSets(sample.chestPress, 3),
        )
        start()

        assertNull(state.nextExerciseId)
        assertEquals(HeroAction.FINISH, state.heroPrimary)
    }

    @Test
    fun `without a routine there is no next exercise and the hero adds one`() = runTest {
        training.plan.value = TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false)
        start()

        assertNull(state.nextExerciseId)
        assertEquals(HeroAction.ADD, state.heroPrimary)
    }
}

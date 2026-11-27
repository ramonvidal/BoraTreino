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
import com.boratreino.core.testing.StubMachineRepository
import com.boratreino.core.testing.StubRoutineRepository
import com.boratreino.core.testing.StubTrainingRepository
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
import com.boratreino.core.data.RemovedSets

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

    private class HeroTraining : StubTrainingRepository() {
        val plan = MutableStateFlow(TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false))
        val open = MutableStateFlow<OpenSession?>(null)

        override fun observeOpenSession(): Flow<OpenSession?> = open
        override fun observeTodayPlan(): Flow<TodayPlan> = plan
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
    }

    private class HeroMachines(private val all: List<Machine>) : StubMachineRepository(all) {
        override suspend fun delete(machineId: Long): Boolean = true
    }

    private class HeroRoutines : StubRoutineRepository() {
        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = emptyFlow()
        override fun observePlanned(): Flow<List<com.boratreino.core.model.PlannedWorkout>> = emptyFlow()
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

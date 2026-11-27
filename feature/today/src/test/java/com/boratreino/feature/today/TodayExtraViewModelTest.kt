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
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.notify.RestTimerState
import com.boratreino.core.testing.StubMachineRepository
import com.boratreino.core.testing.StubRoutineRepository
import com.boratreino.core.testing.StubTrainingRepository
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.boratreino.core.data.RemovedSets

/**
 * The extra-workout switch on the Today view model: setting it chooses the active routine with the flag, it does
 * nothing without an active routine, it works during a session, and choosing another routine clears the flag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayExtraViewModelTest {
    private val sample = TodaySamples
    private val at = Instant.parse("2026-11-05T10:05:00Z")

    /** Records the routine choices the view model makes; the rest of the training repository is not used here. */
    private class RecordingTraining : StubTrainingRepository() {
        val chosen = mutableListOf<RoutineChoice>()
        val plan = MutableStateFlow(TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false))
        val open = MutableStateFlow<OpenSession?>(null)

        override suspend fun chooseRoutine(choice: RoutineChoice) {
            chosen += choice
        }

        override fun observeOpenSession(): Flow<OpenSession?> = open
        override fun observeTodayPlan(): Flow<TodayPlan> = plan
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
    }

    private class NoMachines : StubMachineRepository() {
        override suspend fun delete(machineId: Long): Boolean = true
    }

    private class NoRoutines : StubRoutineRepository() {
        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = emptyFlow()
        override fun observePlanned(): Flow<List<PlannedWorkout>> = emptyFlow()
    }

    private class IdleRest : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
    }

    private lateinit var training: RecordingTraining
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = RecordingTraining()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        viewModel = TodayViewModel(
            training,
            NoMachines(),
            NoRoutines(),
            IdleRest(),
            FakeHistory(),
            FakeBodySettings(),
            TodayTestClock,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private val state get() = viewModel.state.value

    /** The plan with routine B active (or none when [active] is null) and [choice] as the owner's choice. */
    private fun planWith(choice: RoutineChoice, active: Routine? = sample.routineB): TodayPlan = TodayPlan(
        mode = ScheduleMode.IN_TURN,
        routines = listOf(sample.routineA, sample.routineB, sample.routineC),
        suggested = sample.routineB,
        choice = choice,
        active = active?.let { RoutineWithItems(it, emptyList()) },
        restDay = false,
    )

    @Test
    fun `turning the switch on chooses the active routine as an extra workout`() = runTest {
        training.plan.value = planWith(RoutineChoice.Suggested)
        start()

        viewModel.setExtra(true)

        assertEquals(listOf<RoutineChoice>(RoutineChoice.Chosen(sample.routineB.id, extra = true)), training.chosen)
    }

    @Test
    fun `turning the switch off chooses the active routine without the flag`() = runTest {
        training.plan.value = planWith(RoutineChoice.Chosen(sample.routineB.id, extra = true))
        start()

        viewModel.setExtra(false)

        assertEquals(listOf<RoutineChoice>(RoutineChoice.Chosen(sample.routineB.id, extra = false)), training.chosen)
    }

    @Test
    fun `the switch does nothing without an active routine`() = runTest {
        training.plan.value = planWith(RoutineChoice.Free, active = null)
        start()

        viewModel.setExtra(true)

        assertTrue(training.chosen.isEmpty())
    }

    @Test
    fun `the switch works during a session on the open session's routine`() = runTest {
        training.plan.value = planWith(RoutineChoice.Suggested)
        training.open.value = OpenSession(
            Session(id = 1L, startedAt = at, endedAt = null, routineId = sample.routineB.id, note = null),
            emptyList(),
        )
        start()

        viewModel.setExtra(true)

        assertEquals(listOf<RoutineChoice>(RoutineChoice.Chosen(sample.routineB.id, extra = true)), training.chosen)
    }

    @Test
    fun `the routine state reports the extra flag of the active choice`() = runTest {
        training.plan.value = planWith(RoutineChoice.Chosen(sample.routineB.id, extra = true))
        start()

        assertTrue(requireNotNull(state.routines).extra)
    }

    @Test
    fun `the routine state has no extra flag for a routine that is not chosen as extra`() = runTest {
        training.plan.value = planWith(RoutineChoice.Suggested)
        start()

        assertFalse(requireNotNull(state.routines).extra)
    }

    @Test
    fun `choosing another routine after an extra one leaves the extra flag off`() = runTest {
        training.plan.value = planWith(RoutineChoice.Suggested)
        start()
        viewModel.setExtra(true)

        viewModel.choose(RoutineChoice.Chosen(sample.routineA.id))

        assertEquals(
            listOf<RoutineChoice>(
                RoutineChoice.Chosen(sample.routineB.id, extra = true),
                RoutineChoice.Chosen(sample.routineA.id, extra = false),
            ),
            training.chosen,
        )
    }
}

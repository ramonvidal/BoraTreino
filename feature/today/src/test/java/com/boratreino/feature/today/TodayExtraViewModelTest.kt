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
    private class RecordingTraining : TrainingRepository {
        val chosen = mutableListOf<RoutineChoice>()
        val plan = MutableStateFlow(TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false))
        val open = MutableStateFlow<OpenSession?>(null)

        override suspend fun chooseRoutine(choice: RoutineChoice) {
            chosen += choice
        }

        override fun observeOpenSession(): Flow<OpenSession?> = open
        override fun observeTodayPlan(): Flow<TodayPlan> = plan
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = MutableStateFlow(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override suspend fun closeStaleSession() = Unit
        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used")
        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = error("not used")
        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet): RemovedSets = RemovedSets(listOf(set), null)
        override suspend fun restoreSets(removed: RemovedSets) = Unit
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun finishIfRoutineComplete(): SessionSummary? = null
        override val pendingSummarySessionId: Flow<Long?> = MutableStateFlow(null)
        override suspend fun consumePendingSummary() = Unit
        override suspend fun discardMachineToday(machineId: Long): RemovedSets = RemovedSets(emptyList(), null)
        override suspend fun undoDiscardMachineToday(machineId: Long, removed: RemovedSets) = Unit
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
        override suspend fun trainingSummary(sessionId: Long): com.boratreino.core.model.TrainingSummary? = null
        override suspend fun sessionRoutineId(sessionId: Long): Long? = null
    }

    private class NoMachines : MachineRepository {
    override suspend fun kindConversions(machineId: Long): Map<com.boratreino.core.model.MachineKind, com.boratreino.core.model.KindConversion> = emptyMap()
    override suspend fun saveConvertingSets(machine: com.boratreino.core.model.Machine, factor: Int?): Boolean = true
        override suspend fun addFromLibrary(exercise: com.boratreino.core.model.LibraryExercise, portuguese: Boolean): Long = error("not used")
        override fun observeLibraryIdsInUse(): Flow<Set<String>> = flowOf(emptySet())
        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(null)
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = MachineUsage(0, emptyList())
        override suspend fun delete(machineId: Long): Boolean = true
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private class NoRoutines : RoutineRepository {
        override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): com.boratreino.core.model.RoutineUpdate =
            com.boratreino.core.model.RoutineUpdate(emptyList(), emptyList())
        override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<RoutineItem>) {}
        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = emptyFlow()
        override fun observeLastRoutineId(): Flow<Long?> = flowOf(null)
        override fun observeLastTrained(): Flow<Map<Long, Instant>> = flowOf(emptyMap())
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
        override fun observePlanned(): Flow<List<PlannedWorkout>> = emptyFlow()
        override suspend fun planWorkout(date: LocalDate, routineId: Long) = Unit
        override suspend fun unplanWorkout(date: LocalDate, routineId: Long) = Unit
        override suspend fun reorderPlanned(date: LocalDate, routineIds: List<Long>) = Unit
        override suspend fun restoreItem(item: RoutineItem) = Unit
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

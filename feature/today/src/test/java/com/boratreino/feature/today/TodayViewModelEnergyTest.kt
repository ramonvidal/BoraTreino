package com.boratreino.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.DayKind
import com.boratreino.core.model.EnergyRules
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
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionSets
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.Sex
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Today's quiet energy line: when it shows, what kind of day it is and that it follows the sessions. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayViewModelEnergyTest {
    private val sample = TodaySamples
    private val today = LocalDate.of(2026, 11, 5)
    private val now = Instant.parse("2026-11-05T12:00:00Z")
    private val profile = BodyProfile(Sex.MALE, birthYear = 1990, heightCm = 180, weightKg = 80.0)

    private class EnergyTraining : TrainingRepository {
    override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
    override suspend fun trainingSummary(sessionId: Long): com.boratreino.core.model.TrainingSummary? = null
    override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        val plan = MutableStateFlow(noRoutinePlan())
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

    private class EnergyMachines(private val all: List<Machine>) : MachineRepository {
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

    private class EnergyRoutines : RoutineRepository {
    override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): com.boratreino.core.model.RoutineUpdate =
        com.boratreino.core.model.RoutineUpdate(emptyList(), emptyList())
    override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<com.boratreino.core.model.RoutineItem>) {}

        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = emptyFlow()
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
        override fun observePlanned(): kotlinx.coroutines.flow.Flow<List<com.boratreino.core.model.PlannedWorkout>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun planWorkout(date: java.time.LocalDate, routineId: Long) = Unit
        override suspend fun unplanWorkout(date: java.time.LocalDate, routineId: Long) = Unit
        override suspend fun reorderPlanned(date: java.time.LocalDate, routineIds: List<Long>) = Unit
        override suspend fun restoreItem(item: RoutineItem) = Unit
    }

    private class EnergyRest : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
    }

    private lateinit var training: EnergyTraining
    private lateinit var history: FakeHistory
    private lateinit var body: FakeBodySettings
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = EnergyTraining()
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
            EnergyMachines(listOf(sample.legPress, sample.legExtension, sample.latPulldown, sample.chestPress)),
            EnergyRoutines(),
            EnergyRest(),
            history,
            body,
            TodayTestClock,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private fun suggestRoutine() {
        training.plan.value = TodayPlan(
            mode = ScheduleMode.IN_TURN,
            routines = listOf(sample.routineA, sample.routineB, sample.routineC),
            suggested = sample.routineB,
            choice = RoutineChoice.Suggested,
            active = RoutineWithItems(sample.routineB, sample.routineBItems),
            restDay = false,
        )
    }

    private fun restDayPlan() {
        training.plan.value = TodayPlan(
            mode = ScheduleMode.WEEKDAYS,
            routines = listOf(sample.routineA, sample.routineB, sample.routineC),
            suggested = null,
            choice = RoutineChoice.Suggested,
            active = null,
            restDay = true,
        )
    }

    private fun session(id: Long = 1L, startedAt: String, endedAt: String?) = Session(
        id = id,
        startedAt = Instant.parse(startedAt),
        endedAt = endedAt?.let(Instant::parse),
        routineId = null,
        note = null,
    )

    private fun strengthSet(sessionId: Long = 1L, id: Long = 1L) = WorkoutSet(
        id = id, sessionId = sessionId, machineId = sample.legPress.id, position = 0,
        weightKg = 50.0, reps = 10, seconds = null, calories = null, distanceM = null,
        loggedAt = Instant.parse("2026-11-05T10:05:00Z"),
    )

    private fun detail(session: Session, vararg sets: WorkoutSet) =
        SessionDetail(session, null, if (sets.isEmpty()) emptyList() else listOf(SessionMachine(sample.legPress, sets.toList())))

    private val energy get() = viewModel.state.value.energy

    private fun expected(sessions: List<SessionSets>, trainingExpected: Boolean) =
        EnergyRules.day(profile, today, now, sessions, trainingExpected)

    @Test
    fun `without a profile there is no energy line`() = runTest {
        suggestRoutine()
        start()

        assertNull(energy)
    }

    @Test
    fun `with the switch off there is no energy line`() = runTest {
        body.profileFlow.value = profile
        body.showFlow.value = false
        start()

        assertNull(energy)
    }

    @Test
    fun `no sessions and no routine suggested is a rest day`() = runTest {
        body.profileFlow.value = profile
        start()

        assertEquals(DayEnergyUi(2100, DayKind.REST), energy)
    }

    @Test
    fun `a rest day in the plan is a rest day`() = runTest {
        body.profileFlow.value = profile
        restDayPlan()
        start()

        assertEquals(DayKind.REST, energy?.kind)
    }

    @Test
    fun `a suggested routine means training is expected`() = runTest {
        body.profileFlow.value = profile
        suggestRoutine()
        start()

        // 2100 + 4 * 80 * 1 h = 2420
        assertEquals(DayEnergyUi(2420, DayKind.TRAINING_EXPECTED), energy)
        val result = expected(emptyList(), trainingExpected = true)
        assertEquals(DayEnergyUi(result.kcal, result.kind), energy)
    }

    @Test
    fun `a session today with sets is a trained day`() = runTest {
        body.profileFlow.value = profile
        suggestRoutine()
        val done = session(startedAt = "2026-11-05T10:00:00Z", endedAt = "2026-11-05T10:30:00Z")
        history.day.value = listOf(detail(done, strengthSet()))
        start()

        // half an hour: 2100 + 160 = 2260
        assertEquals(DayEnergyUi(2260, DayKind.TRAINED), energy)
        val result = expected(listOf(SessionSets(done, listOf(strengthSet()))), trainingExpected = true)
        assertEquals(DayEnergyUi(result.kcal, result.kind), energy)
    }

    @Test
    fun `the day asked for is today by the clock`() = runTest {
        body.profileFlow.value = profile
        start()

        assertEquals(true, history.daysAsked.isNotEmpty())
        assertEquals(setOf(today), history.daysAsked.toSet())
    }

    @Test
    fun `an open session missing from the day list is counted`() = runTest {
        body.profileFlow.value = profile
        val open = session(id = 2L, startedAt = "2026-11-05T11:30:00Z", endedAt = null)
        training.open.value = OpenSession(open, listOf(SessionMachine(sample.legPress, listOf(strengthSet(sessionId = 2L)))))
        start()

        // open for 30 minutes up to the clock: 2100 + 160 = 2260
        assertEquals(DayEnergyUi(2260, DayKind.TRAINED), energy)
    }

    @Test
    fun `an open session that the day list has is not counted twice`() = runTest {
        body.profileFlow.value = profile
        val open = session(id = 2L, startedAt = "2026-11-05T11:30:00Z", endedAt = null)
        val sets = listOf(strengthSet(sessionId = 2L))
        training.open.value = OpenSession(open, listOf(SessionMachine(sample.legPress, sets)))
        history.day.value = listOf(SessionDetail(open, null, listOf(SessionMachine(sample.legPress, sets))))
        start()

        assertEquals(DayEnergyUi(2260, DayKind.TRAINED), energy)
    }

    @Test
    fun `an open session without sets expects training`() = runTest {
        body.profileFlow.value = profile
        training.open.value = OpenSession(session(id = 2L, startedAt = "2026-11-05T11:55:00Z", endedAt = null), emptyList())
        start()

        assertEquals(DayEnergyUi(2420, DayKind.TRAINING_EXPECTED), energy)
    }

    @Test
    fun `logging a set updates the line`() = runTest {
        body.profileFlow.value = profile
        val open = session(id = 2L, startedAt = "2026-11-05T11:00:00Z", endedAt = null)
        training.open.value = OpenSession(open, emptyList())
        start()
        assertEquals(DayKind.TRAINING_EXPECTED, energy?.kind)

        training.open.value = OpenSession(open, listOf(SessionMachine(sample.legPress, listOf(strengthSet(sessionId = 2L)))))
        runCurrent()

        // one hour open: 2100 + 320 = 2420
        assertEquals(DayEnergyUi(2420, DayKind.TRAINED), energy)
    }

    @Test
    fun `turning the switch off and on hides and shows the line`() = runTest {
        body.profileFlow.value = profile
        start()
        assertNotNull(energy)

        body.showFlow.value = false
        runCurrent()
        assertNull(energy)

        body.showFlow.value = true
        runCurrent()
        assertNotNull(energy)
    }

    @Test
    fun `the goal changes the figure`() = runTest {
        body.profileFlow.value = profile.copy(goalAdjustmentPercent = 10)
        start()

        // 2100 * 1.1 = 2310
        assertEquals(DayEnergyUi(2310, DayKind.REST), energy)
    }

    private companion object {
        fun noRoutinePlan() =
            TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false)
    }
}

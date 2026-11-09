package com.boratreino.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.DayKind
import com.boratreino.core.model.HealthExport
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
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.Sex
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.notify.RestTimerState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A clock the test moves by hand. */
private class MovableClock(start: String) : Clock() {
    var now: Instant = Instant.parse(start)

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}

/** A history with one state flow per date; remembers every date it was asked for. */
private class PerDayHistory : HistoryRepository {
    private val days = mutableMapOf<LocalDate, MutableStateFlow<List<SessionDetail>>>()
    val asked = mutableListOf<LocalDate>()

    fun day(date: LocalDate): MutableStateFlow<List<SessionDetail>> = days.getOrPut(date) { MutableStateFlow(emptyList()) }

    override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> {
        asked += date
        return day(date)
    }

    override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = emptyFlow()
    override fun observeSession(sessionId: Long): Flow<SessionDetail?> = emptyFlow()
    override fun observeTrainingDates(): Flow<Set<LocalDate>> = emptyFlow()
    override suspend fun addSession(date: LocalDate, routineId: Long?): Long = 0L
    override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) = Unit
    override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet = error("not used")
    override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet = error("not used")
    override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> = emptyList()
    override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
    override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
    override suspend fun deleteSession(sessionId: Long): SessionDetail? = null
    override suspend fun restoreSession(detail: SessionDetail) = Unit
    override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> = emptyList()
}

/** The energy line follows the clock's date: it is recomputed for the new day after midnight. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayViewModelMidnightTest {
    private val sample = TodaySamples
    private val day1 = LocalDate.of(2026, 11, 5)
    private val day2 = LocalDate.of(2026, 11, 6)
    private val day3 = LocalDate.of(2026, 11, 7)
    private val profile = BodyProfile(Sex.MALE, birthYear = 1990, heightCm = 180, weightKg = 80.0)

    // Wake-up: the 12 hours left of the day plus the 1 second margin.
    private val firstWakeMs = 12 * 3_600_000L + 1_000L

    // From 00:00:05 the day has 86395 s left, plus the 1 second margin.
    private val fullDayWakeMs = 86_395_000L + 1_000L

    private class MidTraining : TrainingRepository {
    override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
    override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        override fun observeOpenSession(): Flow<OpenSession?> = MutableStateFlow(null)
        override fun observeTodayPlan(): Flow<TodayPlan> =
            MutableStateFlow(TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false))
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
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
    }

    private class MidMachines : MachineRepository {
    override suspend fun addFromLibrary(exercise: com.boratreino.core.model.LibraryExercise, portuguese: Boolean): Long = error("not used by this test")
    override fun observeLibraryIdsInUse(): kotlinx.coroutines.flow.Flow<Set<String>> = kotlinx.coroutines.flow.flowOf(emptySet())

        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = MutableStateFlow(emptyList())
        override fun observeMachine(id: Long): Flow<Machine?> = MutableStateFlow(null)
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = MutableStateFlow(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = MutableStateFlow(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = MachineUsage(0, emptyList())
        override suspend fun delete(machineId: Long): Boolean = true
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private class MidRoutines : RoutineRepository {
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
        override suspend fun restoreItem(item: RoutineItem) = Unit
    }

    private class MidRest : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
    }

    private lateinit var history: PerDayHistory
    private lateinit var body: FakeBodySettings
    private lateinit var clock: MovableClock
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        history = PerDayHistory()
        body = FakeBodySettings()
        body.profileFlow.value = profile
        clock = MovableClock("2026-11-05T12:00:00Z")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        viewModel = TodayViewModel(MidTraining(), MidMachines(), MidRoutines(), MidRest(), history, body, clock)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private fun yesterdaySession() = Session(
        id = 1L,
        startedAt = Instant.parse("2026-11-05T10:00:00Z"),
        endedAt = Instant.parse("2026-11-05T10:30:00Z"),
        routineId = null,
        note = null,
    )

    private fun yesterdaySet() = WorkoutSet(
        id = 1L, sessionId = 1L, machineId = sample.legPress.id, position = 0,
        weightKg = 50.0, reps = 10, seconds = null, calories = null, distanceM = null,
        loggedAt = Instant.parse("2026-11-05T10:05:00Z"),
    )

    private fun TestScope.passMidnight(newClock: String, waitMs: Long) {
        clock.now = Instant.parse(newClock)
        advanceTimeBy(waitMs + 1)
        runCurrent()
    }

    private val energy get() = viewModel.state.value.energy

    @Test
    fun `at start the day asked for is the clock's date`() = runTest {
        start()

        assertEquals(listOf(day1), history.asked)
    }

    @Test
    fun `after midnight the history is asked for the new date`() = runTest {
        start()

        passMidnight("2026-11-06T00:00:05Z", firstWakeMs)

        assertEquals(listOf(day1, day2), history.asked)
    }

    @Test
    fun `a session logged yesterday no longer makes today trained`() = runTest {
        history.day(day1).value = listOf(SessionDetail(yesterdaySession(), null, listOf(SessionMachine(sample.legPress, listOf(yesterdaySet())))))
        start()
        // half an hour of training: 2100 + 4 * 80 * 0.5 = 2260
        assertEquals(DayEnergyUi(2260, DayKind.TRAINED), energy)

        passMidnight("2026-11-06T00:00:05Z", firstWakeMs)

        // the new day is empty and no routine is suggested: a rest day, 2100
        assertEquals(DayEnergyUi(2100, DayKind.REST), energy)
    }

    @Test
    fun `the new day's sessions are used`() = runTest {
        start()
        assertEquals(DayEnergyUi(2100, DayKind.REST), energy)

        passMidnight("2026-11-06T00:00:05Z", firstWakeMs)
        val session = Session(
            id = 2L,
            startedAt = Instant.parse("2026-11-06T00:00:00Z"),
            endedAt = Instant.parse("2026-11-06T00:03:00Z"),
            routineId = null,
            note = null,
        )
        val set = yesterdaySet().copy(id = 2L, sessionId = 2L, loggedAt = Instant.parse("2026-11-06T00:02:00Z"))
        history.day(day2).value = listOf(SessionDetail(session, null, listOf(SessionMachine(sample.legPress, listOf(set)))))
        runCurrent()

        assertEquals(DayKind.TRAINED, energy?.kind)
    }

    @Test
    fun `it keeps working across two midnights`() = runTest {
        start()

        passMidnight("2026-11-06T00:00:05Z", firstWakeMs)
        passMidnight("2026-11-07T00:00:05Z", fullDayWakeMs)

        assertEquals(listOf(day1, day2, day3), history.asked)
        assertEquals(DayEnergyUi(2100, DayKind.REST), energy)
    }

    @Test
    fun `a clock that never moves does not spin`() = runTest {
        start()

        advanceUntilIdle()

        assertEquals(listOf(day1), history.asked)
        assertEquals(DayEnergyUi(2100, DayKind.REST), energy)
    }
}

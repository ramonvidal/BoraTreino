package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.MeasurementRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.StatsRepository
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MeasurementRules
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.Sex
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.settings.BodySettings
import com.boratreino.core.settings.TrainingSettings
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Records every query; the report it returns carries the asked period. */
    private class FakeStatsRepository : StatsRepository {
        val calls = mutableListOf<Pair<StatsPeriod, Int>>()

        override fun observeStats(period: StatsPeriod, weeklyGoal: Int): Flow<StatsReport> = flow {
            calls += period to weeklyGoal
            emit(StatsSamples.full.copy(period = period))
        }
    }

    private class FakeSettings(initialGoal: Int = 4) : TrainingSettings {
        val goal = MutableStateFlow(initialGoal)
        override val weeklyGoal: Flow<Int> get() = goal
        override suspend fun setWeeklyGoal(goal: Int) {
            this.goal.value = goal.coerceIn(1, 7)
        }
        override val reminders: Flow<ReminderSettings> = MutableStateFlow(ReminderSettings())
        override suspend fun setReminders(settings: ReminderSettings) = Unit
        override val restTimer: Flow<RestTimerSettings> = MutableStateFlow(RestTimerSettings())
        override suspend fun setRestTimer(settings: RestTimerSettings) = Unit
        override val warmupCalculator: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setWarmupCalculator(enabled: Boolean) = Unit
    }

    /** Body settings on state flows: the profile and the weight entries (newest first, as stored). */
    private class FakeBody(profile: BodyProfile? = null, weights: List<WeightEntry> = emptyList()) : BodySettings {
        val profileFlow = MutableStateFlow(profile)
        val weightsFlow = MutableStateFlow(weights)
        override val profile: Flow<BodyProfile?> get() = profileFlow
        override val weights: Flow<List<WeightEntry>> get() = weightsFlow
        override val showEnergyEstimate: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setProfile(profile: BodyProfile) {
            profileFlow.value = profile
        }
        override suspend fun addWeight(date: LocalDate, kg: Double) = Unit
        override suspend fun setShowEnergyEstimate(show: Boolean) = Unit
    }

    /** Keeps the measurements newest first, as the repository does; [add] clamps like the real one. */
    private class FakeMeasurementRepository(initial: List<BodyMeasurement> = emptyList()) : MeasurementRepository {
        val items = MutableStateFlow(initial.sortedWith(newestFirst))
        val added = mutableListOf<Triple<LocalDate, MeasurementType, Double>>()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

        override fun observeAll(): Flow<List<BodyMeasurement>> = items

        override suspend fun add(date: LocalDate, type: MeasurementType, valueCm: Double): Long {
            added += Triple(date, type, valueCm)
            val id = nextId++
            val entry = BodyMeasurement(id = id, date = date, type = type, valueCm = MeasurementRules.clamp(valueCm))
            items.value = (items.value + entry).sortedWith(newestFirst)
            return id
        }

        override suspend fun delete(id: Long): BodyMeasurement? {
            val gone = items.value.firstOrNull { it.id == id }
            items.value = items.value.filterNot { it.id == id }
            return gone
        }

        override suspend fun restore(measurement: BodyMeasurement) {
            items.value = (items.value + measurement).sortedWith(newestFirst)
        }

        private companion object {
            val newestFirst = compareByDescending<BodyMeasurement> { it.date }.thenByDescending { it.id }
        }
    }

    private val repository = FakeStatsRepository()
    private val settings = FakeSettings()
    private val body = FakeBody()
    private val measurements = FakeMeasurementRepository()
    private val trainingDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    private val history = answering<HistoryRepository>(
        "observeTrainingDates" to trainingDates,
        "observeDay" to flowOf(emptyList<SessionDetail>()),
    )
    private val machines = answering<MachineRepository>("observeMachines" to flowOf(emptyList<Machine>()))
    private val routines = answering<RoutineRepository>(
        "observePlanned" to flowOf(emptyList<PlannedWorkout>()),
        "observePlan" to flowOf(RoutinePlanWithRoutines(RoutinePlan(1L, ScheduleMode.IN_TURN), emptyList())),
        "observeLastRoutineId" to flowOf<Long?>(null),
    )

    private fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        bodySettings: BodySettings = body,
        measurementRepository: MeasurementRepository = measurements,
    ): StatsViewModel {
        val vm = StatsViewModel(repository, settings, bodySettings, handle, measurementRepository, history, machines, routines, statsTestClock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
        return vm
    }

    @Test
    fun `before anything is collected the state is the empty initial one`() = runTest(dispatcher) {
        val vm = StatsViewModel(repository, settings, body, SavedStateHandle(), measurements, history, machines, routines, statsTestClock)
        val state = vm.state.value
        assertFalse(state.loaded)
        assertNull(state.report)
        assertEquals(StatsPeriod.FOUR_WEEKS, state.period)
        assertEquals(4, state.weeklyGoal)
        assertTrue(state.measurements.isEmpty())
    }

    @Test
    fun `the default period is four weeks`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(StatsPeriod.FOUR_WEEKS, vm.state.value.period)
        assertEquals(StatsPeriod.FOUR_WEEKS, vm.state.value.report!!.period)
    }

    @Test
    fun `the report loads and loaded becomes true`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.loaded)
        assertEquals(StatsSamples.full.copy(period = StatsPeriod.FOUR_WEEKS), vm.state.value.report)
    }

    @Test
    fun `the weekly goal from the settings is passed to the repository`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(StatsPeriod.FOUR_WEEKS to 4, repository.calls.last())
        assertEquals(4, vm.state.value.weeklyGoal)
    }

    @Test
    fun `a different goal in the settings is used from the start`() = runTest(dispatcher) {
        settings.goal.value = 6
        val vm = viewModel()
        assertEquals(StatsPeriod.FOUR_WEEKS to 6, repository.calls.last())
        assertEquals(6, vm.state.value.weeklyGoal)
    }

    @Test
    fun `changing the goal queries the repository again`() = runTest(dispatcher) {
        val vm = viewModel()
        settings.goal.value = 2
        runCurrent()
        assertEquals(StatsPeriod.FOUR_WEEKS to 2, repository.calls.last())
        assertEquals(2, vm.state.value.weeklyGoal)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `setPeriod switches the report`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setPeriod(StatsPeriod.ONE_YEAR)
        runCurrent()

        assertEquals(StatsPeriod.ONE_YEAR, vm.state.value.period)
        assertEquals(StatsPeriod.ONE_YEAR, vm.state.value.report!!.period)
        assertEquals(StatsPeriod.ONE_YEAR to 4, repository.calls.last())
    }

    @Test
    fun `every period can be picked`() = runTest(dispatcher) {
        val vm = viewModel()
        StatsPeriod.entries.forEach { period ->
            vm.setPeriod(period)
            runCurrent()
            assertEquals(period, vm.state.value.period)
            assertEquals(period, vm.state.value.report!!.period)
        }
    }

    @Test
    fun `the period is kept in the saved state handle`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)

        vm.setPeriod(StatsPeriod.FOUR_WEEKS)
        runCurrent()

        // A new view model over the same handle (rotation, process death) starts on the saved period.
        val restored = viewModel(handle)
        assertEquals(StatsPeriod.FOUR_WEEKS, restored.state.value.period)
        assertEquals(StatsPeriod.FOUR_WEEKS, restored.state.value.report!!.period)
    }

    @Test
    fun `the one week period is restored from the saved state and a fresh handle starts on four weeks`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        viewModel(handle).setPeriod(StatsPeriod.ONE_WEEK)
        runCurrent()

        val restored = viewModel(handle)
        assertEquals(StatsPeriod.ONE_WEEK, restored.state.value.period)
        assertEquals(StatsPeriod.ONE_WEEK, restored.state.value.report!!.period)
        assertEquals(StatsPeriod.FOUR_WEEKS, viewModel(SavedStateHandle()).state.value.period)
    }

    @Test
    fun `a view model over a fresh handle does not inherit another one's period`() = runTest(dispatcher) {
        viewModel().setPeriod(StatsPeriod.ALL)
        runCurrent()
        val other = viewModel(SavedStateHandle())
        assertEquals(StatsPeriod.FOUR_WEEKS, other.state.value.period)
    }

    // The Overview inputs

    @Test
    fun `the frequency counts the training days of the current week`() = runTest(dispatcher) {
        trainingDates.value = setOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 10, 20))
        val vm = viewModel()

        assertEquals(1, vm.state.value.frequency!!.thisWeek)
        assertEquals(4, vm.state.value.frequency!!.goal)
    }

    @Test
    fun `the body section tiles are the newest measurement of each type`() = runTest(dispatcher) {
        val vm = viewModel(
            measurementRepository = FakeMeasurementRepository(
                listOf(
                    BodyMeasurement(id = 1, date = LocalDate.of(2026, 10, 1), type = MeasurementType.HIPS, valueCm = 99.0),
                    BodyMeasurement(id = 2, date = LocalDate.of(2026, 11, 1), type = MeasurementType.HIPS, valueCm = 98.0),
                ),
            ),
        )

        assertEquals(listOf(98.0), vm.state.value.body.tiles.map { it.valueCm })
    }

    // Body weight

    private val profile = BodyProfile(Sex.MALE, 1990, 178, 82.0)

    @Test
    fun `the body weight is the newest weight entry, and the entries are passed on as stored`() = runTest(dispatcher) {
        val entries = listOf(WeightEntry(LocalDate.of(2026, 11, 2), 78.5), WeightEntry(LocalDate.of(2026, 10, 1), 80.0))
        val vm = viewModel(bodySettings = FakeBody(profile, entries))

        assertEquals(78.5, vm.state.value.bodyWeightKg!!, 1e-9)
        assertEquals(entries, vm.state.value.weights)
    }

    @Test
    fun `without weight entries the profile's weight is the body weight`() = runTest(dispatcher) {
        val vm = viewModel(bodySettings = FakeBody(profile))

        assertEquals(82.0, vm.state.value.bodyWeightKg!!, 1e-9)
        assertTrue(vm.state.value.weights.isEmpty())
    }

    @Test
    fun `without a profile or any entry the body weight is null`() = runTest(dispatcher) {
        val vm = viewModel(bodySettings = FakeBody())

        assertNull(vm.state.value.bodyWeightKg)
        assertTrue(vm.state.value.weights.isEmpty())
    }

    @Test
    fun `a weight entry added while the page is open changes the body weight at once`() = runTest(dispatcher) {
        val fake = FakeBody(profile)
        val vm = viewModel(bodySettings = fake)

        fake.weightsFlow.value = listOf(WeightEntry(LocalDate.of(2026, 11, 3), 81.0))
        runCurrent()

        assertEquals(81.0, vm.state.value.bodyWeightKg!!, 1e-9)
    }

    // Body measurements

    private val measured = listOf(
        BodyMeasurement(id = 2, date = LocalDate.of(2026, 11, 1), type = MeasurementType.WAIST, valueCm = 82.5),
        BodyMeasurement(id = 1, date = LocalDate.of(2026, 10, 1), type = MeasurementType.WAIST, valueCm = 84.0),
    )

    @Test
    fun `the measurements come from the repository newest first`() = runTest(dispatcher) {
        val vm = viewModel(measurementRepository = FakeMeasurementRepository(measured))

        assertEquals(measured, vm.state.value.measurements)
    }

    @Test
    fun `adding a measurement passes it to the repository and it appears in the state`() = runTest(dispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(measurementRepository = repo)
        val date = LocalDate.of(2026, 11, 2)

        vm.addMeasurement(date, MeasurementType.CHEST, 90.0)
        runCurrent()

        assertEquals(listOf(Triple(date, MeasurementType.CHEST, 90.0)), repo.added)
        val shown = vm.state.value.measurements.single()
        assertEquals(date, shown.date)
        assertEquals(MeasurementType.CHEST, shown.type)
        assertEquals(90.0, shown.valueCm, 1e-9)
    }

    @Test
    fun `a value above the maximum is clamped by the repository`() = runTest(dispatcher) {
        val vm = viewModel(measurementRepository = FakeMeasurementRepository())

        vm.addMeasurement(LocalDate.of(2026, 11, 2), MeasurementType.WAIST, 1000.0)
        runCurrent()

        assertEquals(MeasurementRules.MAX_CM, vm.state.value.measurements.single().valueCm, 1e-9)
    }

    @Test
    fun `deleting a measurement removes it and undo puts it back with the same id`() = runTest(dispatcher) {
        val vm = viewModel(measurementRepository = FakeMeasurementRepository(measured))
        val original = vm.state.value.measurements.first { it.id == 1L }

        vm.deleteMeasurement(1L)
        runCurrent()
        assertEquals(listOf(2L), vm.state.value.measurements.map { it.id })

        vm.undoDeleteMeasurement(original)
        runCurrent()
        assertEquals(original, vm.state.value.measurements.first { it.id == 1L })
        assertEquals(measured, vm.state.value.measurements)
    }
}

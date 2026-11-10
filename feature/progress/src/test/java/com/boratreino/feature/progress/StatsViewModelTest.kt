package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.StatsRepository
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.ReminderSettings
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

    private val repository = FakeStatsRepository()
    private val settings = FakeSettings()
    private val body = FakeBody()

    private fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        bodySettings: BodySettings = body,
    ): StatsViewModel {
        val vm = StatsViewModel(repository, settings, bodySettings, handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
        return vm
    }

    @Test
    fun `before anything is collected the state is the empty initial one`() = runTest(dispatcher) {
        val vm = StatsViewModel(repository, settings, body, SavedStateHandle())
        val state = vm.state.value
        assertFalse(state.loaded)
        assertNull(state.report)
        assertEquals(StatsPeriod.THREE_MONTHS, state.period)
        assertEquals(4, state.weeklyGoal)
    }

    @Test
    fun `the default period is three months`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(StatsPeriod.THREE_MONTHS, vm.state.value.period)
        assertEquals(StatsPeriod.THREE_MONTHS, vm.state.value.report!!.period)
    }

    @Test
    fun `the report loads and loaded becomes true`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.loaded)
        assertEquals(StatsSamples.full.copy(period = StatsPeriod.THREE_MONTHS), vm.state.value.report)
    }

    @Test
    fun `the weekly goal from the settings is passed to the repository`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(StatsPeriod.THREE_MONTHS to 4, repository.calls.last())
        assertEquals(4, vm.state.value.weeklyGoal)
    }

    @Test
    fun `a different goal in the settings is used from the start`() = runTest(dispatcher) {
        settings.goal.value = 6
        val vm = viewModel()
        assertEquals(StatsPeriod.THREE_MONTHS to 6, repository.calls.last())
        assertEquals(6, vm.state.value.weeklyGoal)
    }

    @Test
    fun `changing the goal queries the repository again`() = runTest(dispatcher) {
        val vm = viewModel()
        settings.goal.value = 2
        runCurrent()
        assertEquals(StatsPeriod.THREE_MONTHS to 2, repository.calls.last())
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
    fun `a view model over a fresh handle does not inherit another one's period`() = runTest(dispatcher) {
        viewModel().setPeriod(StatsPeriod.ALL)
        runCurrent()
        val other = viewModel(SavedStateHandle())
        assertEquals(StatsPeriod.THREE_MONTHS, other.state.value.period)
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
}

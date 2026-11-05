package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.StatsRepository
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.settings.TrainingSettings
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

    private val repository = FakeStatsRepository()
    private val settings = FakeSettings()

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): StatsViewModel {
        val vm = StatsViewModel(repository, settings, handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
        return vm
    }

    @Test
    fun `before anything is collected the state is the empty initial one`() = runTest(dispatcher) {
        val vm = StatsViewModel(repository, settings, SavedStateHandle())
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
}

package com.boratreino.feature.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Routine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import java.time.Instant
import java.time.LocalDate

/** The planned workouts on the Calendar follow the date: a day that has become past leaves the upcoming list at midnight. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarMidnightPlanTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val tomorrow = LocalDate.of(2026, 11, 6)
    private val routineA = Routine(1L, 1L, "Treino A", "A", 0, emptySet())

    private lateinit var clock: MutableClock
    private lateinit var routines: PlanRoutines
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Thirty seconds before midnight in UTC.
        clock = MutableClock(Instant.parse("2026-11-05T23:59:30Z"))
        routines = PlanRoutines(listOf(routineA))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        viewModel = CalendarViewModel(EnergyHistory(), routines, FakeTrainingSettings(), FakeBodySettings(), clock)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private val planned: Set<LocalDate> get() = viewModel.state.value.planned.keys

    @Test
    fun `a workout planned for today is listed`() = runTest {
        routines.planWorkout(today, 1L)

        start()

        assertEquals(setOf(today), planned)
    }

    @Test
    fun `after midnight the day that has become past drops out of the planned days, and the new today stays`() = runTest {
        routines.planWorkout(today, 1L)
        routines.planWorkout(tomorrow, 1L)
        start()
        assertEquals(setOf(today, tomorrow), planned)

        clock.now = Instant.parse("2026-11-06T00:00:05Z")
        // The wake-up is due 30 seconds after the start, plus the one second margin.
        advanceTimeBy(31_001)
        runCurrent()

        assertEquals(setOf(tomorrow), planned)
    }

    @Test
    fun `a day that is still ahead stays listed after midnight`() = runTest {
        val later = LocalDate.of(2026, 11, 9)
        routines.planWorkout(later, 1L)
        start()

        clock.now = Instant.parse("2026-11-06T00:00:05Z")
        advanceTimeBy(31_001)
        runCurrent()

        assertEquals(setOf(later), planned)
    }
}

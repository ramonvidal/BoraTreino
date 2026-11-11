package com.boratreino.feature.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Routine
import com.boratreino.core.model.ScheduleMode
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** The schedule rule ("which routine is next") as the Calendar view model passes it on and shows it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarScheduleViewModelTest {
    private val clock = MutableClock(Instant.parse("2026-11-05T12:00:00Z"))

    private lateinit var routines: PlanRoutines
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        routines = PlanRoutines(listOf(Routine(1L, 1L, "Treino A", "A", 0, emptySet())))
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

    private val state: CalendarUiState get() = viewModel.state.value

    @Test
    fun `the schedule starts in turn with every day as a training day`() = runTest {
        start()

        assertEquals(ScheduleMode.IN_TURN, state.scheduleMode)
        assertEquals(emptySet<DayOfWeek>(), state.trainingDays)
    }

    @Test
    fun `switching to on weekdays hands the mode to the repository and shows it`() = runTest {
        start()

        viewModel.setScheduleMode(ScheduleMode.WEEKDAYS)
        runCurrent()

        assertEquals(listOf(ScheduleMode.WEEKDAYS), routines.scheduleModeCalls)
        assertEquals(ScheduleMode.WEEKDAYS, state.scheduleMode)
    }

    @Test
    fun `switching back to in turn is passed on too`() = runTest {
        start()
        viewModel.setScheduleMode(ScheduleMode.WEEKDAYS)
        runCurrent()

        viewModel.setScheduleMode(ScheduleMode.IN_TURN)
        runCurrent()

        assertEquals(listOf(ScheduleMode.WEEKDAYS, ScheduleMode.IN_TURN), routines.scheduleModeCalls)
        assertEquals(ScheduleMode.IN_TURN, state.scheduleMode)
    }

    @Test
    fun `the training days are passed on and shown`() = runTest {
        start()
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        viewModel.setTrainingDays(days)
        runCurrent()

        assertEquals(listOf(days), routines.trainingDayCalls)
        assertEquals(days, state.trainingDays)
    }

    @Test
    fun `clearing the training days hands an empty set to the repository`() = runTest {
        start()
        viewModel.setTrainingDays(setOf(DayOfWeek.TUESDAY))
        runCurrent()

        viewModel.setTrainingDays(emptySet())
        runCurrent()

        assertEquals(listOf(setOf(DayOfWeek.TUESDAY), emptySet<DayOfWeek>()), routines.trainingDayCalls)
        assertEquals(emptySet<DayOfWeek>(), state.trainingDays)
    }
}

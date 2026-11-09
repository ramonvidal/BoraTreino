package com.boratreino.feature.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Planning workouts from the Calendar: the chooser, several routines on one day, removing one, and which plans count. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarPlanViewModelTest {
    // Today is 5 November 2026 (UTC).
    private val clock = MutableClock(Instant.parse("2026-11-05T12:00:00Z"))
    private val today = LocalDate.of(2026, 11, 5)
    private val yesterday = LocalDate.of(2026, 11, 4)
    private val nextWeek = LocalDate.of(2026, 11, 10)

    private val routineA = Routine(1L, 1L, "Treino A", "A", 0, emptySet())
    private val routineB = Routine(2L, 1L, "Treino B", "B", 1, emptySet())

    private val plannedA = PlannedUi(1L, "Treino A", "A")
    private val plannedB = PlannedUi(2L, "Treino B", "B")

    private lateinit var routines: PlanRoutines
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        routines = PlanRoutines(listOf(routineA, routineB))
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
    fun `the chooser lists the routines by name`() = runTest {
        start()

        assertEquals(listOf(RoutineChoiceUi(1L, "Treino A"), RoutineChoiceUi(2L, "Treino B")), state.routines)
    }

    @Test
    fun `opening the chooser on a future day and picking a routine plans that day and closes it`() = runTest {
        start()
        viewModel.selectDay(nextWeek)
        viewModel.openPlanChooser()
        assertTrue(state.planChooser)

        viewModel.plan(2L)
        runCurrent()

        assertEquals(listOf(nextWeek to 2L), routines.planCalls)
        assertEquals(listOf(plannedB), state.planned[nextWeek])
        assertFalse(state.planChooser)
    }

    @Test
    fun `today can be planned`() = runTest {
        start()

        viewModel.openPlanChooser()
        viewModel.plan(1L)
        runCurrent()

        assertEquals(listOf(plannedA), state.planned[today])
    }

    @Test
    fun `dismissing the chooser plans nothing`() = runTest {
        start()
        viewModel.selectDay(nextWeek)
        viewModel.openPlanChooser()

        viewModel.dismissPlanChooser()
        runCurrent()

        assertFalse(state.planChooser)
        assertEquals(emptyList<Pair<LocalDate, Long>>(), routines.planCalls)
        assertTrue(state.planned.isEmpty())
    }

    @Test
    fun `a past day cannot be planned`() = runTest {
        start()
        viewModel.selectDay(yesterday)

        viewModel.openPlanChooser()
        assertFalse(state.planChooser)
        viewModel.plan(1L)
        runCurrent()

        assertEquals(emptyList<Pair<LocalDate, Long>>(), routines.planCalls)
        assertTrue(state.planned.isEmpty())
    }

    @Test
    fun `a second routine can be planned on a day that already has one`() = runTest {
        start()
        viewModel.selectDay(nextWeek)
        viewModel.openPlanChooser()
        viewModel.plan(1L)
        runCurrent()

        viewModel.openPlanChooser()
        viewModel.plan(2L)
        runCurrent()

        assertEquals(listOf(plannedA, plannedB), state.planned[nextWeek])
        assertEquals(setOf(nextWeek), state.planned.keys)
    }

    @Test
    fun `the routines of a day are listed by position, whatever order they were planned in`() = runTest {
        start()
        viewModel.selectDay(nextWeek)
        viewModel.openPlanChooser()
        viewModel.plan(2L)
        viewModel.openPlanChooser()
        viewModel.plan(1L)
        runCurrent()

        assertEquals(listOf(plannedA, plannedB), state.planned[nextWeek])
    }

    @Test
    fun `planning the same routine on the same day again keeps one entry`() = runTest {
        start()
        viewModel.selectDay(nextWeek)
        viewModel.openPlanChooser()
        viewModel.plan(1L)
        viewModel.openPlanChooser()
        viewModel.plan(1L)
        runCurrent()

        assertEquals(listOf(plannedA), state.planned[nextWeek])
    }

    @Test
    fun `removing one routine of a day keeps the others planned there`() = runTest {
        start()
        viewModel.selectDay(nextWeek)
        viewModel.openPlanChooser()
        viewModel.plan(1L)
        viewModel.openPlanChooser()
        viewModel.plan(2L)
        runCurrent()

        viewModel.unplan(1L)
        runCurrent()

        assertEquals(listOf(nextWeek to 1L), routines.unplanCalls)
        assertEquals(listOf(plannedB), state.planned[nextWeek])
    }

    @Test
    fun `removing the last routine of a day clears that day and keeps the other days`() = runTest {
        start()
        viewModel.selectDay(nextWeek)
        viewModel.openPlanChooser()
        viewModel.plan(1L)
        viewModel.selectDay(today)
        viewModel.openPlanChooser()
        viewModel.plan(2L)
        viewModel.selectDay(nextWeek)
        runCurrent()

        viewModel.unplan(1L)
        runCurrent()

        assertEquals(setOf(today), state.planned.keys)
        assertEquals(listOf(plannedB), state.planned[today])
    }

    @Test
    fun `a plan that appears later in the data is shown`() = runTest {
        start()

        routines.plannedFlow.value = listOf(PlannedWorkout(nextWeek, 2L))
        runCurrent()

        assertEquals(listOf(plannedB), state.planned[nextWeek])
    }

    @Test
    fun `several plans on one date that appear in the data are all shown`() = runTest {
        start()

        routines.plannedFlow.value = listOf(PlannedWorkout(nextWeek, 2L), PlannedWorkout(nextWeek, 1L))
        runCurrent()

        assertEquals(listOf(plannedA, plannedB), state.planned[nextWeek])
    }

    @Test
    fun `past planned workouts are ignored`() = runTest {
        routines.plannedFlow.value = listOf(PlannedWorkout(yesterday, 1L), PlannedWorkout(nextWeek, 2L))

        start()

        assertEquals(setOf(nextWeek), state.planned.keys)
    }

    @Test
    fun `a plan for today is kept`() = runTest {
        routines.plannedFlow.value = listOf(PlannedWorkout(today, 1L))

        start()

        assertEquals(listOf(plannedA), state.planned[today])
    }

    @Test
    fun `a planned routine that no longer exists is not shown`() = runTest {
        routines.plannedFlow.value = listOf(PlannedWorkout(nextWeek, 99L))

        start()

        assertTrue(state.planned.isEmpty())
    }
}

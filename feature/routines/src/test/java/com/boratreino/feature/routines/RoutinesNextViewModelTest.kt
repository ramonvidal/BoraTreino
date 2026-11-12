package com.boratreino.feature.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** The "Next" pill and the "Last trained" days on the Routines tab's rows, with a fixed clock. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoutinesNextViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")

    /** Wednesday 11 November 2026, 09:00 in São Paulo (12:00 UTC). */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), zone)

    private val routines = listOf(
        RoutineWithItems(fakeRoutine(1L, "Treino A", "A", 0), emptyList()),
        RoutineWithItems(fakeRoutine(2L, "Treino B", "B", 1), emptyList()),
        RoutineWithItems(fakeRoutine(3L, "Treino C", "C", 2), emptyList()),
    )

    private fun TestScope.viewModel(repo: FakeRoutineRepository): RoutinesViewModel {
        val vm = RoutinesViewModel(FakeMachineRepository(), repo, FakeExerciseLibrary(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        runCurrent()
        return vm
    }

    // Next pill

    @Test
    fun `the routine after the last trained one in turn is the only one flagged Next`() = runTest(dispatcher) {
        val vm = viewModel(FakeRoutineRepository(routines, initialLastRoutineId = 1L))

        val rows = vm.uiState.value.routines
        assertEquals(listOf(false, true, false), rows.map { it.isNext })
    }

    @Test
    fun `with no finished session yet the first routine is flagged Next`() = runTest(dispatcher) {
        val vm = viewModel(FakeRoutineRepository(routines, initialLastRoutineId = null))

        assertEquals(listOf(true, false, false), vm.uiState.value.routines.map { it.isNext })
    }

    @Test
    fun `the last routine wraps around to the first one`() = runTest(dispatcher) {
        val vm = viewModel(FakeRoutineRepository(routines, initialLastRoutineId = 3L))

        assertEquals(listOf(true, false, false), vm.uiState.value.routines.map { it.isNext })
    }

    @Test
    fun `on a rest day in turn no routine is flagged Next`() = runTest(dispatcher) {
        val plan = RoutinePlan(id = 1L, scheduleMode = ScheduleMode.IN_TURN, trainingDays = setOf(DayOfWeek.MONDAY))
        val vm = viewModel(FakeRoutineRepository(routines, initialPlan = plan, initialLastRoutineId = 1L))

        assertEquals(listOf(false, false, false), vm.uiState.value.routines.map { it.isNext })
    }

    @Test
    fun `a routine on specific days is flagged Next on its day`() = runTest(dispatcher) {
        val weekly = listOf(
            RoutineWithItems(fakeRoutine(1L, "Treino A", "A", 0).copy(weekdays = setOf(DayOfWeek.MONDAY)), emptyList()),
            RoutineWithItems(fakeRoutine(2L, "Treino B", "B", 1).copy(weekdays = setOf(DayOfWeek.WEDNESDAY)), emptyList()),
        )
        val vm = viewModel(FakeRoutineRepository(weekly))

        assertEquals(listOf(false, true), vm.uiState.value.routines.map { it.isNext })
    }

    @Test
    fun `a routine on specific days beats the rotation on its day`() = runTest(dispatcher) {
        val plan = RoutinePlan(id = 1L, scheduleMode = ScheduleMode.IN_TURN, trainingDays = setOf(DayOfWeek.WEDNESDAY))
        val mixed = listOf(
            RoutineWithItems(fakeRoutine(1L, "Treino A", "A", 0), emptyList()),
            RoutineWithItems(fakeRoutine(2L, "Treino B", "B", 1).copy(weekdays = setOf(DayOfWeek.WEDNESDAY)), emptyList()),
        )
        val vm = viewModel(FakeRoutineRepository(mixed, initialPlan = plan, initialLastRoutineId = null))

        assertEquals(listOf(false, true), vm.uiState.value.routines.map { it.isNext })
    }

    @Test
    fun `the rotation moves on past a routine on specific days`() = runTest(dispatcher) {
        val plan = RoutinePlan(id = 1L, scheduleMode = ScheduleMode.IN_TURN, trainingDays = setOf(DayOfWeek.WEDNESDAY))
        val mixed = listOf(
            RoutineWithItems(fakeRoutine(1L, "Treino A", "A", 0), emptyList()),
            RoutineWithItems(fakeRoutine(2L, "Treino B", "B", 1).copy(weekdays = setOf(DayOfWeek.MONDAY)), emptyList()),
            RoutineWithItems(fakeRoutine(3L, "Treino C", "C", 2), emptyList()),
        )
        val vm = viewModel(FakeRoutineRepository(mixed, initialPlan = plan, initialLastRoutineId = 1L))

        assertEquals(listOf(false, false, true), vm.uiState.value.routines.map { it.isNext })
    }

    // Last trained

    @Test
    fun `a routine trained earlier today shows zero days`() = runTest(dispatcher) {
        // 09:00 local today.
        val trained = mapOf(1L to Instant.parse("2026-11-11T12:00:00Z"))
        val vm = viewModel(FakeRoutineRepository(routines, initialLastTrained = trained))

        assertEquals(0, vm.uiState.value.routines[0].lastTrainedDaysAgo)
    }

    @Test
    fun `days are counted in the calendar days of the clock zone, not in UTC`() = runTest(dispatcher) {
        // 02:30 UTC on the 11th is 23:30 on the 10th in São Paulo: yesterday, not today.
        val trained = mapOf(2L to Instant.parse("2026-11-11T02:30:00Z"))
        val vm = viewModel(FakeRoutineRepository(routines, initialLastTrained = trained))

        assertEquals(1, vm.uiState.value.routines[1].lastTrainedDaysAgo)
    }

    @Test
    fun `a routine trained three calendar days ago shows three`() = runTest(dispatcher) {
        // 12:00 local on the 8th.
        val trained = mapOf(3L to Instant.parse("2026-11-08T15:00:00Z"))
        val vm = viewModel(FakeRoutineRepository(routines, initialLastTrained = trained))

        assertEquals(3, vm.uiState.value.routines[2].lastTrainedDaysAgo)
    }

    @Test
    fun `a routine never trained has no last trained days`() = runTest(dispatcher) {
        val trained = mapOf(1L to Instant.parse("2026-11-10T14:00:00Z"))
        val vm = viewModel(FakeRoutineRepository(routines, initialLastTrained = trained))

        val rows = vm.uiState.value.routines
        assertEquals(1, rows[0].lastTrainedDaysAgo)
        assertNull(rows[1].lastTrainedDaysAgo)
        assertNull(rows[2].lastTrainedDaysAgo)
    }

    @Test
    fun `the last trained days follow the repository when it changes`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(routines)
        val vm = viewModel(repo)
        assertNull(vm.uiState.value.routines[0].lastTrainedDaysAgo)

        repo.lastTrained.value = mapOf(1L to Instant.parse("2026-11-10T14:00:00Z"))
        runCurrent()

        assertEquals(1, vm.uiState.value.routines[0].lastTrainedDaysAgo)
    }
}

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/** The training days of the In turn schedule, as the Routines view model passes them on and shows them. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoutinesTrainingDaysViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val stored = listOf(
        RoutineWithItems(fakeRoutine(1L, "Legs", "L", 0), emptyList()),
        RoutineWithItems(fakeRoutine(2L, "Push", "P", 1), emptyList()),
    )

    private fun TestScope.viewModel(repo: FakeRoutineRepository): RoutinesViewModel {
        val vm = RoutinesViewModel(FakeMachineRepository(emptyList()), repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        runCurrent()
        return vm
    }

    @Test
    fun `setting the training days hands them to the repository`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)

        vm.setTrainingDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        runCurrent()

        assertEquals(listOf(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), repo.trainingDayCalls)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), vm.uiState.value.trainingDays)
    }

    @Test
    fun `the training days of the stored plan are shown`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored, RoutinePlan(1L, ScheduleMode.IN_TURN, setOf(DayOfWeek.SATURDAY)))
        val vm = viewModel(repo)

        assertEquals(ScheduleMode.IN_TURN, vm.uiState.value.scheduleMode)
        assertEquals(setOf(DayOfWeek.SATURDAY), vm.uiState.value.trainingDays)
    }

    @Test
    fun `clearing the training days hands an empty set to the repository`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored, RoutinePlan(1L, ScheduleMode.IN_TURN, setOf(DayOfWeek.TUESDAY)))
        val vm = viewModel(repo)

        vm.setTrainingDays(emptySet())
        runCurrent()

        assertEquals(listOf(emptySet<DayOfWeek>()), repo.trainingDayCalls)
        assertEquals(emptySet<DayOfWeek>(), vm.uiState.value.trainingDays)
    }

    @Test
    fun `each change is passed on, in order, and the last one is shown`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)

        vm.setTrainingDays(setOf(DayOfWeek.MONDAY))
        runCurrent()
        vm.setTrainingDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        runCurrent()

        assertEquals(
            listOf(setOf(DayOfWeek.MONDAY), setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            repo.trainingDayCalls,
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), vm.uiState.value.trainingDays)
    }
}

package com.boratreino.feature.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.RoutineWithItems
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Routines tab's view model: loaded flag, rows, create / duplicate / delete / move. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoutinesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val legPress = fakeMachine(1L, "Leg press")
    private val latPulldown = fakeMachine(2L, "Lat pulldown")

    private val stored = listOf(
        RoutineWithItems(fakeRoutine(1L, "Legs", "L", 0), listOf(fakeItem(1L, 1L, 0), fakeItem(1L, 2L, 1))),
        RoutineWithItems(fakeRoutine(2L, "Push", "P", 1), emptyList()),
    )

    private fun TestScope.viewModel(repo: FakeRoutineRepository, subscribe: Boolean = true): RoutinesViewModel {
        val vm = RoutinesViewModel(FakeMachineRepository(listOf(legPress, latPulldown)), repo)
        if (subscribe) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
            runCurrent()
        }
        return vm
    }

    @Test
    fun `not loaded until the database has answered`() = runTest(dispatcher) {
        val vm = viewModel(FakeRoutineRepository(stored), subscribe = false)

        assertFalse(vm.uiState.value.loaded)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        runCurrent()

        assertTrue(vm.uiState.value.loaded)
    }

    @Test
    fun `rows carry the machine count and the machine names in routine order`() = runTest(dispatcher) {
        val vm = viewModel(FakeRoutineRepository(stored))

        val rows = vm.uiState.value.routines
        assertEquals(listOf("Legs", "Push"), rows.map { it.routine.name })
        assertEquals(2, rows[0].machineCount)
        assertEquals(listOf("Leg press", "Lat pulldown"), rows[0].machineNames)
        assertEquals(0, rows[1].machineCount)
        assertEquals(emptyList<String>(), rows[1].machineNames)
        assertEquals(2, vm.uiState.value.gymCount)
    }

    @Test
    fun `createRoutine creates first and then hands the new id to the callback`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)
        var opened: Long? = null

        vm.createRoutine { id ->
            // The routine already exists when the screen is told to open it.
            assertEquals(listOf("create"), repo.events)
            opened = id
        }
        runCurrent()

        assertEquals(1, repo.created.size)
        assertEquals(repo.created.single(), opened)
    }

    @Test
    fun `createRoutine does not call back before the repository answers`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)
        var opened: Long? = null

        vm.createRoutine { opened = it }

        assertNull(opened)
        runCurrent()
        assertNotNull(opened)
    }

    @Test
    fun `duplicateRoutine asks the repository to duplicate that routine`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)

        vm.duplicateRoutine(2L)
        runCurrent()

        assertEquals(listOf(2L), repo.duplicated)
    }

    @Test
    fun `requesting a delete shows the question and deletes nothing yet`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)

        vm.requestDeleteRoutine(1L)
        runCurrent()

        assertEquals(1L, vm.uiState.value.routineDelete?.id)
        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun `confirming the delete removes the routine and closes the question`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)
        vm.requestDeleteRoutine(1L)
        runCurrent()

        vm.confirmDeleteRoutine()
        runCurrent()

        assertEquals(listOf(1L), repo.deleted)
        assertNull(vm.uiState.value.routineDelete)
        assertEquals(listOf("Push"), vm.uiState.value.routines.map { it.routine.name })
    }

    @Test
    fun `dismissing the delete closes the question and keeps the routine`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)
        vm.requestDeleteRoutine(1L)
        runCurrent()

        vm.dismissDeleteRoutine()
        runCurrent()

        assertNull(vm.uiState.value.routineDelete)
        assertTrue(repo.deleted.isEmpty())
        assertEquals(2, vm.uiState.value.routines.size)
    }

    @Test
    fun `confirming with no question open deletes nothing`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)

        vm.confirmDeleteRoutine()
        runCurrent()

        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun `requesting the delete of an unknown routine opens no question`() = runTest(dispatcher) {
        val vm = viewModel(FakeRoutineRepository(stored))

        vm.requestDeleteRoutine(77L)
        runCurrent()

        assertNull(vm.uiState.value.routineDelete)
    }

    @Test
    fun `moveRoutine passes the id and direction to the repository`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(stored)
        val vm = viewModel(repo)

        vm.moveRoutine(2L, up = true)
        vm.moveRoutine(1L, up = false)
        runCurrent()

        assertEquals(listOf(2L to true, 1L to false), repo.moved)
    }
}

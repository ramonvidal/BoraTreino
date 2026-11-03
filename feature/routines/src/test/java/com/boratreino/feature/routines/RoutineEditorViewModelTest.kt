package com.boratreino.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

/**
 * The routine editor's view model: every change is saved at once (debounced ~400 ms after the
 * last change), back saves immediately, and a blank name or short name never reaches the database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoutineEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val legPress = fakeMachine(1L, "Leg press")
    private val legExtension = fakeMachine(2L, "Leg extension")
    private val latPulldown = fakeMachine(3L, "Lat pulldown", MuscleGroup.BACK)
    private val seatedRow = fakeMachine(4L, "Seated row", MuscleGroup.BACK)
    private val treadmill = fakeMachine(7L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)
    private val gym = listOf(legPress, legExtension, latPulldown, seatedRow, treadmill)

    /** Routine 5 "Legs" with three machines. */
    private fun storedRoutine() = RoutineWithItems(
        fakeRoutine(5L, "Legs", "L", 2),
        listOf(
            fakeItem(5L, 1L, 0, sets = 3, min = 8, max = 12),
            fakeItem(5L, 2L, 1, sets = 4, min = 10, max = 15),
            fakeItem(5L, 3L, 2, sets = 2, min = 6, max = 8),
        ),
    )

    private fun TestScope.editor(
        repo: FakeRoutineRepository = FakeRoutineRepository(listOf(storedRoutine())),
        handle: SavedStateHandle = SavedStateHandle(mapOf(ROUTINE_ID_ARG to 5L)),
    ): RoutineEditorViewModel {
        val vm = RoutineEditorViewModel(repo, FakeMachineRepository(gym), handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        runCurrent()
        return vm
    }

    /** Lets the debounce (400 ms) run out and the save happen. */
    private fun TestScope.settle() {
        advanceTimeBy(401)
        runCurrent()
    }

    @Test
    fun `opening an existing routine loads it without writing anything`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)
        settle()

        val s = vm.uiState.value
        assertFalse(s.loading)
        assertEquals(5L, s.routineId)
        assertEquals("Legs", s.name)
        assertEquals("L", s.shortName)
        assertEquals(listOf("Leg press", "Leg extension", "Lat pulldown"), s.items.map { it.machineName })
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `machines already in the routine are not offered again`() = runTest(dispatcher) {
        val vm = editor()

        val offered = vm.uiState.value.available.flatMap { it.machines }.map { it.name }
        assertEquals(setOf("Seated row", "Treadmill"), offered.toSet())
    }

    @Test
    fun `a change is not saved before the debounce runs out`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setName("Leg day")
        advanceTimeBy(300)
        runCurrent()

        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `a name change is saved about 400 ms later`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setName("Leg day")
        settle()

        assertEquals(1, repo.saved.size)
        val (routine, items) = repo.saved.single()
        assertEquals("Leg day", routine.name)
        assertEquals(5L, routine.id)
        assertEquals(2, routine.position)
        assertEquals(3, items.size)
    }

    @Test
    fun `several quick changes become one save with the last value`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setName("L")
        advanceTimeBy(200)
        runCurrent()
        vm.setName("Le")
        advanceTimeBy(200)
        runCurrent()
        vm.setName("Leg day")
        advanceTimeBy(200)
        runCurrent()
        assertTrue(repo.saved.isEmpty())

        settle()

        assertEquals(1, repo.saved.size)
        assertEquals("Leg day", repo.saved.single().first.name)
    }

    @Test
    fun `back saves at once without waiting for the debounce`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setName("Leg day")
        vm.back()
        runCurrent()

        assertEquals(1, repo.saved.size)
        assertEquals("Leg day", repo.saved.single().first.name)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `back after the debounce already saved does not save twice`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)
        vm.setName("Leg day")
        settle()

        vm.back()
        settle()

        assertEquals(1, repo.saved.size)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `back with nothing changed writes nothing and leaves`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.back()
        settle()

        assertTrue(repo.saved.isEmpty())
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `a blank name is never written`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setName("")
        settle()
        vm.setName("   ")
        settle()
        vm.back()
        runCurrent()

        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `a blank name keeps the last non-blank one when something else changes`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)
        vm.setName("Leg day")
        settle()

        vm.setName("")
        vm.stepSets(0, up = true)
        settle()

        assertEquals(2, repo.saved.size)
        assertEquals("Leg day", repo.saved.last().first.name)
        assertEquals(4, repo.saved.last().second[0].targetSets)
    }

    @Test
    fun `a blank short name is never written and keeps the last one`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setShortName("")
        settle()
        assertTrue(repo.saved.isEmpty())

        vm.setShortName("LD")
        settle()
        vm.setShortName("")
        vm.setName("Leg day")
        settle()

        assertEquals("LD", repo.saved.first().first.shortName)
        assertEquals("LD", repo.saved.last().first.shortName)
        assertEquals("Leg day", repo.saved.last().first.name)
    }

    @Test
    fun `the short name is at most three characters`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setShortName("ABCDE")
        settle()

        assertEquals("ABC", repo.saved.single().first.shortName)
    }

    @Test
    fun `opened without an id it creates a routine and edits that one`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository()
        val handle = SavedStateHandle(mapOf(ROUTINE_ID_ARG to NEW_ROUTINE))
        val vm = editor(repo, handle)

        assertEquals(1, repo.created.size)
        val id = repo.created.single()
        assertEquals(id, vm.uiState.value.routineId)
        assertEquals("Routine A", vm.uiState.value.name)
        assertFalse(vm.uiState.value.loading)
        assertEquals(id, handle.get<Long>(ROUTINE_ID_ARG))

        vm.setName("Pull")
        settle()

        assertEquals(id, repo.saved.single().first.id)
        assertEquals("Pull", repo.saved.single().first.name)
        // Still just the one routine: the edit saved over it.
        assertEquals(1, repo.store.value.size)
    }

    @Test
    fun `opened with no argument at all also creates a routine`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository()
        editor(repo, SavedStateHandle())

        assertEquals(1, repo.created.size)
    }

    @Test
    fun `opening a routine that is gone leaves at once`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo, SavedStateHandle(mapOf(ROUTINE_ID_ARG to 999L)))

        assertTrue(vm.uiState.value.done)
        assertFalse(vm.uiState.value.loading)
        assertTrue(repo.created.isEmpty())
    }

    @Test
    fun `stepping sets saves the new target`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.stepSets(0, up = true)
        settle()

        assertEquals(4, repo.saved.single().second[0].targetSets)
        assertEquals(4, vm.uiState.value.items[0].item.targetSets)
    }

    @Test
    fun `stepping reps from and to saves the new range`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.stepRepsMin(0, up = true)
        vm.stepRepsMax(0, up = false)
        settle()

        val saved = repo.saved.single().second[0]
        assertEquals(9, saved.repsMin)
        assertEquals(11, saved.repsMax)
    }

    @Test
    fun `reps from never passes reps to`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        repeat(10) { vm.stepRepsMin(2, up = true) }
        settle()

        val saved = repo.saved.single().second[2]
        assertEquals(8, saved.repsMin)
        assertEquals(8, saved.repsMax)
    }

    @Test
    fun `moving a machine down saves the new order with renumbered positions`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.moveItem(0, up = false)
        settle()

        val items = repo.saved.single().second
        assertEquals(listOf(2L, 1L, 3L), items.map { it.machineId })
        assertEquals(listOf(0, 1, 2), items.map { it.position })
    }

    @Test
    fun `moving the first machine up changes nothing and saves nothing`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.moveItem(0, up = true)
        vm.moveItem(2, up = false)
        settle()

        assertTrue(repo.saved.isEmpty())
        assertEquals(listOf(1L, 2L, 3L), vm.uiState.value.items.map { it.item.machineId })
    }

    @Test
    fun `removing a machine saves the shorter list`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.removeItem(1)
        settle()

        val items = repo.saved.single().second
        assertEquals(listOf(1L, 3L), items.map { it.machineId })
        assertEquals(listOf(0, 1), items.map { it.position })
    }

    @Test
    fun `removing the last machine saves an empty routine`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(RoutineWithItems(fakeRoutine(5L, "Legs", "L"), listOf(fakeItem(5L, 1L, 0)))))
        val vm = editor(repo)

        vm.removeItem(0)
        settle()

        assertTrue(repo.saved.single().second.isEmpty())
    }

    @Test
    fun `adding a machine saves it last with the default target`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.addMachine(seatedRow)
        settle()

        val items = repo.saved.single().second
        assertEquals(4, items.size)
        val added = items.last()
        assertEquals(4L, added.machineId)
        assertEquals(3, added.position)
        assertEquals(3, added.targetSets)
        assertEquals(8, added.repsMin)
        assertEquals(12, added.repsMax)
        assertFalse(vm.uiState.value.available.flatMap { it.machines }.any { it.id == 4L })
    }

    @Test
    fun `a cardio machine is marked as cardio in its row`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.addMachine(treadmill)
        runCurrent()

        val row = vm.uiState.value.items.last()
        assertEquals(MachineKind.CARDIO, row.kind)
        assertEquals("Treadmill", row.machineName)
        assertEquals(MachineKind.WEIGHT_REPS, vm.uiState.value.items.first().kind)
    }

    @Test
    fun `opening and closing the sets and reps sheet saves nothing`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.openTarget(2L)
        runCurrent()
        assertEquals(2L, vm.uiState.value.targetMachineId)
        vm.closeTarget()
        settle()

        assertEquals(null, vm.uiState.value.targetMachineId)
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `duplicate saves pending changes, duplicates and leaves`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setName("Leg day")
        vm.duplicate()
        runCurrent()

        assertEquals(listOf("save", "duplicate"), repo.events)
        assertEquals(listOf(5L), repo.duplicated)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `delete asks first and deletes only after the confirmation`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.requestDelete()
        runCurrent()
        assertTrue(vm.uiState.value.confirmDelete)
        assertTrue(repo.deleted.isEmpty())

        vm.dismissDelete()
        runCurrent()
        assertFalse(vm.uiState.value.confirmDelete)
        assertTrue(repo.deleted.isEmpty())

        vm.requestDelete()
        vm.confirmDelete()
        runCurrent()

        assertEquals(listOf(5L), repo.deleted)
        assertTrue(vm.uiState.value.done)
        assertFalse(vm.uiState.value.confirmDelete)
    }

    @Test
    fun `a change waiting in the debounce is not saved after the routine is deleted`() = runTest(dispatcher) {
        val repo = FakeRoutineRepository(listOf(storedRoutine()))
        val vm = editor(repo)

        vm.setName("Leg day")
        vm.confirmDelete()
        runCurrent()
        settle()

        assertEquals(listOf(5L), repo.deleted)
        assertTrue(repo.saved.isEmpty())
    }
}

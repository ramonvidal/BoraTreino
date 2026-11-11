package com.boratreino.feature.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.Sex
import com.boratreino.core.model.StarterMachines
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/** The setup's steps as the owner walks them: what each Next writes, and what Finish and the guards do. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val machines = FakeSetupMachines()
    private val routines = FakeSetupRoutines()
    private val defaultName = "Treino A"

    private fun TestScope.viewModel(body: FakeBodySettings = FakeBodySettings()): SetupViewModel {
        val vm = SetupViewModel(machines, routines, body)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        runCurrent()
        return vm
    }

    /** Confirms the current step with Next and lets the write finish. */
    private fun TestScope.confirm(vm: SetupViewModel, onDone: () -> Unit = {}) {
        vm.next(defaultName, onDone)
        runCurrent()
    }

    private fun starter(name: String) = StarterMachines.all.first { it.name == name }

    // Walking the steps (step is 0-based: 0 gym, 1 routine, 2 days)

    @Test
    fun `the setup starts on the first step with nothing ticked`() = runTest(dispatcher) {
        val vm = viewModel()

        val state = vm.uiState.value
        assertEquals(0, state.step)
        assertTrue(state.selected.isEmpty())
        assertEquals("", state.query)
        assertFalse(state.busy)
    }

    @Test
    fun `Next on the first step adds the ticked starters once, in one call`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        vm.toggleStarter(starter("Chest press"))

        confirm(vm)

        assertEquals(1, machines.addCalls.size)
        assertEquals(setOf("Leg press", "Chest press"), machines.addCalls.single().toSet())
    }

    @Test
    fun `Next on the first step moves on with the added exercises ticked`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))

        confirm(vm)

        val state = vm.uiState.value
        assertEquals(1, state.step)
        assertEquals(listOf("Leg press"), state.chosen.map { it.name })
        assertEquals(machines.idOf("Leg press"), state.chosen.single().machineId)
        assertTrue(state.unticked.isEmpty())
    }

    @Test
    fun `Next with nothing ticked adds nothing and still moves on`() = runTest(dispatcher) {
        val vm = viewModel()

        confirm(vm)

        assertTrue(machines.addCalls.isEmpty())
        assertEquals(1, vm.uiState.value.step)
        assertTrue(vm.uiState.value.chosen.isEmpty())
    }

    @Test
    fun `Back goes one step up and never below the first step`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        confirm(vm)
        confirm(vm)
        assertEquals(2, vm.uiState.value.step)

        vm.back()
        runCurrent()
        assertEquals(1, vm.uiState.value.step)

        vm.back()
        vm.back()
        runCurrent()
        assertEquals(0, vm.uiState.value.step)
    }

    // The routine (step 2)

    @Test
    fun `Next on the routine step creates the routine with the ticked exercises under the given name`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        vm.toggleStarter(starter("Chest press"))
        confirm(vm)
        vm.setRoutineName("Treino Costas")

        confirm(vm)

        assertEquals(1, routines.createCount)
        assertEquals(1, routines.saved.size)
        val (routine, items) = routines.saved.single()
        assertEquals("Treino Costas", routine.name)
        assertEquals(setOf(machines.idOf("Leg press"), machines.idOf("Chest press")), items.map { it.machineId }.toSet())
        assertEquals(2, vm.uiState.value.step)
    }

    @Test
    fun `the routine is created once even when going back and forth, and updated in place`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        vm.toggleStarter(starter("Chest press"))
        confirm(vm)
        vm.setRoutineName("Treino Costas")
        confirm(vm)
        val createdId = routines.current().single().routine.id

        vm.back()
        runCurrent()
        vm.setRoutineName("Treino Peito")
        confirm(vm)

        assertEquals(1, routines.createCount)
        assertEquals(1, routines.current().size)
        assertEquals(createdId, routines.current().single().routine.id)
        assertEquals(2, routines.saved.size)
        assertTrue(routines.saved.all { it.first.id == createdId })
        assertEquals("Treino Peito", routines.current().single().routine.name)
    }

    @Test
    fun `a blank routine name falls back to the default name`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        confirm(vm)
        vm.setRoutineName("   ")

        confirm(vm)

        assertEquals(defaultName, routines.saved.single().first.name)
    }

    @Test
    fun `an unticked exercise is left out of the routine`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        vm.toggleStarter(starter("Chest press"))
        confirm(vm)
        vm.toggleExercise(machines.idOf("Chest press")!!)

        confirm(vm)

        val items = routines.saved.single().second
        assertEquals(listOf(machines.idOf("Leg press")), items.map { it.machineId })
    }

    @Test
    fun `with every exercise unticked on the routine step no routine is created`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        confirm(vm)
        vm.toggleExercise(machines.idOf("Leg press")!!)

        confirm(vm)

        assertEquals(0, routines.createCount)
        assertTrue(routines.saved.isEmpty())
        assertEquals(2, vm.uiState.value.step)
    }

    @Test
    fun `with nothing picked on the first step no routine is created and the step still moves on`() = runTest(dispatcher) {
        val vm = viewModel()
        confirm(vm)

        confirm(vm)

        assertEquals(0, routines.createCount)
        assertTrue(routines.saved.isEmpty())
        assertEquals(2, vm.uiState.value.step)
    }

    // The days (step 3)

    @Test
    fun `Finish writes the training days, leaves the plan in turn and calls onDone once`() = runTest(dispatcher) {
        val vm = viewModel()
        confirm(vm)
        confirm(vm)
        vm.toggleDay(DayOfWeek.MONDAY)
        vm.toggleDay(DayOfWeek.WEDNESDAY)
        var done = 0

        confirm(vm) { done++ }

        assertEquals(listOf(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), routines.trainingDayWrites)
        assertTrue(routines.scheduleModeWrites.isEmpty())
        assertEquals(1, done)
    }

    @Test
    fun `toggling a day twice takes it off again`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleDay(DayOfWeek.FRIDAY)
        vm.toggleDay(DayOfWeek.FRIDAY)

        assertTrue(vm.uiState.value.days.isEmpty())
    }

    @Test
    fun `Finish with no days picked writes an empty set, which means every day`() = runTest(dispatcher) {
        val vm = viewModel()
        confirm(vm)
        confirm(vm)
        var done = 0

        confirm(vm) { done++ }

        assertEquals(listOf(emptySet<DayOfWeek>()), routines.trainingDayWrites)
        assertEquals(1, done)
    }

    // Guards

    @Test
    fun `a second Next while the first write is running is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        val gate = CompletableDeferred<Unit>()
        machines.gate = gate

        vm.next(defaultName) {}
        vm.next(defaultName) {}
        runCurrent()

        assertEquals(1, machines.addCalls.size)
        assertTrue(vm.uiState.value.busy)
        assertEquals(0, vm.uiState.value.step)

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, machines.addCalls.size)
        assertEquals(1, vm.uiState.value.step)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `a double tap on Finish calls onDone and writes the days once`() = runTest(dispatcher) {
        val vm = viewModel()
        confirm(vm)
        confirm(vm)
        vm.toggleDay(DayOfWeek.SATURDAY)
        var done = 0

        vm.next(defaultName) { done++ }
        vm.next(defaultName) { done++ }
        runCurrent()

        assertEquals(1, routines.trainingDayWrites.size)
        assertEquals(1, done)
    }

    @Test
    fun `Back does nothing while a write is running`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleStarter(starter("Leg press"))
        confirm(vm)
        assertEquals(1, vm.uiState.value.step)
        val gate = CompletableDeferred<Unit>()
        routines.gate = gate
        vm.next(defaultName) {}
        runCurrent()
        assertTrue(vm.uiState.value.busy)

        vm.back()
        runCurrent()
        assertEquals(1, vm.uiState.value.step)

        gate.complete(Unit)
        runCurrent()
        assertEquals(2, vm.uiState.value.step)
        assertFalse(vm.uiState.value.busy)
    }

    // Other state

    @Test
    fun `the search text is kept and narrows the starters`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setQuery("Leg")
        runCurrent()

        val state = vm.uiState.value
        assertEquals("Leg", state.query)
        val names = state.groups.flatMap { group -> group.starters.map { it.name } }
        assertTrue(names.contains("Leg press"))
        assertFalse(names.contains("Chest press"))
    }

    @Test
    fun `the mascot follows the sex in the body profile`() = runTest(dispatcher) {
        val body = FakeBodySettings(BodyProfile(sex = Sex.FEMALE, birthYear = 1990, heightCm = 165, weightKg = 60.0))
        val vm = viewModel(body)

        assertEquals(Sex.FEMALE, vm.uiState.value.mascotSex)
    }

    @Test
    fun `the mascot is the default man without a profile`() = runTest(dispatcher) {
        val vm = viewModel()

        assertNull(vm.uiState.value.mascotSex)
    }
}

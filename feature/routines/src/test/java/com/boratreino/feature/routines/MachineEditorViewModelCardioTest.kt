package com.boratreino.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The machine editor's kind and muscle group: Cardio is both a kind and a group and the two always agree. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineEditorViewModelCardioTest {
    private class FakeMachines(private val stored: Machine? = null) : MachineRepository {
    override suspend fun addFromLibrary(exercise: com.boratreino.core.model.LibraryExercise, portuguese: Boolean): Long = error("not used by this test")
    override fun observeLibraryIdsInUse(): kotlinx.coroutines.flow.Flow<Set<String>> = kotlinx.coroutines.flow.flowOf(emptySet())

        val saved = mutableListOf<Machine>()

        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(stored?.takeIf { it.id == id })
        override suspend fun save(machine: Machine): Long {
            saved += machine
            return if (machine.id == 0L) 99L else machine.id
        }

        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
        override suspend fun usage(machineId: Long): MachineUsage = error("not used")
        override suspend fun delete(machineId: Long): Boolean = false
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newMachine(repo: FakeMachines = FakeMachines()) =
        MachineEditorViewModel(repo, FakeExerciseLibrary(), SavedStateHandle(mapOf(MACHINE_ID_ARG to NEW_MACHINE)))

    private fun existing(machine: Machine, repo: FakeMachines = FakeMachines(machine)) =
        MachineEditorViewModel(repo, FakeExerciseLibrary(), SavedStateHandle(mapOf(MACHINE_ID_ARG to machine.id)))

    @Test
    fun `a new machine starts as legs with weights`() {
        val s = newMachine().uiState.value

        assertEquals(MuscleGroup.LEGS, s.muscleGroup)
        assertEquals(MachineKind.WEIGHT_REPS, s.kind)
    }

    @Test
    fun `choosing the Cardio kind sets the group to Cardio`() {
        val vm = newMachine()

        vm.setKind(MachineKind.CARDIO)

        assertEquals(MachineKind.CARDIO, vm.uiState.value.kind)
        assertEquals(MuscleGroup.CARDIO, vm.uiState.value.muscleGroup)
    }

    @Test
    fun `switching from Cardio back to weights resets the group to Legs`() {
        val vm = newMachine()
        vm.setMuscleGroup(MuscleGroup.ARMS)
        vm.setKind(MachineKind.CARDIO)

        vm.setKind(MachineKind.WEIGHT_REPS)

        assertEquals(MachineKind.WEIGHT_REPS, vm.uiState.value.kind)
        assertEquals(MuscleGroup.LEGS, vm.uiState.value.muscleGroup)
    }

    @Test
    fun `switching from Cardio to bodyweight resets the group to Legs`() {
        val vm = newMachine()
        vm.setKind(MachineKind.CARDIO)

        vm.setKind(MachineKind.BODYWEIGHT_REPS)

        assertEquals(MachineKind.BODYWEIGHT_REPS, vm.uiState.value.kind)
        assertEquals(MuscleGroup.LEGS, vm.uiState.value.muscleGroup)
    }

    @Test
    fun `switching between weights and bodyweight keeps the group`() {
        val vm = newMachine()
        vm.setMuscleGroup(MuscleGroup.BACK)

        vm.setKind(MachineKind.BODYWEIGHT_REPS)
        assertEquals(MuscleGroup.BACK, vm.uiState.value.muscleGroup)

        vm.setKind(MachineKind.WEIGHT_REPS)
        assertEquals(MuscleGroup.BACK, vm.uiState.value.muscleGroup)
    }

    @Test
    fun `choosing the Cardio group makes the machine a cardio one`() {
        val vm = newMachine()

        vm.setMuscleGroup(MuscleGroup.CARDIO)

        assertEquals(MachineKind.CARDIO, vm.uiState.value.kind)
        assertEquals(MuscleGroup.CARDIO, vm.uiState.value.muscleGroup)
    }

    @Test
    fun `leaving the Cardio group makes the machine a weights one`() {
        val vm = newMachine()
        vm.setKind(MachineKind.CARDIO)

        vm.setMuscleGroup(MuscleGroup.CHEST)

        assertEquals(MuscleGroup.CHEST, vm.uiState.value.muscleGroup)
        assertEquals(MachineKind.WEIGHT_REPS, vm.uiState.value.kind)
    }

    @Test
    fun `choosing another group keeps a bodyweight kind`() {
        val vm = newMachine()
        vm.setKind(MachineKind.BODYWEIGHT_REPS)

        vm.setMuscleGroup(MuscleGroup.CORE)

        assertEquals(MachineKind.BODYWEIGHT_REPS, vm.uiState.value.kind)
    }

    @Test
    fun `kind is Cardio exactly when the group is Cardio after any three changes`() {
        val changes: List<(MachineEditorViewModel) -> Unit> = buildList {
            MachineKind.entries.forEach { kind -> add { vm -> vm.setKind(kind) } }
            MuscleGroup.entries.forEach { group -> add { vm -> vm.setMuscleGroup(group) } }
        }
        for (a in changes) for (b in changes) for (c in changes) {
            val vm = newMachine()
            a(vm)
            b(vm)
            c(vm)
            val s = vm.uiState.value
            assertEquals(s.kind == MachineKind.CARDIO, s.muscleGroup == MuscleGroup.CARDIO)
        }
    }

    @Test
    fun `an existing cardio machine opens as Cardio and can be turned into a weights machine`() {
        val treadmill = Machine(7L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)
        val vm = existing(treadmill)

        assertFalse(vm.uiState.value.loading)
        assertEquals(MachineKind.CARDIO, vm.uiState.value.kind)
        assertEquals(MuscleGroup.CARDIO, vm.uiState.value.muscleGroup)

        vm.setKind(MachineKind.WEIGHT_REPS)

        assertEquals(MuscleGroup.LEGS, vm.uiState.value.muscleGroup)
    }

    @Test
    fun `saving a cardio machine stores kind and group Cardio`() {
        val repo = FakeMachines()
        val vm = newMachine(repo)
        vm.setName("  Rowing machine ")
        vm.setKind(MachineKind.CARDIO)

        vm.save()

        val machine = repo.saved.single()
        assertEquals("Rowing machine", machine.name)
        assertEquals(MachineKind.CARDIO, machine.kind)
        assertEquals(MuscleGroup.CARDIO, machine.muscleGroup)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `saving with a blank name stores nothing`() {
        val repo = FakeMachines()
        val vm = newMachine(repo)
        vm.setKind(MachineKind.CARDIO)

        vm.save()

        assertTrue(repo.saved.isEmpty())
        assertFalse(vm.uiState.value.done)
    }
}

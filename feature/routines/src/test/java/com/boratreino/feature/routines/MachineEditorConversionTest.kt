package com.boratreino.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.ConversionFactor
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.KindConversionRules
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.testing.StubMachineRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Saving a machine whose kind changed while it has logged sets: the conversion dialog, its factor,
 * and the one confirmed save that converts the sets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineEditorConversionTest {
    /** A machine with [sets] logged; records each conversion the editor asks for. */
    private class ConvertingMachines(private val stored: Machine, private val sets: List<WorkoutSet>) : StubMachineRepository() {
        val converted = mutableListOf<Pair<MachineKind, Int?>>()
        var convertSucceeds = true

        override suspend fun kindConversions(machineId: Long): Map<MachineKind, KindConversion> =
            if (machineId == stored.id) KindConversionRules.conversions(stored.kind, sets) else emptyMap()

        override suspend fun saveConvertingSets(machine: Machine, factor: Int?): Boolean {
            converted += machine.kind to factor
            return convertSucceeds
        }

        override suspend fun usage(machineId: Long): MachineUsage = MachineUsage(setCount = sets.size, routineNames = emptyList())
        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(stored.takeIf { it.id == id })
        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(listOf(stored))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun set(id: Long, machineId: Long, weightKg: Double?, reps: Int?, seconds: Int? = null, distanceM: Int? = null) =
        WorkoutSet(
            id = id, sessionId = 1L, machineId = machineId, position = id.toInt(), weightKg = weightKg, reps = reps,
            seconds = seconds, calories = null, distanceM = distanceM, loggedAt = Instant.EPOCH,
        )

    private fun machine(id: Long, kind: MachineKind, group: MuscleGroup = MuscleGroup.ARMS) =
        Machine(id = id, name = "Farmer's walk", muscleGroup = group, kind = kind, stepKg = 2.0, setupNote = null, restSeconds = null, archived = false)

    private fun editor(repo: MachineRepository, machineId: Long) =
        MachineEditorViewModel(repo, FakeExerciseLibrary(), SavedStateHandle(mapOf(MACHINE_ID_ARG to machineId)))

    @Test
    fun `changing a weight and reps machine to weight and time asks for the seconds per rep`() {
        val stored = machine(5L, MachineKind.WEIGHT_REPS)
        val repo = ConvertingMachines(stored, listOf(set(1L, 5L, weightKg = 47.5, reps = 1)))
        val vm = editor(repo, 5L)

        vm.setKind(MachineKind.WEIGHT_TIME)
        vm.save()

        val dialog = requireNotNull(vm.uiState.value.convertDialog)
        assertEquals(30, dialog.factor)
        assertEquals(1, dialog.setCount)
        assertTrue(dialog.canConfirm)
        assertEquals(ConversionFactor.SECONDS_PER_REP, (dialog.conversion as KindConversion.NeedsFactor).factor)
        assertTrue(repo.converted.isEmpty())
    }

    @Test
    fun `confirming the seconds per rep converts the sets with that factor`() {
        val stored = machine(5L, MachineKind.WEIGHT_REPS)
        val repo = ConvertingMachines(stored, listOf(set(1L, 5L, weightKg = 47.5, reps = 1)))
        val vm = editor(repo, 5L)
        vm.setKind(MachineKind.WEIGHT_TIME)
        vm.save()

        vm.confirmConvert()

        assertEquals(listOf(MachineKind.WEIGHT_TIME to 30), repo.converted)
        assertTrue(vm.uiState.value.done)
        assertNull(vm.uiState.value.convertDialog)
    }

    @Test
    fun `stepping the seconds per rep moves it by five`() {
        val stored = machine(5L, MachineKind.WEIGHT_REPS)
        val repo = ConvertingMachines(stored, listOf(set(1L, 5L, weightKg = 47.5, reps = 1)))
        val vm = editor(repo, 5L)
        vm.setKind(MachineKind.WEIGHT_TIME)
        vm.save()

        vm.stepFactor(up = true)
        assertEquals(35, vm.uiState.value.convertDialog?.factor)

        vm.confirmConvert()
        assertEquals(listOf(MachineKind.WEIGHT_TIME to 35), repo.converted)
    }

    @Test
    fun `changing a bodyweight machine to weight and reps keeps the numbers and asks for no factor`() {
        val stored = machine(6L, MachineKind.BODYWEIGHT_REPS, MuscleGroup.BACK)
        val repo = ConvertingMachines(stored, listOf(set(1L, 6L, weightKg = 10.0, reps = 12)))
        val vm = editor(repo, 6L)

        vm.setKind(MachineKind.WEIGHT_REPS)
        vm.save()

        val dialog = requireNotNull(vm.uiState.value.convertDialog)
        assertEquals(KindConversion.Unchanged(1), dialog.conversion)
        assertNull(dialog.factor)
        assertTrue(dialog.canConfirm)

        vm.confirmConvert()

        assertEquals(listOf(MachineKind.WEIGHT_REPS to null), repo.converted)
    }

    @Test
    fun `changing to metres asks for the metres per rep and cannot confirm until it is set`() {
        val stored = machine(7L, MachineKind.WEIGHT_REPS, MuscleGroup.CHEST)
        val repo = ConvertingMachines(stored, listOf(set(1L, 7L, weightKg = 40.0, reps = 10)))
        val vm = editor(repo, 7L)
        vm.setKind(MachineKind.WEIGHT_DISTANCE)

        vm.save()
        val empty = requireNotNull(vm.uiState.value.convertDialog)
        assertNull(empty.factor)
        assertFalse(empty.canConfirm)

        vm.confirmConvert()
        assertTrue(repo.converted.isEmpty())

        vm.setFactor(25)
        assertTrue(requireNotNull(vm.uiState.value.convertDialog).canConfirm)

        vm.confirmConvert()
        assertEquals(listOf(MachineKind.WEIGHT_DISTANCE to 25), repo.converted)
    }

    @Test
    fun `a typed metres factor of zero clears it again`() {
        val stored = machine(7L, MachineKind.WEIGHT_REPS, MuscleGroup.CHEST)
        val repo = ConvertingMachines(stored, listOf(set(1L, 7L, weightKg = 40.0, reps = 10)))
        val vm = editor(repo, 7L)
        vm.setKind(MachineKind.WEIGHT_DISTANCE)
        vm.save()
        vm.setFactor(25)

        vm.setFactor(0)

        assertNull(requireNotNull(vm.uiState.value.convertDialog).factor)
        assertFalse(requireNotNull(vm.uiState.value.convertDialog).canConfirm)
    }

    @Test
    fun `a time kind is not offered while a logged set carries a load`() {
        val stored = machine(5L, MachineKind.WEIGHT_REPS)
        val repo = ConvertingMachines(stored, listOf(set(1L, 5L, weightKg = 10.0, reps = 5)))
        val vm = editor(repo, 5L)

        val state = vm.uiState.value
        assertFalse(state.kindEnabled(MachineKind.TIME))
        assertTrue(state.kindEnabled(MachineKind.WEIGHT_TIME))
        assertEquals(1, state.lockedSetCount)
    }

    @Test
    fun `cancelling the conversion dialog saves nothing`() {
        val stored = machine(5L, MachineKind.WEIGHT_REPS)
        val repo = ConvertingMachines(stored, listOf(set(1L, 5L, weightKg = 47.5, reps = 1)))
        val vm = editor(repo, 5L)
        vm.setKind(MachineKind.WEIGHT_TIME)
        vm.save()

        vm.dismissConvert()

        assertNull(vm.uiState.value.convertDialog)
        assertTrue(repo.converted.isEmpty())
        assertFalse(vm.uiState.value.done)
    }

    @Test
    fun `a refused conversion shows the save failure and does not leave`() {
        val stored = machine(5L, MachineKind.WEIGHT_REPS)
        val repo = ConvertingMachines(stored, listOf(set(1L, 5L, weightKg = 47.5, reps = 1)))
        repo.convertSucceeds = false
        val vm = editor(repo, 5L)
        vm.setKind(MachineKind.WEIGHT_TIME)
        vm.save()

        vm.confirmConvert()

        assertTrue(vm.uiState.value.saveFailed)
        assertFalse(vm.uiState.value.done)
    }
}

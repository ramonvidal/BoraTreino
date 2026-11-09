package com.boratreino.feature.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.StarterMachines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

/**
 * The exercise picker on the Routines tab: what is listed, marked, searched, filtered, selected, shown in
 * "How to do it" and added. Written from the screen's behaviour, not from the view model's code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoutinesLibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Machines in the gym, the starters still on offer, and the library ids already in use. */
    private class PickerMachines(
        private val stored: List<Machine>,
        private val notAdded: List<StarterMachine>,
        private val inUse: Set<String>,
    ) : MachineRepository {
        val addedStarters = mutableListOf<String>()
        val addedLibrary = mutableListOf<String>()

        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(stored)
        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(stored.firstOrNull { it.id == id })
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(notAdded)
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = error("not used by this test")
        override suspend fun delete(machineId: Long): Boolean = false
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long {
            addedStarters += starter.name
            return 0L
        }
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> {
            addedStarters += starters.map { it.name }
            return starters.map { 0L }
        }
        override suspend fun addFromLibrary(exercise: LibraryExercise, portuguese: Boolean): Long {
            addedLibrary += exercise.id
            return 0L
        }
        override fun observeLibraryIdsInUse(): Flow<Set<String>> = flowOf(inUse)
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private fun exercise(
        id: String,
        name: String,
        namePt: String,
        equipment: LibraryEquipment,
        level: LibraryLevel,
        primary: List<String>,
        category: String = "strength",
    ) = LibraryExercise(
        id = id,
        name = name,
        namePt = namePt,
        equipment = equipment,
        level = level,
        category = category,
        mechanic = null,
        force = null,
        primaryMuscles = primary,
        secondaryMuscles = emptyList(),
        steps = listOf("Stand tall.", "Curl the weight up."),
    )

    private val bench = exercise(
        "Barbell_Bench_Press_-_Medium_Grip", "Barbell Bench Press - Medium Grip", "Supino reto com pegada média",
        LibraryEquipment.BARBELL, LibraryLevel.INTERMEDIATE, listOf("chest"),
    )
    private val leg = exercise("Leg_Press", "Leg Press", "Leg press", LibraryEquipment.MACHINE, LibraryLevel.INTERMEDIATE, listOf("quadriceps"))
    private val zottman = exercise("Zottman_Curl", "Zottman Curl", "Rosca Zottman", LibraryEquipment.DUMBBELL, LibraryLevel.BEGINNER, listOf("biceps"))
    private val lateral = exercise("Side_Lateral_Raise", "Side Lateral Raise", "Elevação lateral", LibraryEquipment.DUMBBELL, LibraryLevel.BEGINNER, listOf("shoulders"))
    private val running = exercise("Running", "Running", "Corrida", LibraryEquipment.OTHER, LibraryLevel.BEGINNER, emptyList(), category = "cardio")
    private val library = listOf(bench, leg, zottman, lateral, running)

    private val legPress = Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, false, libraryId = "Leg_Press")

    /** The gym has the leg press; every other starter is still on offer. */
    private val notAddedStarters = StarterMachines.all.filter { it.name != "Leg press" }

    private fun TestScope.viewModel(
        machines: PickerMachines = PickerMachines(listOf(legPress), notAddedStarters, inUse = setOf("Leg_Press")),
        exercises: List<LibraryExercise> = library,
    ): RoutinesViewModel {
        val vm = RoutinesViewModel(machines, FakeRoutineRepository(emptyList<RoutineWithItems>()), FakeExerciseLibrary(exercises))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        runCurrent()
        return vm
    }

    private fun TestScope.openPicker(vm: RoutinesViewModel) {
        vm.toggleStarters()
        runCurrent()
    }

    private fun RoutinesViewModel.rows(): List<PickerRow> = uiState.value.pickerGroups.flatMap { it.rows }

    private fun RoutinesViewModel.keys(): List<String> = rows().map { it.key }

    // Opening and listing

    @Test
    fun `the picker stays folded until it is opened, but its card is there`() = runTest(dispatcher) {
        val vm = viewModel()

        assertFalse(vm.uiState.value.startersExpanded)
        assertTrue(vm.uiState.value.pickerGroups.isEmpty())
        assertTrue(vm.uiState.value.pickerAvailable)
    }

    @Test
    fun `opening the picker lists the starters still on offer and the library, by muscle group`() = runTest(dispatcher) {
        val vm = viewModel()

        openPicker(vm)

        assertTrue(vm.uiState.value.startersExpanded)
        // "Running" is a starter ("Running (outdoor)", Portuguese "Corrida") with a same-named Portuguese name, so the
        // library entry is shown as that starter, not as its own row.
        assertTrue(vm.keys().containsAll(listOf("s:Barbell bench press", "s:Dumbbell lateral raise", "l:Zottman_Curl", "s:Running (outdoor)")))
        assertFalse(vm.keys().contains("l:Running"))
        val groups = vm.uiState.value.pickerGroups.map { it.muscleGroup }
        assertEquals(groups.sorted(), groups)
        assertTrue(MuscleGroup.CARDIO in groups)
        assertEquals(vm.rows().size, vm.uiState.value.pickerCount)
    }

    @Test
    fun `a library exercise that is the same as a starter still on offer is shown once, as the starter`() = runTest(dispatcher) {
        val vm = viewModel()

        openPicker(vm)

        val shown = vm.rows().filter { it.libraryId == bench.id }
        assertEquals(1, shown.size)
        assertEquals("s:Barbell bench press", shown.single().key)
        assertEquals(LibraryEquipment.BARBELL, shown.single().equipment)
        assertFalse(vm.keys().contains("l:${bench.id}"))
        assertFalse(vm.keys().contains("l:${lateral.id}"))
    }

    @Test
    fun `a starter row points at its library entry for instructions, and one without an entry has none`() = runTest(dispatcher) {
        val vm = viewModel()

        openPicker(vm)

        val withEntry = vm.rows().single { it.key == "s:Barbell bench press" }
        assertEquals(bench.id, withEntry.libraryId)
        val withoutEntry = vm.rows().single { it.key == "s:Lat pulldown" }
        assertNull(withoutEntry.libraryId)
    }

    @Test
    fun `an exercise already in the gym is listed as added, and the others are not`() = runTest(dispatcher) {
        val vm = viewModel()

        openPicker(vm)

        assertTrue(vm.rows().single { it.key == "l:Leg_Press" }.added)
        assertFalse(vm.rows().single { it.key == "l:Zottman_Curl" }.added)
    }

    @Test
    fun `with nothing to pick from, the card is not offered`() = runTest(dispatcher) {
        val vm = viewModel(PickerMachines(listOf(legPress), emptyList(), inUse = setOf("Leg_Press")), exercises = emptyList())

        assertFalse(vm.uiState.value.pickerAvailable)
    }

    // Search

    @Test
    fun `the search finds a library exercise by its Portuguese name, ignoring case`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.searchStarters("ROSCA zot")
        runCurrent()

        assertEquals(listOf("l:Zottman_Curl"), vm.keys())
    }

    @Test
    fun `the search ignores accents`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        // Accents in the query are dropped on both sides: this library-only entry matches its Portuguese name.
        vm.searchStarters("ROSCA ZÓTTMAN")
        runCurrent()
        assertEquals(listOf("l:Zottman_Curl"), vm.keys())

        vm.searchStarters("rosca zottman")
        runCurrent()
        assertEquals(listOf("l:Zottman_Curl"), vm.keys())
    }

    @Test
    fun `the search finds a starter by its Portuguese name as well as its English one`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.searchStarters("supino")
        runCurrent()

        assertTrue(vm.keys().contains("s:Barbell bench press"))
    }

    // Filters

    @Test
    fun `the muscle group chip keeps that group only`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.setGroupFilter(MuscleGroup.ARMS)
        runCurrent()

        assertTrue(vm.rows().isNotEmpty())
        assertTrue(vm.rows().all { it.group == MuscleGroup.ARMS })
        assertTrue(vm.keys().contains("l:Zottman_Curl"))
    }

    @Test
    fun `the equipment chip keeps that equipment, starters by their library entry`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.setEquipmentFilter(LibraryEquipment.BARBELL)
        runCurrent()

        assertEquals(listOf("s:Barbell bench press"), vm.keys())
    }

    @Test
    fun `the level chip and the group chip together keep only what matches both`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.setGroupFilter(MuscleGroup.ARMS)
        vm.setLevelFilter(LibraryLevel.BEGINNER)
        runCurrent()
        assertEquals(listOf("l:Zottman_Curl"), vm.keys())

        vm.setLevelFilter(LibraryLevel.EXPERT)
        runCurrent()
        assertTrue(vm.keys().isEmpty())
    }

    @Test
    fun `clear filters removes the chips and keeps the search text`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)
        vm.searchStarters("curl")
        vm.setGroupFilter(MuscleGroup.ARMS)
        vm.setEquipmentFilter(LibraryEquipment.DUMBBELL)
        runCurrent()
        assertTrue(vm.uiState.value.filter.isActive)

        vm.clearFilters()
        runCurrent()

        assertFalse(vm.uiState.value.filter.isActive)
        assertEquals("curl", vm.uiState.value.filter.query)
    }

    // Selection

    @Test
    fun `tapping an exercise ticks it and tapping again unticks it`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.toggleLibrary("Zottman_Curl")
        runCurrent()
        assertEquals(setOf("Zottman_Curl"), vm.uiState.value.selectedLibrary)
        assertEquals(1, vm.uiState.value.selectedCount)

        vm.toggleLibrary("Zottman_Curl")
        runCurrent()
        assertTrue(vm.uiState.value.selectedLibrary.isEmpty())
        assertEquals(0, vm.uiState.value.selectedCount)
    }

    @Test
    fun `an exercise already in the gym cannot be ticked`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.toggleLibrary("Leg_Press")
        runCurrent()

        assertTrue(vm.uiState.value.selectedLibrary.isEmpty())
    }

    @Test
    fun `add selected adds the ticked starters and exercises together and clears the ticks`() = runTest(dispatcher) {
        val machines = PickerMachines(listOf(legPress), notAddedStarters, inUse = setOf("Leg_Press"))
        val vm = viewModel(machines)
        openPicker(vm)
        vm.toggleStarter(StarterMachines.all.single { it.name == "Barbell bench press" })
        vm.toggleLibrary("Zottman_Curl")
        runCurrent()
        assertEquals(2, vm.uiState.value.selectedCount)

        vm.addSelectedStarters()
        runCurrent()

        assertEquals(listOf("Barbell bench press"), machines.addedStarters)
        assertEquals(listOf("Zottman_Curl"), machines.addedLibrary)
        assertEquals(0, vm.uiState.value.selectedCount)
    }

    @Test
    fun `add selected with nothing ticked adds nothing`() = runTest(dispatcher) {
        val machines = PickerMachines(listOf(legPress), notAddedStarters, inUse = setOf("Leg_Press"))
        val vm = viewModel(machines)
        openPicker(vm)

        vm.addSelectedStarters()
        runCurrent()

        assertTrue(machines.addedStarters.isEmpty())
        assertTrue(machines.addedLibrary.isEmpty())
    }

    // How to do it

    @Test
    fun `how to do it shows a library exercise, not yet added`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.showDetail(vm.rows().single { it.key == "l:Zottman_Curl" })
        runCurrent()

        val detail = vm.uiState.value.detail
        assertEquals("Zottman_Curl", detail?.exercise?.id)
        assertEquals(false, detail?.added)
        assertNull(detail?.starterName)
    }

    @Test
    fun `how to do it of an exercise already in the gym says it is added`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.showDetail(vm.rows().single { it.key == "l:Leg_Press" })
        runCurrent()

        assertTrue(vm.uiState.value.detail?.added == true)
    }

    @Test
    fun `a row without instructions has no how to do it`() = runTest(dispatcher) {
        val vm = viewModel()
        openPicker(vm)

        vm.showDetail(vm.rows().single { it.key == "s:Lat pulldown" })
        runCurrent()

        assertNull(vm.uiState.value.detail)
    }

    @Test
    fun `dismissing how to do it closes it and adds nothing`() = runTest(dispatcher) {
        val machines = PickerMachines(listOf(legPress), notAddedStarters, inUse = setOf("Leg_Press"))
        val vm = viewModel(machines)
        openPicker(vm)
        vm.showDetail(vm.rows().single { it.key == "l:Zottman_Curl" })

        vm.dismissDetail()
        runCurrent()

        assertNull(vm.uiState.value.detail)
        assertTrue(machines.addedLibrary.isEmpty())
    }

    @Test
    fun `add in how to do it adds just that exercise and closes it`() = runTest(dispatcher) {
        val machines = PickerMachines(listOf(legPress), notAddedStarters, inUse = setOf("Leg_Press"))
        val vm = viewModel(machines)
        openPicker(vm)
        vm.showDetail(vm.rows().single { it.key == "l:Zottman_Curl" })

        vm.addDetail()
        runCurrent()

        assertEquals(listOf("Zottman_Curl"), machines.addedLibrary)
        assertTrue(machines.addedStarters.isEmpty())
        assertNull(vm.uiState.value.detail)
    }

    @Test
    fun `add in how to do it of a starter adds that starter only`() = runTest(dispatcher) {
        val machines = PickerMachines(listOf(legPress), notAddedStarters, inUse = setOf("Leg_Press"))
        val vm = viewModel(machines)
        openPicker(vm)

        vm.showDetail(vm.rows().single { it.key == "s:Barbell bench press" })
        runCurrent()
        assertEquals("Barbell bench press", vm.uiState.value.detail?.starterName)
        vm.addDetail()
        runCurrent()

        assertEquals(listOf("Barbell bench press"), machines.addedStarters)
        assertTrue(machines.addedLibrary.isEmpty())
    }
}

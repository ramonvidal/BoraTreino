package com.boratreino.core.data

import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MachineLibraryRepositoryTest {
    private lateinit var store: FakeWorkoutStore
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        machines = DefaultMachineRepository(store)
    }

    private fun exercise(
        id: String = "Barbell_Bench_Press",
        name: String = "Barbell Bench Press",
        namePt: String = "Supino reto com barra",
        equipment: LibraryEquipment = LibraryEquipment.BARBELL,
        category: String = "strength",
        primary: List<String> = listOf("chest"),
    ) = LibraryExercise(
        id = id,
        name = name,
        namePt = namePt,
        equipment = equipment,
        level = LibraryLevel.INTERMEDIATE,
        category = category,
        mechanic = null,
        force = null,
        primaryMuscles = primary,
        secondaryMuscles = emptyList(),
        steps = listOf("Lie down.", "Press up."),
    )

    private suspend fun allMachines(): List<Machine> = machines.observeMachines(includeArchived = true).first()

    // addFromLibrary: creating

    @Test
    fun `adding an exercise creates a machine with its mapped group, kind and step`() = runTest {
        val id = machines.addFromLibrary(exercise(), portuguese = false)

        val stored = requireNotNull(machines.observeMachine(id).first())
        assertEquals("Barbell Bench Press", stored.name)
        assertEquals(MuscleGroup.CHEST, stored.muscleGroup)
        assertEquals(MachineKind.WEIGHT_REPS, stored.kind)
        assertEquals(2.5, stored.stepKg, 0.0)
        assertEquals("Barbell_Bench_Press", stored.libraryId)
        assertFalse(stored.archived)
        assertNull(stored.setupNote)
        assertNull(stored.restSeconds)
    }

    @Test
    fun `adding in Portuguese stores the Portuguese name`() = runTest {
        val id = machines.addFromLibrary(exercise(), portuguese = true)

        assertEquals("Supino reto com barra", machines.observeMachine(id).first()?.name)
    }

    @Test
    fun `a body weight exercise adds as bodyweight reps`() = runTest {
        val id = machines.addFromLibrary(
            exercise(id = "Pull-Up", name = "Pull-Up", namePt = "Barra fixa", equipment = LibraryEquipment.BODY_ONLY, primary = listOf("lats")),
            portuguese = true,
        )

        val stored = requireNotNull(machines.observeMachine(id).first())
        assertEquals(MuscleGroup.BACK, stored.muscleGroup)
        assertEquals(MachineKind.BODYWEIGHT_REPS, stored.kind)
        assertEquals(2.5, stored.stepKg, 0.0)
    }

    @Test
    fun `a cardio exercise adds as cardio with a step of one unit`() = runTest {
        val id = machines.addFromLibrary(
            exercise(id = "Running", name = "Running", namePt = "Corrida", equipment = LibraryEquipment.OTHER, category = "cardio", primary = emptyList()),
            portuguese = true,
        )

        val stored = requireNotNull(machines.observeMachine(id).first())
        assertEquals(MuscleGroup.CARDIO, stored.muscleGroup)
        assertEquals(MachineKind.CARDIO, stored.kind)
        assertEquals(1.0, stored.stepKg, 0.0)
        assertEquals("Running", stored.libraryId)
    }

    // addFromLibrary: same name

    @Test
    fun `adding the same exercise twice gives one machine and the same id`() = runTest {
        val first = machines.addFromLibrary(exercise(), portuguese = false)
        val second = machines.addFromLibrary(exercise(), portuguese = false)

        assertEquals(first, second)
        assertEquals(1, allMachines().size)
    }

    @Test
    fun `a stored machine with the same name, in another case, is reused and not duplicated`() = runTest {
        val existing = machines.save(Machine(0, "barbell BENCH press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

        val id = machines.addFromLibrary(exercise(), portuguese = false)

        assertEquals(existing, id)
        assertEquals(1, allMachines().size)
    }

    @Test
    fun `an archived machine with the same name is unarchived and its id returned`() = runTest {
        val existing = machines.save(Machine(0, "Barbell Bench Press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, archived = true))

        val id = machines.addFromLibrary(exercise(), portuguese = false)

        assertEquals(existing, id)
        assertFalse(requireNotNull(machines.observeMachine(id).first()).archived)
        assertEquals(1, allMachines().size)
    }

    // Starters get their library link

    @Test
    fun `adding a starter that has a library entry links the machine to it`() = runTest {
        val id = machines.addStarter(StarterMachines.all.single { it.name == "Leg press" })

        assertEquals("Leg_Press", machines.observeMachine(id).first()?.libraryId)
    }

    @Test
    fun `adding a starter with no library entry leaves the link empty`() = runTest {
        val id = machines.addStarter(StarterMachines.all.single { it.name == "Lat pulldown" })

        assertNull(machines.observeMachine(id).first()?.libraryId)
    }

    @Test
    fun `adding starters in one go links each one that has an entry`() = runTest {
        val starters = listOf("Leg press", "Lat pulldown", "Leg extension").map { name -> StarterMachines.all.single { it.name == name } }

        val ids = machines.addStarters(starters, portuguese = true)

        assertEquals(listOf("Leg_Press", null, "Leg_Extensions"), ids.map { machines.observeMachine(it).first()?.libraryId })
    }

    @Test
    fun `a saved machine keeps its library link`() = runTest {
        val id = machines.save(Machine(0, "Supino", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false, libraryId = "Barbell_Bench_Press"))

        assertEquals("Barbell_Bench_Press", machines.observeMachine(id).first()?.libraryId)
    }

    // observeLibraryIdsInUse

    @Test
    fun `the library ids in use are those of the machines not archived`() = runTest {
        machines.addFromLibrary(exercise(), portuguese = false)
        machines.addFromLibrary(
            exercise(id = "Pull-Up", name = "Pull-Up", namePt = "Barra fixa", equipment = LibraryEquipment.BODY_ONLY, primary = listOf("lats")),
            portuguese = false,
        )
        val pullUp = machines.observeMachines().first().single { it.libraryId == "Pull-Up" }.id
        machines.setArchived(pullUp, true)

        assertEquals(setOf("Barbell_Bench_Press"), machines.observeLibraryIdsInUse().first())
    }

    @Test
    fun `machines without a library link do not count as in use`() = runTest {
        machines.save(Machine(0, "Home-made rig", MuscleGroup.CORE, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

        assertEquals(emptySet<String>(), machines.observeLibraryIdsInUse().first())
    }

    @Test
    fun `a starter added from the library shows as in use`() = runTest {
        machines.addStarter(StarterMachines.all.single { it.name == "Leg press" })

        assertTrue(machines.observeLibraryIdsInUse().first().contains("Leg_Press"))
    }

    @Test
    fun `deleting a machine takes its library id out of use`() = runTest {
        val id = machines.addFromLibrary(exercise(), portuguese = false)
        assertEquals(setOf("Barbell_Bench_Press"), machines.observeLibraryIdsInUse().first())

        assertTrue(machines.delete(id))

        assertEquals(emptySet<String>(), machines.observeLibraryIdsInUse().first())
    }
}

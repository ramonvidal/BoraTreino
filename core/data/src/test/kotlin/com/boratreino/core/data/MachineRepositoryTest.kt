package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.StarterMachines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MachineRepositoryTest {
    private val t0: Instant = Instant.parse("2026-10-30T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var machines: MachineRepository
    private lateinit var training: TrainingRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(t0)
        machines = DefaultMachineRepository(store)
        training = DefaultTrainingRepository(store, clock, DefaultRoutineRepository(store))
    }

    private fun starter(name: String): StarterMachine = StarterMachines.all.single { it.name == name }

    private suspend fun save(
        name: String,
        group: MuscleGroup = MuscleGroup.BACK,
        archived: Boolean = false,
    ): Long = machines.save(Machine(0, name, group, MachineKind.WEIGHT_REPS, 2.5, null, null, archived))

    // save

    @Test
    fun `saving with id 0 inserts and returns the new id`() = runTest {
        val id = save("Lat pulldown")

        assertTrue(id > 0)
        assertEquals("Lat pulldown", machines.observeMachine(id).first()?.name)
    }

    @Test
    fun `saving with an existing id updates that machine`() = runTest {
        val id = save("Lat pulldown")
        val original = requireNotNull(machines.observeMachine(id).first())

        val returned = machines.save(original.copy(name = "Wide lat pulldown", stepKg = 5.0))

        assertEquals(id, returned)
        val updated = requireNotNull(machines.observeMachine(id).first())
        assertEquals("Wide lat pulldown", updated.name)
        assertEquals(5.0, updated.stepKg, 0.0)
        assertEquals(1, machines.observeMachines(includeArchived = true).first().size)
    }

    @Test
    fun `an unknown id has no machine`() = runTest {
        assertNull(machines.observeMachine(999L).first())
    }

    // observeMachines

    @Test
    fun `machines are listed by name ignoring case`() = runTest {
        save("lat pulldown")
        save("Calf raise", MuscleGroup.LEGS)
        save("Back extension")

        val names = machines.observeMachines().first().map { it.name }

        assertEquals(listOf("Back extension", "Calf raise", "lat pulldown"), names)
    }

    @Test
    fun `archived machines are hidden unless asked for`() = runTest {
        val kept = save("Seated row")
        val archived = save("Lat pulldown")
        machines.setArchived(archived, true)

        assertEquals(listOf(kept), machines.observeMachines().first().map { it.id })
        // Sorted by name: "Lat pulldown" (archived) comes before "Seated row".
        assertEquals(
            listOf(archived, kept),
            machines.observeMachines(includeArchived = true).first().map { it.id },
        )
    }

    @Test
    fun `setArchived can unarchive again`() = runTest {
        val id = save("Lat pulldown")
        machines.setArchived(id, true)

        machines.setArchived(id, false)

        assertEquals(listOf(id), machines.observeMachines().first().map { it.id })
        assertFalse(requireNotNull(machines.observeMachine(id).first()).archived)
    }

    // observeRecentMachines

    @Test
    fun `recent machines leave out machines never used`() = runTest {
        val used = save("Lat pulldown")
        save("Seated row")
        training.logSet(used, SetDraft(55.0, 10))

        assertEquals(listOf(used), machines.observeRecentMachines().first().map { it.id })
    }

    @Test
    fun `recent machines leave out archived machines`() = runTest {
        val kept = save("Lat pulldown")
        val archived = save("Seated row")
        training.logSet(kept, SetDraft(55.0, 10))
        training.logSet(archived, SetDraft(40.0, 10))
        machines.setArchived(archived, true)

        assertEquals(listOf(kept), machines.observeRecentMachines().first().map { it.id })
    }

    @Test
    fun `recent machines are the most recently logged first`() = runTest {
        val older = save("Lat pulldown")
        val newer = save("Seated row")
        training.logSet(older, SetDraft(55.0, 10))
        clock.advance(Duration.ofDays(1))
        training.logSet(newer, SetDraft(40.0, 10))

        assertEquals(listOf(newer, older), machines.observeRecentMachines().first().map { it.id })
    }

    @Test
    fun `recent machines are capped at the limit`() = runTest {
        val older = save("Lat pulldown")
        val newer = save("Seated row")
        training.logSet(older, SetDraft(55.0, 10))
        clock.advance(Duration.ofDays(1))
        training.logSet(newer, SetDraft(40.0, 10))

        assertEquals(listOf(newer), machines.observeRecentMachines(limit = 1).first().map { it.id })
    }

    // Starters

    @Test
    fun `starters not added are all of them when nothing is stored`() = runTest {
        assertEquals(StarterMachines.all, machines.observeStartersNotAdded().first())
    }

    @Test
    fun `starters already added are left out`() = runTest {
        machines.addStarter(starter("Leg press"))

        val names = machines.observeStartersNotAdded().first().map { it.name }

        assertFalse(names.contains("Leg press"))
        assertTrue(names.contains("Leg extension"))
        assertEquals(StarterMachines.all.size - 1, names.size)
    }

    @Test
    fun `a starter with a typed name in another case counts as added`() = runTest {
        save("LEG PRESS", MuscleGroup.LEGS)

        val names = machines.observeStartersNotAdded().first().map { it.name }

        assertFalse(names.contains("Leg press"))
    }

    @Test
    fun `an archived starter that was added is still left out`() = runTest {
        val id = machines.addStarter(starter("Leg press"))
        machines.setArchived(id, true)

        val names = machines.observeStartersNotAdded().first().map { it.name }

        assertFalse(names.contains("Leg press"))
    }

    @Test
    fun `adding a starter stores it with the starter's details`() = runTest {
        val id = machines.addStarter(starter("Pull-ups"))

        val stored = requireNotNull(machines.observeMachine(id).first())
        assertEquals("Pull-ups", stored.name)
        assertEquals(MuscleGroup.BACK, stored.muscleGroup)
        assertEquals(MachineKind.BODYWEIGHT_REPS, stored.kind)
        assertEquals(2.5, stored.stepKg, 0.0)
        assertNull(stored.setupNote)
        assertFalse(stored.archived)
    }

    @Test
    fun `adding a starter whose name is already stored unarchives that machine and returns its id`() = runTest {
        val existing = save("Lat pulldown", archived = true)

        val returned = machines.addStarter(starter("Lat pulldown"))

        assertEquals(existing, returned)
        val all = machines.observeMachines(includeArchived = true).first()
        assertEquals(1, all.size)
        assertFalse(all.single().archived)
    }

    @Test
    fun `adding a starter matches an existing name ignoring case`() = runTest {
        val existing = save("LAT PULLDOWN")

        val returned = machines.addStarter(starter("Lat pulldown"))

        assertEquals(existing, returned)
        assertEquals(1, machines.observeMachines(includeArchived = true).first().size)
    }

    @Test
    fun `adding a starter with a new name inserts a machine`() = runTest {
        save("Seated row")

        val id = machines.addStarter(starter("Lat pulldown"))

        assertNotNull(machines.observeMachine(id).first())
        assertEquals(2, machines.observeMachines(includeArchived = true).first().size)
    }

    // Names are trimmed; matching ignores surrounding spaces and case

    @Test
    fun `saving trims the name`() = runTest {
        val id = save("  Lat pulldown  ")

        assertEquals("Lat pulldown", machines.observeMachine(id).first()?.name)
    }

    @Test
    fun `a stored name with a trailing space counts as the starter`() = runTest {
        val stored = store.saveMachine(Machine(0, "Leg press ", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, false))

        val names = machines.observeStartersNotAdded().first().map { it.name }

        assertFalse(names.contains("Leg press"))
        assertEquals(StarterMachines.all.size - 1, names.size)
        assertEquals(stored, machines.addStarter(starter("Leg press")))
        assertEquals(1, machines.observeMachines(includeArchived = true).first().size)
    }

    @Test
    fun `a stored name with spaces and another case counts as the starter`() = runTest {
        store.saveMachine(Machine(0, "  LEG PRESS ", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, false))

        val names = machines.observeStartersNotAdded().first().map { it.name }

        assertFalse(names.contains("Leg press"))
    }

    @Test
    fun `adding a starter whose stored name has spaces unarchives that machine`() = runTest {
        val stored = store.saveMachine(Machine(0, "Leg press ", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, true))

        val returned = machines.addStarter(starter("Leg press"))

        assertEquals(stored, returned)
        val all = machines.observeMachines(includeArchived = true).first()
        assertEquals(1, all.size)
        assertFalse(all.single().archived)
    }

    // Usage

    private val routines by lazy { DefaultRoutineRepository(store) }

    /** A routine with the machines, in order; it takes the next position. */
    private suspend fun routineWith(name: String, shortName: String, vararg machineIds: Long): Long =
        routines.saveRoutine(
            Routine(0, 0, name, shortName, 0, emptySet()),
            machineIds.map { RoutineItem(0, it, 0, 3, 8, 12) },
        )

    @Test
    fun `usage counts the logged sets of the machine`() = runTest {
        val curl = save("Leg curl", MuscleGroup.LEGS)
        training.logSet(curl, SetDraft(20.0, 10))
        training.logSet(curl, SetDraft(20.0, 9))

        val usage = machines.usage(curl)

        assertEquals(2, usage.setCount)
        assertFalse(usage.canDelete)
    }

    @Test
    fun `usage lists the routines that contain the machine, by routine position`() = runTest {
        val curl = save("Leg curl", MuscleGroup.LEGS)
        val press = save("Leg press", MuscleGroup.LEGS)
        routineWith("Treino C", "C", curl, press)
        routineWith("Treino A", "A", press)
        routineWith("Treino B", "B", curl)

        val usage = machines.usage(curl)

        assertEquals(listOf("Treino C", "Treino B"), usage.routineNames)
        assertEquals(0, usage.setCount)
        assertTrue(usage.canDelete)
    }

    // delete

    @Test
    fun `deleting a machine without sets takes it out of every routine and deletes it`() = runTest {
        val curl = save("Leg curl", MuscleGroup.LEGS)
        val press = save("Leg press", MuscleGroup.LEGS)
        val a = routineWith("Treino A", "A", press, curl)
        val b = routineWith("Treino B", "B", curl)

        assertTrue(machines.delete(curl))

        assertNull(machines.observeMachine(curl).first())
        assertEquals(listOf(press), requireNotNull(routines.observeRoutine(a).first()).items.map { it.machineId })
        assertEquals(emptyList<Long>(), requireNotNull(routines.observeRoutine(b).first()).items.map { it.machineId })
    }

    @Test
    fun `deleting a machine with logged sets is refused and changes nothing`() = runTest {
        val curl = save("Leg curl", MuscleGroup.LEGS)
        val a = routineWith("Treino A", "A", curl)
        training.logSet(curl, SetDraft(20.0, 10))

        assertFalse(machines.delete(curl))

        assertNotNull(machines.observeMachine(curl).first())
        assertEquals(listOf(curl), requireNotNull(routines.observeRoutine(a).first()).items.map { it.machineId })
        assertEquals(1, machines.usage(curl).setCount)
    }

    @Test
    fun `deleting a machine that doesn't exist returns false`() = runTest {
        assertFalse(machines.delete(999L))
    }

    // Starters in either language

    @Test
    fun `adding a starter in Portuguese stores its Portuguese name`() = runTest {
        val id = machines.addStarter(starter("Barbell bench press"), portuguese = true)

        assertEquals("Supino reto", requireNotNull(machines.observeMachine(id).first()).name)
    }

    @Test
    fun `adding a starter in English stores its English name`() = runTest {
        val explicit = machines.addStarter(starter("Barbell bench press"), portuguese = false)
        val byDefault = machines.addStarter(starter("Incline barbell bench press"))

        assertEquals("Barbell bench press", requireNotNull(machines.observeMachine(explicit).first()).name)
        assertEquals("Incline barbell bench press", requireNotNull(machines.observeMachine(byDefault).first()).name)
    }

    @Test
    fun `adding a starter unarchives a stored machine that has its Portuguese name and returns its id`() = runTest {
        val stored = save("Supino reto", MuscleGroup.CHEST, archived = true)

        val returned = machines.addStarter(starter("Barbell bench press"))

        assertEquals(stored, returned)
        assertFalse(requireNotNull(machines.observeMachine(stored).first()).archived)
        assertEquals(1, machines.observeMachines(includeArchived = true).first().size)
    }

    @Test
    fun `adding a starter unarchives a stored machine that has its English name and returns its id`() = runTest {
        val stored = save("Barbell bench press", MuscleGroup.CHEST, archived = true)

        val returned = machines.addStarter(starter("Barbell bench press"), portuguese = true)

        assertEquals(stored, returned)
        assertFalse(requireNotNull(machines.observeMachine(stored).first()).archived)
    }

    @Test
    fun `addStarters adds each one and reuses the machines already stored`() = runTest {
        val press = save("Leg press", MuscleGroup.LEGS)

        val ids = machines.addStarters(listOf(starter("Leg press"), starter("Barbell bench press")), portuguese = true)

        assertEquals(2, ids.size)
        assertEquals(press, ids[0])
        assertEquals("Supino reto", requireNotNull(machines.observeMachine(ids[1]).first()).name)
        assertEquals(2, machines.observeMachines(includeArchived = true).first().size)
    }

    @Test
    fun `addStarters with nothing to add gives nothing`() = runTest {
        assertEquals(emptyList<Long>(), machines.addStarters(emptyList()))
    }

    @Test
    fun `a starter whose Portuguese name is stored is left out of the starters not added`() = runTest {
        save("Supino reto", MuscleGroup.CHEST)

        val names = machines.observeStartersNotAdded().first().map { it.name }

        assertFalse(names.contains("Barbell bench press"))
    }

    @Test
    fun `an archived machine with a starter's name still leaves the starter out`() = runTest {
        save("Supino reto", MuscleGroup.CHEST, archived = true)

        val names = machines.observeStartersNotAdded().first().map { it.name }

        assertFalse(names.contains("Barbell bench press"))
    }

    @Test
    fun `a starter is offered again once its machine is deleted`() = runTest {
        val id = machines.addStarter(starter("Barbell bench press"), portuguese = true)
        assertFalse(machines.observeStartersNotAdded().first().map { it.name }.contains("Barbell bench press"))

        assertTrue(machines.delete(id))

        assertTrue(machines.observeStartersNotAdded().first().map { it.name }.contains("Barbell bench press"))
    }
}

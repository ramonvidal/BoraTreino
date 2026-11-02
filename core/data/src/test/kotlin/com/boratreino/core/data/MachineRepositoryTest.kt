package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
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
}

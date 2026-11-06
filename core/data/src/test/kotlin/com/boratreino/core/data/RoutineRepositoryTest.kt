package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.SetDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

class RoutineRepositoryTest {
    private val t0: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var routines: RoutineRepository
    private lateinit var training: TrainingRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(t0)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private fun routine(
        name: String,
        shortName: String,
        weekdays: Set<DayOfWeek> = emptySet(),
    ) = Routine(id = 0, planId = 0, name = name, shortName = shortName, position = 0, weekdays = weekdays)

    private fun item(machineId: Long, targetSets: Int = 3, repsMin: Int = 8, repsMax: Int = 12, position: Int = 0) =
        RoutineItem(routineId = 0, machineId = machineId, position = position, targetSets = targetSets, repsMin = repsMin, repsMax = repsMax)

    /** Saves a routine with a machine per item; returns its id. */
    private suspend fun save(name: String, shortName: String, vararg machineIds: Long): Long =
        routines.saveRoutine(routine(name, shortName), machineIds.map { item(it) })

    /** The routine as the editor holds it: its stored plan and position, so an update keeps them. */
    private suspend fun stored(id: Long): Routine = requireNotNull(routines.observeRoutine(id).first()).routine

    private suspend fun orderedShortNames(): List<String> =
        routines.observePlan().first().routines.map { it.routine.shortName }

    private suspend fun positionsOf(): List<Pair<String, Int>> =
        routines.observePlan().first().routines.map { it.routine.shortName to it.routine.position }

    // Plan

    @Test
    fun `the plan is created on first use, in turn and with no routines`() = runTest {
        val plan = routines.observePlan().first()

        assertEquals(ScheduleMode.IN_TURN, plan.plan.scheduleMode)
        assertEquals(emptyList<String>(), orderedShortNames())
    }

    @Test
    fun `there is only ever one plan`() = runTest {
        val first = routines.observePlan().first().plan.id
        val second = routines.observePlan().first().plan.id

        assertEquals(first, second)
        assertEquals(first, store.plan()?.id)
    }

    @Test
    fun `the schedule mode can be switched to weekdays and back`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        assertEquals(ScheduleMode.WEEKDAYS, routines.observePlan().first().plan.scheduleMode)

        routines.setScheduleMode(ScheduleMode.IN_TURN)
        assertEquals(ScheduleMode.IN_TURN, routines.observePlan().first().plan.scheduleMode)
    }

    // Training days

    @Test
    fun `the training days are empty, every day, on the first plan`() = runTest {
        assertEquals(emptySet<DayOfWeek>(), routines.observePlan().first().plan.trainingDays)
    }

    @Test
    fun `setting the training days persists them on the plan`() = runTest {
        routines.setTrainingDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))

        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
            routines.observePlan().first().plan.trainingDays,
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY), store.plan()?.trainingDays)
    }

    @Test
    fun `the training days can be changed and cleared, and the schedule mode is kept`() = runTest {
        routines.setScheduleMode(ScheduleMode.IN_TURN)
        routines.setTrainingDays(setOf(DayOfWeek.FRIDAY))
        routines.setTrainingDays(setOf(DayOfWeek.TUESDAY))

        assertEquals(setOf(DayOfWeek.TUESDAY), routines.observePlan().first().plan.trainingDays)

        routines.setTrainingDays(emptySet())

        val plan = routines.observePlan().first().plan
        assertEquals(emptySet<DayOfWeek>(), plan.trainingDays)
        assertEquals(ScheduleMode.IN_TURN, plan.scheduleMode)
    }

    @Test
    fun `the training days do not change the routines or their order`() = runTest {
        save("Routine A", "A")
        save("Routine B", "B")

        routines.setTrainingDays(setOf(DayOfWeek.MONDAY))

        assertEquals(listOf("A", "B"), orderedShortNames())
    }

    // saveRoutine: insert

    @Test
    fun `a new routine goes to the end and gets the next position`() = runTest {
        save("Routine A", "A")
        save("Routine B", "B")
        save("Routine C", "C")

        assertEquals(listOf("A", "B", "C"), orderedShortNames())
        assertEquals(listOf("A" to 0, "B" to 1, "C" to 2), positionsOf())
    }

    @Test
    fun `saving a new routine returns a new id that the plan lists`() = runTest {
        val id = save("Routine A", "A")

        assertTrue(id > 0)
        assertEquals(id, routines.observePlan().first().routines.single().routine.id)
    }

    @Test
    fun `a new routine stores its name and short name trimmed`() = runTest {
        val id = routines.saveRoutine(routine("  Legs  ", " A "), emptyList())

        val stored = routines.observePlan().first().routines.single { it.routine.id == id }.routine
        assertEquals("Legs", stored.name)
        assertEquals("A", stored.shortName)
    }

    @Test
    fun `a new routine keeps the weekdays it was given`() = runTest {
        val id = routines.saveRoutine(
            routine("Routine A", "A", setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)),
            emptyList(),
        )

        assertEquals(
            setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
            routines.observeRoutine(id).first()?.routine?.weekdays,
        )
    }

    @Test
    fun `a new routine's machines are stored in list order with positions from 0`() = runTest {
        val press = machine("Leg press")
        val extension = machine("Leg extension")
        val curl = machine("Lying leg curl")
        val id = routines.saveRoutine(
            routine("Routine A", "A"),
            listOf(
                item(curl, position = 9),
                item(press, targetSets = 4, repsMin = 6, repsMax = 10, position = 1),
                item(extension, position = 5),
            ),
        )

        val stored = routines.observeRoutine(id).first()!!.items

        assertEquals(listOf(curl, press, extension), stored.map { it.machineId })
        assertEquals(listOf(0, 1, 2), stored.map { it.position })
        assertEquals(4, stored[1].targetSets)
        assertEquals(6, stored[1].repsMin)
        assertEquals(10, stored[1].repsMax)
        assertTrue(stored.all { it.routineId == id })
    }

    @Test
    fun `a routine added after a deletion goes to the end, not into the gap`() = runTest {
        save("Routine A", "A")
        val b = save("Routine B", "B")
        save("Routine C", "C")
        routines.deleteRoutine(b)

        save("Routine D", "D")

        assertEquals(listOf("A" to 0, "C" to 1, "D" to 2), positionsOf())
    }

    // saveRoutine: update

    @Test
    fun `saving an existing routine updates it and returns the same id`() = runTest {
        val press = machine("Leg press")
        val id = save("Routine A", "A", press)
        val before = routines.observeRoutine(id).first()!!

        val returned = routines.saveRoutine(
            before.routine.copy(name = "Legs heavy", shortName = "H"),
            before.items,
        )

        assertEquals(id, returned)
        val stored = routines.observePlan().first().routines.single().routine
        assertEquals("Legs heavy", stored.name)
        assertEquals("H", stored.shortName)
        assertEquals(0, stored.position)
        assertEquals(1, routines.observePlan().first().routines.size)
    }

    @Test
    fun `saving an existing routine replaces its machines in the order given`() = runTest {
        val press = machine("Leg press")
        val row = machine("Seated row")
        val curl = machine("Biceps curl machine")
        val id = save("Routine A", "A", press, row)

        routines.saveRoutine(
            stored(id),
            listOf(item(row, position = 0), item(press, position = 1), item(curl, position = 2)),
        )

        val stored = routines.observeRoutine(id).first()!!.items
        assertEquals(listOf(row, press, curl), stored.map { it.machineId })
        assertEquals(listOf(0, 1, 2), stored.map { it.position })
    }

    @Test
    fun `saving an existing routine with fewer machines removes the rest`() = runTest {
        val press = machine("Leg press")
        val row = machine("Seated row")
        val id = save("Routine A", "A", press, row)

        routines.saveRoutine(stored(id), listOf(item(press)))

        assertEquals(listOf(press), routines.observeRoutine(id).first()!!.items.map { it.machineId })
    }

    @Test
    fun `saving an existing routine with no machines leaves it empty`() = runTest {
        val id = save("Routine A", "A", machine("Leg press"))

        routines.saveRoutine(stored(id), emptyList())

        assertEquals(emptyList<RoutineItem>(), routines.observeRoutine(id).first()!!.items)
    }

    // moveRoutine

    @Test
    fun `moving a routine up swaps it with the one above`() = runTest {
        save("Routine A", "A")
        val b = save("Routine B", "B")
        save("Routine C", "C")

        routines.moveRoutine(b, up = true)

        assertEquals(listOf("B", "A", "C"), orderedShortNames())
    }

    @Test
    fun `moving a routine down swaps it with the one below`() = runTest {
        val a = save("Routine A", "A")
        save("Routine B", "B")
        save("Routine C", "C")

        routines.moveRoutine(a, up = false)

        assertEquals(listOf("B", "A", "C"), orderedShortNames())
    }

    @Test
    fun `moving routines keeps positions from 0 to n minus 1`() = runTest {
        val a = save("Routine A", "A")
        save("Routine B", "B")
        val c = save("Routine C", "C")

        routines.moveRoutine(c, up = true)
        routines.moveRoutine(a, up = false)

        assertEquals(listOf("C" to 0, "A" to 1, "B" to 2), positionsOf())
    }

    @Test
    fun `moving the first routine up does nothing`() = runTest {
        val a = save("Routine A", "A")
        save("Routine B", "B")

        routines.moveRoutine(a, up = true)

        assertEquals(listOf("A", "B"), orderedShortNames())
    }

    @Test
    fun `moving the last routine down does nothing`() = runTest {
        save("Routine A", "A")
        val b = save("Routine B", "B")

        routines.moveRoutine(b, up = false)

        assertEquals(listOf("A", "B"), orderedShortNames())
    }

    // moveItem

    private suspend fun machineIdsOf(routineId: Long): List<Long> =
        requireNotNull(routines.observeRoutine(routineId).first()).items.map { it.machineId }

    @Test
    fun `moving a machine up swaps it with the one above and persists positions`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val c = machine("Pull-ups")
        val id = save("Treino A", "A", a, b, c)

        routines.moveItem(id, b, up = true)

        assertEquals(listOf(b, a, c), machineIdsOf(id))
        assertEquals(listOf(0, 1, 2), requireNotNull(routines.observeRoutine(id).first()).items.map { it.position })
    }

    @Test
    fun `moving a machine down swaps it with the one below`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val c = machine("Pull-ups")
        val id = save("Treino A", "A", a, b, c)

        routines.moveItem(id, b, up = false)

        assertEquals(listOf(a, c, b), machineIdsOf(id))
    }

    @Test
    fun `moving the first machine up or the last down does nothing`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val id = save("Treino A", "A", a, b)

        routines.moveItem(id, a, up = true)
        routines.moveItem(id, b, up = false)

        assertEquals(listOf(a, b), machineIdsOf(id))
    }

    @Test
    fun `moving a machine keeps its target and leaves other routines alone`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val id = routines.saveRoutine(routine("Treino A", "A"), listOf(item(a, 4, 6, 10), item(b, 2, 12, 15)))
        val other = save("Treino B", "B", a, b)

        routines.moveItem(id, b, up = true)

        val moved = requireNotNull(routines.observeRoutine(id).first()).items
        assertEquals(listOf(b, a), moved.map { it.machineId })
        assertEquals(listOf(2, 4), moved.map { it.targetSets })
        assertEquals(listOf(12, 6), moved.map { it.repsMin })
        assertEquals(listOf(a, b), machineIdsOf(other))
    }

    @Test
    fun `moving a machine that is not in the routine does nothing`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val id = save("Treino A", "A", a)

        routines.moveItem(id, b, up = true)

        assertEquals(listOf(a), machineIdsOf(id))
    }

    // duplicateRoutine

    @Test
    fun `duplicating a routine adds a copy with the same machines at the next position`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val id = routines.saveRoutine(
            routine("Treino A", "A", setOf(DayOfWeek.MONDAY)),
            listOf(item(a, 4, 6, 10), item(b, 2, 12, 15)),
        )
        save("Treino B", "B")

        val copyId = routines.duplicateRoutine(id)

        val copy = requireNotNull(routines.observeRoutine(copyId).first())
        assertTrue(copyId != id && copyId != 0L)
        assertEquals("Treino A copy", copy.routine.name)
        assertEquals(2, copy.routine.position)
        assertEquals(listOf(a, b), copy.items.map { it.machineId })
        assertEquals(listOf(4, 2), copy.items.map { it.targetSets })
        assertEquals(listOf(6, 12), copy.items.map { it.repsMin })
        assertEquals(listOf(10, 15), copy.items.map { it.repsMax })
        assertEquals(listOf(0, 1), copy.items.map { it.position })
        assertEquals(copyId, copy.routine.id)
        assertEquals(3, routines.observePlan().first().routines.size)
    }

    @Test
    fun `duplicating leaves the original untouched`() = runTest {
        val a = machine("Leg press")
        val id = save("Treino A", "A", a)

        routines.duplicateRoutine(id)

        val original = requireNotNull(routines.observeRoutine(id).first())
        assertEquals("Treino A", original.routine.name)
        assertEquals(listOf(a), original.items.map { it.machineId })
        assertEquals(0, original.routine.position)
    }

    @Test
    fun `duplicating a routine that does not exist returns 0 and adds nothing`() = runTest {
        save("Treino A", "A")

        assertEquals(0L, routines.duplicateRoutine(999L))
        assertEquals(1, routines.observePlan().first().routines.size)
    }

    // createRoutine

    @Test
    fun `creating a routine on an empty plan gives Routine A with no machines`() = runTest {
        val id = routines.createRoutine()

        val created = requireNotNull(routines.observeRoutine(id).first())
        assertEquals("Routine A", created.routine.name)
        assertEquals("A", created.routine.shortName)
        assertEquals(0, created.routine.position)
        assertEquals(emptyList<RoutineItem>(), created.items)
    }

    @Test
    fun `creating routines takes the next unused letter and the next position`() = runTest {
        save("Treino A", "A")
        save("Treino B", "B")

        val id = routines.createRoutine()

        val created = requireNotNull(routines.observeRoutine(id).first()).routine
        assertEquals("Routine C", created.name)
        assertEquals("C", created.shortName)
        assertEquals(2, created.position)
    }

    @Test
    fun `creating a routine fills a free letter and ignores case`() = runTest {
        save("Treino A", "a")
        save("Treino C", "C")

        val id = routines.createRoutine()

        assertEquals("B", requireNotNull(routines.observeRoutine(id).first()).routine.shortName)
        assertEquals("Routine B", requireNotNull(routines.observeRoutine(id).first()).routine.name)
    }

    @Test
    fun `creating two routines in a row gives two letters`() = runTest {
        val first = routines.createRoutine()
        val second = routines.createRoutine()

        assertEquals(listOf("A", "B"), listOf(first, second).map { requireNotNull(routines.observeRoutine(it).first()).routine.shortName })
        assertEquals(listOf("A" to 0, "B" to 1), positionsOf())
    }

    // deleteRoutine

    @Test
    fun `deleting a routine renumbers the rest from 0`() = runTest {
        save("Routine A", "A")
        val b = save("Routine B", "B")
        save("Routine C", "C")

        routines.deleteRoutine(b)

        assertEquals(listOf("A" to 0, "C" to 1), positionsOf())
    }

    @Test
    fun `deleting a routine removes it and its machines`() = runTest {
        val press = machine("Leg press")
        val id = save("Routine A", "A", press)

        routines.deleteRoutine(id)

        assertNull(routines.observeRoutine(id).first())
        assertEquals(emptyList<RoutineItem>(), store.allRoutineItems())
        assertEquals(emptyList<String>(), orderedShortNames())
    }

    @Test
    fun `deleting a routine keeps its sessions and their sets, with no routine`() = runTest {
        val press = machine("Leg press")
        val id = save("Routine A", "A", press)
        training.chooseRoutine(RoutineChoice.Chosen(id))
        training.logSet(press, SetDraft(120.0, 12))
        training.finishSession()

        routines.deleteRoutine(id)

        assertEquals(1, store.allSessions().size)
        assertNull(store.allSessions().single().routineId)
        assertEquals(1, store.allSets().size)
    }

    @Test
    fun `deleting the routine of an open session clears its routine but not the session`() = runTest {
        val press = machine("Leg press")
        val id = save("Routine A", "A", press)
        training.chooseRoutine(RoutineChoice.Chosen(id))
        training.logSet(press, SetDraft(120.0, 12))

        routines.deleteRoutine(id)

        val open = requireNotNull(training.observeOpenSession().first())
        assertNull(open.session.routineId)
        assertEquals(1, open.machines.single().sets.size)
    }

    @Test
    fun `deleting the only routine leaves none`() = runTest {
        val id = save("Routine A", "A")

        routines.deleteRoutine(id)

        assertEquals(emptyList<String>(), orderedShortNames())
        assertTrue(routines.observePlan().first().routines.isEmpty())
    }

    @Test
    fun `saving an existing routine keeps its stored plan and position whatever the caller passes`() = runTest {
        save("Routine A", "A")
        val b = save("Routine B", "B")
        val planId = routines.observePlan().first().plan.id

        routines.saveRoutine(
            Routine(id = b, planId = 0L, name = "Routine B2", shortName = "B", position = 99, weekdays = emptySet()),
            emptyList(),
        )

        val stored = stored(b)
        assertEquals(planId, stored.planId)
        assertEquals(1, stored.position)
        assertEquals("Routine B2", stored.name)
        assertEquals(listOf("A" to 0, "B" to 1), positionsOf())
        assertTrue(routines.observePlan().first().routines.any { it.routine.id == b })
    }

    @Test
    fun `saving a routine with an id that is not stored throws and changes nothing`() = runTest {
        save("Routine A", "A")

        try {
            routines.saveRoutine(
                Routine(id = 999L, planId = 1L, name = "Ghost", shortName = "G", position = 0, weekdays = emptySet()),
                emptyList(),
            )
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }

        assertEquals(listOf("A"), orderedShortNames())
    }

    // addMachine, removeMachine, restoreItem

    @Test
    fun `adding a machine appends it with the default target`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val id = save("Treino A", "A", a)

        routines.addMachine(id, b)

        val items = requireNotNull(routines.observeRoutine(id).first()).items
        assertEquals(listOf(a, b), items.map { it.machineId })
        assertEquals(listOf(0, 1), items.map { it.position })
        assertEquals(3, items.last().targetSets)
        assertEquals(8, items.last().repsMin)
        assertEquals(12, items.last().repsMax)
    }

    @Test
    fun `adding a machine that is already there changes nothing`() = runTest {
        val a = machine("Leg press")
        val id = routines.saveRoutine(routine("Treino A", "A"), listOf(item(a, targetSets = 5, repsMin = 6, repsMax = 10)))
        val before = requireNotNull(routines.observeRoutine(id).first()).items

        routines.addMachine(id, a)

        assertEquals(before, requireNotNull(routines.observeRoutine(id).first()).items)
    }

    @Test
    fun `adding a machine to a routine that is gone does nothing`() = runTest {
        val a = machine("Leg press")

        routines.addMachine(999L, a)

        assertEquals(emptyList<String>(), orderedShortNames())
        assertNull(routines.observeRoutine(999L).first())
    }

    @Test
    fun `removing a machine returns its item and renumbers the rest`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val c = machine("Leg extension")
        val id = save("Treino A", "A", a, b, c)

        val removed = routines.removeMachine(id, b)

        assertEquals(RoutineItem(id, b, 1, 3, 8, 12), removed)
        val items = requireNotNull(routines.observeRoutine(id).first()).items
        assertEquals(listOf(a, c), items.map { it.machineId })
        assertEquals(listOf(0, 1), items.map { it.position })
    }

    @Test
    fun `removing a machine that is not in the routine returns null and changes nothing`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val id = save("Treino A", "A", a)

        assertNull(routines.removeMachine(id, b))

        assertEquals(listOf(a), requireNotNull(routines.observeRoutine(id).first()).items.map { it.machineId })
    }

    @Test
    fun `restoring a removed item puts it back at its position with its target`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val c = machine("Leg extension")
        val id = routines.saveRoutine(
            routine("Treino A", "A"),
            listOf(item(a), item(b, targetSets = 5, repsMin = 6, repsMax = 10), item(c)),
        )
        val removed = requireNotNull(routines.removeMachine(id, b))

        routines.restoreItem(removed)

        val items = requireNotNull(routines.observeRoutine(id).first()).items
        assertEquals(listOf(a, b, c), items.map { it.machineId })
        assertEquals(listOf(0, 1, 2), items.map { it.position })
        assertEquals(5, items[1].targetSets)
        assertEquals(6, items[1].repsMin)
        assertEquals(10, items[1].repsMax)
    }

    @Test
    fun `restoring an item does nothing when its routine is gone`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val id = save("Treino A", "A", a, b)
        val removed = requireNotNull(routines.removeMachine(id, b))
        routines.deleteRoutine(id)

        routines.restoreItem(removed)

        assertEquals(emptyList<String>(), orderedShortNames())
        assertNull(routines.observeRoutine(id).first())
    }

    @Test
    fun `restoring an item does nothing when its machine is gone`() = runTest {
        val a = machine("Leg press")
        val b = machine("Leg curl")
        val id = save("Treino A", "A", a, b)
        val removed = requireNotNull(routines.removeMachine(id, b))
        assertTrue(machines.delete(b))

        routines.restoreItem(removed)

        assertEquals(listOf(a), requireNotNull(routines.observeRoutine(id).first()).items.map { it.machineId })
    }
}

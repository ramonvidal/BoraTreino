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
}

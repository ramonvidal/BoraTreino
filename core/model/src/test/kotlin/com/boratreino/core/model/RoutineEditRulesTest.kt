package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEditRulesTest {
    private val routine = Routine(id = 7L, planId = 1L, name = "Treino B", shortName = "B", position = 1, weekdays = emptySet())

    private fun item(machineId: Long, position: Int, sets: Int = 3, min: Int = 8, max: Int = 12) =
        RoutineItem(routineId = 7L, machineId = machineId, position = position, targetSets = sets, repsMin = min, repsMax = max)

    /** Leg press (1), Leg curl (2), Pull-ups (3), with the targets the owner set. */
    private val items = listOf(item(1L, 0, 4, 6, 10), item(2L, 1, 3, 10, 15), item(3L, 2))

    // contains

    @Test
    fun `contains is true for a machine in the list`() {
        assertTrue(RoutineEditRules.contains(items, 2L))
    }

    @Test
    fun `contains is false for a machine not in the list`() {
        assertFalse(RoutineEditRules.contains(items, 9L))
        assertFalse(RoutineEditRules.contains(emptyList(), 1L))
    }

    // withMachine

    @Test
    fun `withMachine appends the machine with the default target`() {
        val result = RoutineEditRules.withMachine(7L, items, 9L)

        val added = result.last()
        assertEquals(4, result.size)
        assertEquals(9L, added.machineId)
        assertEquals(7L, added.routineId)
        assertEquals(3, added.targetSets)
        assertEquals(8, added.repsMin)
        assertEquals(12, added.repsMax)
    }

    @Test
    fun `withMachine gives the default target constants`() {
        assertEquals(3, RoutineEditRules.DEFAULT_SETS)
        assertEquals(8, RoutineEditRules.DEFAULT_REPS_MIN)
        assertEquals(12, RoutineEditRules.DEFAULT_REPS_MAX)
    }

    @Test
    fun `withMachine keeps the other items and their targets`() {
        val result = RoutineEditRules.withMachine(7L, items, 9L)

        assertEquals(items, result.take(3))
    }

    @Test
    fun `withMachine keeps positions 0 to n-1 in list order`() {
        val result = RoutineEditRules.withMachine(7L, items, 9L)

        assertEquals(listOf(0, 1, 2, 3), result.map { it.position })
    }

    @Test
    fun `withMachine does nothing when the machine is already there`() {
        assertEquals(items, RoutineEditRules.withMachine(7L, items, 2L))
    }

    @Test
    fun `withMachine on an empty routine gives position 0`() {
        val result = RoutineEditRules.withMachine(7L, emptyList(), 5L)

        assertEquals(listOf(0), result.map { it.position })
        assertEquals(5L, result.single().machineId)
    }

    // withoutMachine

    @Test
    fun `withoutMachine removes the machine and renumbers the rest`() {
        val result = RoutineEditRules.withoutMachine(items, 1L)

        assertEquals(listOf(2L, 3L), result.map { it.machineId })
        assertEquals(listOf(0, 1), result.map { it.position })
    }

    @Test
    fun `withoutMachine keeps the targets of the items it leaves`() {
        val result = RoutineEditRules.withoutMachine(items, 1L)

        assertEquals(item(2L, 0, 3, 10, 15), result.first())
    }

    @Test
    fun `withoutMachine does nothing when the machine is absent`() {
        assertEquals(items, RoutineEditRules.withoutMachine(items, 9L))
    }

    // withItemBack

    @Test
    fun `withItemBack puts the item back at its own position`() {
        val removed = items[1]
        val without = RoutineEditRules.withoutMachine(items, removed.machineId)

        val restored = RoutineEditRules.withItemBack(without, removed)

        assertEquals(items.map { it.machineId }, restored.map { it.machineId })
        assertEquals(listOf(0, 1, 2), restored.map { it.position })
        assertEquals(removed, restored[1])
    }

    @Test
    fun `withItemBack clamps a position past the end to the end`() {
        val back = item(9L, 42)

        val result = RoutineEditRules.withItemBack(items, back)

        assertEquals(9L, result.last().machineId)
        assertEquals(listOf(0, 1, 2, 3), result.map { it.position })
    }

    @Test
    fun `withItemBack clamps a negative position to the start`() {
        val back = item(9L, -5)

        val result = RoutineEditRules.withItemBack(items, back)

        assertEquals(9L, result.first().machineId)
        assertEquals(listOf(0, 1, 2, 3), result.map { it.position })
    }

    @Test
    fun `withItemBack does nothing when its machine is already in the routine`() {
        assertEquals(items, RoutineEditRules.withItemBack(items, item(2L, 0, 5, 1, 2)))
    }

    // moved

    @Test
    fun `moved up swaps the machine with the one above and renumbers`() {
        val result = RoutineEditRules.moved(items, 2L, up = true)

        assertEquals(listOf(2L, 1L, 3L), result.map { it.machineId })
        assertEquals(listOf(0, 1, 2), result.map { it.position })
    }

    @Test
    fun `moved down swaps the machine with the one below and renumbers`() {
        val result = RoutineEditRules.moved(items, 2L, up = false)

        assertEquals(listOf(1L, 3L, 2L), result.map { it.machineId })
        assertEquals(listOf(0, 1, 2), result.map { it.position })
    }

    @Test
    fun `moved keeps the targets of the machines`() {
        val result = RoutineEditRules.moved(items, 1L, up = false)

        assertEquals(item(2L, 0, 3, 10, 15), result[0])
        assertEquals(item(1L, 1, 4, 6, 10), result[1])
    }

    @Test
    fun `moving the first machine up changes nothing`() {
        assertEquals(items, RoutineEditRules.moved(items, 1L, up = true))
    }

    @Test
    fun `moving the last machine down changes nothing`() {
        assertEquals(items, RoutineEditRules.moved(items, 3L, up = false))
    }

    @Test
    fun `moving an absent machine changes nothing`() {
        assertEquals(items, RoutineEditRules.moved(items, 9L, up = true))
        assertEquals(items, RoutineEditRules.moved(items, 9L, up = false))
        assertEquals(emptyList<RoutineItem>(), RoutineEditRules.moved(emptyList(), 1L, up = true))
    }

    @Test
    fun `moved renumbers positions that had gaps`() {
        val gappy = listOf(item(1L, 0), item(2L, 5), item(3L, 9))

        val result = RoutineEditRules.moved(gappy, 3L, up = true)

        assertEquals(listOf(1L, 3L, 2L), result.map { it.machineId })
        assertEquals(listOf(0, 1, 2), result.map { it.position })
    }

    // nextLetter

    @Test
    fun `nextLetter starts at A`() {
        assertEquals("A", RoutineEditRules.nextLetter(emptyList()))
    }

    @Test
    fun `nextLetter takes the first unused letter`() {
        assertEquals("C", RoutineEditRules.nextLetter(listOf("A", "B")))
        assertEquals("B", RoutineEditRules.nextLetter(listOf("A", "C")))
        assertEquals("A", RoutineEditRules.nextLetter(listOf("B", "C")))
    }

    @Test
    fun `nextLetter ignores case and spaces of the used names`() {
        assertEquals("C", RoutineEditRules.nextLetter(listOf("a", " b ")))
    }

    @Test
    fun `nextLetter ignores names that are not letters`() {
        assertEquals("A", RoutineEditRules.nextLetter(listOf("Push", "")))
    }

    @Test
    fun `nextLetter goes to A1 after Z`() {
        val all = ('A'..'Z').map { it.toString() }

        assertEquals("A1", RoutineEditRules.nextLetter(all))
        assertEquals("B1", RoutineEditRules.nextLetter(all + "A1"))
    }

    // shouldOfferAdding

    private fun ficha(vararg machineIds: Long) = RoutineWithItems(
        routine = routine,
        items = machineIds.mapIndexed { i, id -> item(id, i) },
    )

    @Test
    fun `no active ficha means no question`() {
        assertFalse(RoutineEditRules.shouldOfferAdding(active = null, skipped = emptySet(), machineId = 9L))
    }

    @Test
    fun `a machine already in the active ficha is not offered`() {
        assertFalse(RoutineEditRules.shouldOfferAdding(ficha(1L, 2L), emptySet(), 2L))
    }

    @Test
    fun `a machine skipped today is not offered`() {
        assertFalse(RoutineEditRules.shouldOfferAdding(ficha(1L, 2L), setOf(9L), 9L))
    }

    @Test
    fun `a machine that is neither in the ficha nor skipped is offered`() {
        assertTrue(RoutineEditRules.shouldOfferAdding(ficha(1L, 2L), setOf(5L), 9L))
    }

    @Test
    fun `an empty active ficha offers any machine`() {
        assertTrue(RoutineEditRules.shouldOfferAdding(ficha(), emptySet(), 9L))
    }
}

/** The routine without the given machines; the positions of the rest stay as they were. */
class RoutineWithItemsWithoutMachinesTest {
    private val routine = Routine(id = 7L, planId = 1L, name = "Treino B", shortName = "B", position = 1, weekdays = emptySet())

    @Test
    fun `withoutMachines keeps the remaining items and their positions`() {
        val full = RoutineWithItems(
            routine,
            listOf(
                RoutineItem(7L, 1L, 0, 3, 8, 12),
                RoutineItem(7L, 2L, 1, 3, 8, 12),
                RoutineItem(7L, 3L, 2, 4, 6, 10),
            ),
        )

        val result = full.withoutMachines(setOf(2L))

        assertEquals(routine, result.routine)
        assertEquals(listOf(1L, 3L), result.items.map { it.machineId })
        assertEquals(listOf(0, 2), result.items.map { it.position })
    }

    @Test
    fun `withoutMachines with no ids changes nothing`() {
        val full = RoutineWithItems(routine, listOf(RoutineItem(7L, 1L, 0, 3, 8, 12)))

        assertEquals(full, full.withoutMachines(emptySet()))
    }
}

class MachineUsageTest {
    @Test
    fun `a machine with no logged sets can be deleted`() {
        assertTrue(MachineUsage(setCount = 0, routineNames = listOf("Treino A")).canDelete)
    }

    @Test
    fun `a machine with logged sets cannot be deleted`() {
        assertFalse(MachineUsage(setCount = 1, routineNames = emptyList()).canDelete)
    }
}

class TodayPlanSkippedTest {
    @Test
    fun `nothing is skipped unless said so`() {
        val plan = TodayPlan(
            mode = ScheduleMode.IN_TURN,
            routines = emptyList(),
            suggested = null,
            choice = RoutineChoice.Suggested,
            active = null,
            restDay = false,
        )

        assertEquals(emptySet<Long>(), plan.skipped)
    }
}

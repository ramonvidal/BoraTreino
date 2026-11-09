package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RoutineUpdateRulesTest {
    private var nextSetId = 1L

    private fun item(machineId: Long, targetSets: Int, position: Int = 0, repsMin: Int = 8, repsMax: Int = 12) =
        RoutineItem(routineId = 1, machineId = machineId, position = position, targetSets = targetSets, repsMin = repsMin, repsMax = repsMax)

    /** A strength set on [machineId], logged [at] seconds after the epoch. */
    private fun set(machineId: Long, at: Long, type: SetType = SetType.NORMAL) = WorkoutSet(
        id = nextSetId++, sessionId = 1, machineId = machineId, position = 0,
        weightKg = 20.0, reps = 10, seconds = null, calories = null, distanceM = null,
        loggedAt = Instant.ofEpochSecond(at), type = type,
    )

    private fun sets(machineId: Long, count: Int, startAt: Long = 0, type: SetType = SetType.NORMAL) =
        (0 until count).map { set(machineId, startAt + it, type) }

    /** A cardio set (time, calories or distance): it has seconds, not weight and reps. */
    private fun cardio(machineId: Long, at: Long) = WorkoutSet(
        id = nextSetId++, sessionId = 1, machineId = machineId, position = 0,
        weightKg = null, reps = null, seconds = 600, calories = null, distanceM = null,
        loggedAt = Instant.ofEpochSecond(at),
    )

    // What changes

    @Test
    fun `a routine item trained with a different number of sets gets that number`() {
        val update = RoutineUpdateRules.plan(listOf(item(1, targetSets = 3)), sets(1, 4))

        assertEquals(listOf(SetsChange(1, from = 3, to = 4)), update?.setChanges)
        assertTrue(update!!.additions.isEmpty())
    }

    @Test
    fun `fewer sets than the target lowers the target`() {
        val update = RoutineUpdateRules.plan(listOf(item(1, targetSets = 4)), sets(1, 2))

        assertEquals(listOf(SetsChange(1, from = 4, to = 2)), update?.setChanges)
    }

    @Test
    fun `the new target is kept between 1 and 10`() {
        val many = RoutineUpdateRules.plan(listOf(item(1, targetSets = 3)), sets(1, 12))
        assertEquals(listOf(SetsChange(1, from = 3, to = 10)), many?.setChanges)

        val one = RoutineUpdateRules.plan(listOf(item(1, targetSets = 3)), sets(1, 1))
        assertEquals(listOf(SetsChange(1, from = 3, to = 1)), one?.setChanges)
    }

    @Test
    fun `a target already equal to the sets done is not a change`() {
        assertNull(RoutineUpdateRules.plan(listOf(item(1, targetSets = 3)), sets(1, 3)))
    }

    @Test
    fun `warm-up sets never count`() {
        val session = sets(1, 2) + sets(1, 3, startAt = 10, type = SetType.WARMUP)

        assertNull(RoutineUpdateRules.plan(listOf(item(1, targetSets = 2)), session))
        assertEquals(
            listOf(SetsChange(1, from = 2, to = 3)),
            RoutineUpdateRules.plan(listOf(item(1, targetSets = 2)), session + sets(1, 1, startAt = 20))?.setChanges,
        )
    }

    @Test
    fun `a machine done only with warm-ups is not added and its item is left alone`() {
        val update = RoutineUpdateRules.plan(
            listOf(item(1, targetSets = 3)),
            sets(1, 3, type = SetType.WARMUP) + sets(2, 2, type = SetType.WARMUP),
        )

        assertNull(update)
    }

    @Test
    fun `drop and failure sets count as working sets`() {
        val session = listOf(
            set(1, 0),
            set(1, 1, SetType.DROP),
            set(1, 2, SetType.FAILURE),
        )

        assertEquals(listOf(SetsChange(1, from = 2, to = 3)), RoutineUpdateRules.plan(listOf(item(1, targetSets = 2)), session)?.setChanges)
    }

    @Test
    fun `a routine item with no sets in the session is left alone`() {
        val update = RoutineUpdateRules.plan(listOf(item(1, targetSets = 3), item(2, targetSets = 5, position = 1)), sets(1, 3))

        assertNull(update)
    }

    @Test
    fun `a routine item is never removed and its rep range never changes`() {
        val update = RoutineUpdateRules.plan(
            listOf(item(1, targetSets = 3, repsMin = 6, repsMax = 8), item(2, targetSets = 3, position = 1, repsMin = 15, repsMax = 20)),
            sets(1, 4),
        )!!
        val applied = RoutineUpdateRules.apply(1, listOf(item(1, targetSets = 3, repsMin = 6, repsMax = 8), item(2, targetSets = 3, position = 1, repsMin = 15, repsMax = 20)), update)

        assertEquals(2, applied.size)
        assertEquals(listOf(6, 15), applied.map { it.repsMin })
        assertEquals(listOf(8, 20), applied.map { it.repsMax })
        assertEquals(listOf(4, 3), applied.map { it.targetSets })
    }

    // Additions

    @Test
    fun `a machine trained but not in the routine is added with the sets done`() {
        val routine = listOf(item(1, targetSets = 3))
        val update = RoutineUpdateRules.plan(routine, sets(1, 3) + sets(7, 2, startAt = 100))

        assertEquals(listOf(Addition(7, 2)), update?.additions)
        assertTrue(update!!.setChanges.isEmpty())
    }

    @Test
    fun `additions are appended in the order they were first done`() {
        val session = sets(9, 1, startAt = 300) + sets(4, 2, startAt = 100) + sets(6, 3, startAt = 200)

        val update = RoutineUpdateRules.plan(emptyList(), session)

        assertEquals(listOf(4L, 6L, 9L), update?.additions?.map { it.machineId })
        assertEquals(listOf(2, 3, 1), update?.additions?.map { it.sets })
    }

    @Test
    fun `an added machine gets the default rep range and the next positions`() {
        val routine = listOf(item(1, targetSets = 3), item(2, targetSets = 2, position = 1))
        val update = RoutineUpdate(emptyList(), listOf(Addition(7, 4)))

        val applied = RoutineUpdateRules.apply(1, routine, update)

        assertEquals(3, applied.size)
        val added = applied.last()
        assertEquals(RoutineItem(1, 7, 2, 4, 8, 12), added)
        assertEquals(listOf(0, 1, 2), applied.map { it.position })
    }

    @Test
    fun `a cardio machine not in the routine is added`() {
        val update = RoutineUpdateRules.plan(listOf(item(1, targetSets = 3)), sets(1, 3) + listOf(cardio(5, 50), cardio(5, 60)))

        assertEquals(listOf(Addition(5, 2)), update?.additions)
    }

    // Cardio

    @Test
    fun `a cardio machine in the routine keeps its target sets`() {
        val routine = listOf(item(5, targetSets = 1))

        assertNull(RoutineUpdateRules.plan(routine, listOf(cardio(5, 10), cardio(5, 20), cardio(5, 30))))
    }

    // Untouched items and apply

    @Test
    fun `items the session did not touch are kept as they were, in order`() {
        val routine = listOf(item(1, targetSets = 3), item(2, targetSets = 4, position = 1, repsMin = 10, repsMax = 15), item(3, targetSets = 2, position = 2))
        val update = RoutineUpdateRules.plan(routine, sets(3, 5))!!

        val applied = RoutineUpdateRules.apply(1, routine, update)

        assertEquals(listOf(1L, 2L, 3L), applied.map { it.machineId })
        assertEquals(item(1, targetSets = 3, position = 0), applied[0])
        assertEquals(item(2, targetSets = 4, position = 1, repsMin = 10, repsMax = 15), applied[1])
        assertEquals(item(3, targetSets = 5, position = 2), applied[2])
    }

    @Test
    fun `the items given to apply are not changed, so they can be kept for undo`() {
        val routine = listOf(item(1, targetSets = 3))
        val before = routine.toList()
        val update = RoutineUpdateRules.plan(routine, sets(1, 6))!!

        val applied = RoutineUpdateRules.apply(1, routine, update)

        assertEquals(before, routine)
        assertEquals(6, applied.single().targetSets)
        assertEquals(listOf(SetsChange(1, from = 3, to = 6)), update.setChanges)
    }

    @Test
    fun `an update with a change reports itself as not empty and nothing as empty`() {
        val update = RoutineUpdateRules.plan(listOf(item(1, targetSets = 3)), sets(1, 4))!!

        assertFalse(update.isEmpty)
        assertTrue(RoutineUpdate(emptyList(), emptyList()).isEmpty)
    }

    @Test
    fun `an empty session or no routine and no sets is no update`() {
        assertNull(RoutineUpdateRules.plan(listOf(item(1, targetSets = 3)), emptyList()))
        assertNull(RoutineUpdateRules.plan(emptyList(), emptyList()))
    }
}

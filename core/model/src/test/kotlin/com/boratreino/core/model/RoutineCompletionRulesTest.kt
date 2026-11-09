package com.boratreino.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RoutineCompletionRulesTest {
    private val t0 = Instant.parse("2026-11-09T18:00:00Z")

    private fun item(machineId: Long, targetSets: Int) =
        RoutineItem(routineId = 1L, machineId = machineId, position = machineId.toInt(), targetSets = targetSets, repsMin = 8, repsMax = 12)

    private fun set(
        id: Long,
        machineId: Long,
        type: SetType = SetType.NORMAL,
        seconds: Int? = null,
    ) = WorkoutSet(
        id = id,
        sessionId = 1L,
        machineId = machineId,
        position = id.toInt(),
        weightKg = if (seconds == null) 40.0 else null,
        reps = if (seconds == null) 10 else null,
        seconds = seconds,
        calories = null,
        distanceM = null,
        loggedAt = t0.plusSeconds(id * 60),
        type = type,
    )

    private fun sets(machineId: Long, count: Int, firstId: Long = machineId * 100) =
        (0 until count).map { set(firstId + it, machineId) }

    @Test
    fun `an empty routine is never complete`() {
        assertFalse(RoutineCompletionRules.isComplete(emptyList(), emptyList(), emptySet()))
        assertFalse(RoutineCompletionRules.isComplete(emptyList(), sets(1L, 3), emptySet()))
    }

    @Test
    fun `complete when every machine has its target sets`() {
        val items = listOf(item(1L, 3), item(2L, 2))
        val logged = sets(1L, 3) + sets(2L, 2)

        assertTrue(RoutineCompletionRules.isComplete(items, logged, emptySet()))
    }

    @Test
    fun `not complete when one machine is one set short`() {
        val items = listOf(item(1L, 3), item(2L, 2))
        val logged = sets(1L, 3) + sets(2L, 1)

        assertFalse(RoutineCompletionRules.isComplete(items, logged, emptySet()))
    }

    @Test
    fun `not complete when a machine has no sets at all`() {
        val items = listOf(item(1L, 3), item(2L, 2))

        assertFalse(RoutineCompletionRules.isComplete(items, sets(1L, 3), emptySet()))
    }

    @Test
    fun `warm-up sets do not count towards the target`() {
        val items = listOf(item(1L, 2))
        val logged = listOf(
            set(1L, 1L, SetType.WARMUP),
            set(2L, 1L, SetType.WARMUP),
            set(3L, 1L, SetType.WARMUP),
            set(4L, 1L),
        )

        assertFalse(RoutineCompletionRules.isComplete(items, logged, emptySet()))
    }

    @Test
    fun `drop and failure sets are working sets and count`() {
        val items = listOf(item(1L, 2))
        val logged = listOf(set(1L, 1L, SetType.DROP), set(2L, 1L, SetType.FAILURE))

        assertTrue(RoutineCompletionRules.isComplete(items, logged, emptySet()))
    }

    @Test
    fun `a skipped machine counts as done without any set`() {
        val items = listOf(item(1L, 3), item(2L, 2))
        val logged = sets(1L, 3)

        assertTrue(RoutineCompletionRules.isComplete(items, logged, setOf(2L)))
    }

    @Test
    fun `a skipped machine does not hide a missing machine that is not skipped`() {
        val items = listOf(item(1L, 3), item(2L, 2), item(3L, 1))
        val logged = sets(1L, 3)

        assertFalse(RoutineCompletionRules.isComplete(items, logged, setOf(2L)))
    }

    @Test
    fun `more sets than the target still complete`() {
        val items = listOf(item(1L, 2))

        assertTrue(RoutineCompletionRules.isComplete(items, sets(1L, 5), emptySet()))
    }

    @Test
    fun `sets of machines outside the routine do not complete it`() {
        val items = listOf(item(1L, 2), item(2L, 1))
        val logged = sets(1L, 2) + sets(9L, 4)

        assertFalse(RoutineCompletionRules.isComplete(items, logged, emptySet()))
    }

    @Test
    fun `cardio sets count by their number of working sets`() {
        val items = listOf(item(7L, 2))
        val logged = listOf(set(1L, 7L, seconds = 600), set(2L, 7L, seconds = 300))

        assertTrue(RoutineCompletionRules.isComplete(items, logged, emptySet()))
    }

    @Test
    fun `one cardio set is not enough for a target of two`() {
        val items = listOf(item(7L, 2))
        val logged = listOf(set(1L, 7L, seconds = 600), set(2L, 7L, SetType.WARMUP, seconds = 60))

        assertFalse(RoutineCompletionRules.isComplete(items, logged, emptySet()))
    }
}

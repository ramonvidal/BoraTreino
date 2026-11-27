package com.boratreino.core.model

import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ProgressionNoRepsTest {
    private val at = Instant.parse("2026-11-20T09:00:00Z")
    private val target = RoutineItem(routineId = 1L, machineId = 5L, position = 0, targetSets = 2, repsMin = 8, repsMax = 12)

    private fun timed(position: Int, weightKg: Double, seconds: Int) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 5L, position = position,
        weightKg = weightKg, reps = null, seconds = seconds, calories = null, distanceM = null, loggedAt = at,
    )

    private fun metres(position: Int, weightKg: Double, distanceM: Int) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 5L, position = position,
        weightKg = weightKg, reps = null, seconds = null, calories = null, distanceM = distanceM, loggedAt = at,
    )

    @Test
    fun `no suggestion for timed sets, even when they would pass the rep range`() {
        val history = listOf(listOf(timed(0, 45.0, 60), timed(1, 45.0, 60)))

        assertNull(ProgressionRules.suggest(target, 2.5, history))
    }

    @Test
    fun `no suggestion when the last two sessions are timed and below the minimum`() {
        val history = listOf(
            listOf(timed(0, 45.0, 5), timed(1, 45.0, 5)),
            listOf(timed(0, 45.0, 5), timed(1, 45.0, 5)),
        )

        assertNull(ProgressionRules.suggest(target, 2.5, history))
    }

    @Test
    fun `no suggestion for distance sets`() {
        val history = listOf(listOf(metres(0, 40.0, 30), metres(1, 40.0, 30)))

        assertNull(ProgressionRules.suggest(target, 2.5, history))
    }
}

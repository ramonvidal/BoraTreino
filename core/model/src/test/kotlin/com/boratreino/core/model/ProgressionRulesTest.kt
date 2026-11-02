package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ProgressionRulesTest {
    private val at = Instant.parse("2026-10-30T18:00:00Z")

    /** Target 3 x 8-12 on the machine with id 1. */
    private val target = RoutineItem(routineId = 1L, machineId = 1L, position = 0, targetSets = 3, repsMin = 8, repsMax = 12)

    private fun set(position: Int, weightKg: Double?, reps: Int?, sessionId: Long = 1L) = WorkoutSet(
        id = sessionId * 100 + position,
        sessionId = sessionId,
        machineId = 1L,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = at,
    )

    /** One session's sets: [reps] per set, all at [weight]. */
    private fun session(weight: Double, vararg reps: Int, sessionId: Long = 1L): List<WorkoutSet> =
        reps.mapIndexed { i, r -> set(i, weight, r, sessionId) }

    // Up

    @Test
    fun `up when every target set reaches the top of the range at one weight`() {
        val history = listOf(session(120.0, 12, 12, 12))

        assertEquals(Suggestion.Up(125.0, 8), ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `up adds one step to the weight and goes back to the bottom of the range`() {
        val history = listOf(session(42.5, 12, 12, 12))

        assertEquals(Suggestion.Up(45.0, 8), ProgressionRules.suggest(target, stepKg = 2.5, history = history))
    }

    @Test
    fun `up is decided by the first target sets only, so an extra set does not block it`() {
        val history = listOf(session(120.0, 12, 12, 12, 6))

        assertEquals(Suggestion.Up(125.0, 8), ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `up accepts weights within 0 001 kg of each other`() {
        val history = listOf(listOf(set(0, 120.0, 12), set(1, 120.0005, 12), set(2, 120.0, 12)))

        assertEquals(Suggestion.Up(125.0, 8), ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `no up when one target set is one rep short of the top`() {
        val history = listOf(session(120.0, 12, 11, 12))

        assertNull(ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `no up when the target sets are at different weights`() {
        val history = listOf(listOf(set(0, 120.0, 12), set(1, 120.0, 12), set(2, 122.5, 12)))

        assertNull(ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `no up when the last session has fewer sets than the target`() {
        val history = listOf(session(120.0, 12, 12))

        assertNull(ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    // Down

    @Test
    fun `down after two sessions that each missed the bottom of the range at the same weight`() {
        val history = listOf(
            session(120.0, 7, 8, 8, sessionId = 2L),
            session(120.0, 8, 7, 8, sessionId = 1L),
        )

        assertEquals(Suggestion.Down(115.0, 8), ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `down goes one step below the weight and stops at zero`() {
        val history = listOf(
            session(2.5, 7, 7, 7, sessionId = 2L),
            session(2.5, 7, 7, 7, sessionId = 1L),
        )

        assertEquals(Suggestion.Down(0.0, 8), ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `no down after only one session under the bottom of the range`() {
        val history = listOf(session(120.0, 7, 8, 8))

        assertNull(ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `no down when the two sessions were at different weights`() {
        val history = listOf(
            session(120.0, 7, 8, 8, sessionId = 2L),
            session(115.0, 8, 7, 8, sessionId = 1L),
        )

        assertNull(ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `no down when the earlier session was within the range`() {
        val history = listOf(
            session(120.0, 7, 8, 8, sessionId = 2L),
            session(120.0, 8, 8, 8, sessionId = 1L),
        )

        assertNull(ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    @Test
    fun `up is checked before down`() {
        val history = listOf(
            session(120.0, 12, 12, 12, sessionId = 2L),
            session(120.0, 7, 7, 7, sessionId = 1L),
        )

        assertEquals(Suggestion.Up(125.0, 8), ProgressionRules.suggest(target, stepKg = 5.0, history = history))
    }

    // Nothing to suggest

    @Test
    fun `no suggestion without a target`() {
        val history = listOf(session(120.0, 12, 12, 12))

        assertNull(ProgressionRules.suggest(null, stepKg = 5.0, history = history))
    }

    @Test
    fun `no suggestion without any history`() {
        assertNull(ProgressionRules.suggest(target, stepKg = 5.0, history = emptyList()))
    }
}

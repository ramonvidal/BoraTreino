package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LoggingRulesTest {
    private val at = Instant.parse("2026-10-30T18:00:00Z")

    private fun set(position: Int, weightKg: Double?, reps: Int?) = WorkoutSet(
        id = position + 1L,
        sessionId = 1L,
        machineId = 1L,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = at,
    )

    // prefill

    @Test
    fun `prefill starts from this session's last set when there is one`() {
        val lastTime = listOf(set(0, 40.0, 10), set(1, 40.0, 8))
        val thisSession = listOf(set(0, 42.5, 12), set(1, 45.0, 9))

        assertEquals(SetDraft(45.0, 9), LoggingRules.prefill(lastTime, thisSession))
    }

    @Test
    fun `prefill starts from last time's first set when nothing is logged this session`() {
        val lastTime = listOf(set(0, 40.0, 10), set(1, 40.0, 8))

        assertEquals(SetDraft(40.0, 10), LoggingRules.prefill(lastTime, emptyList()))
    }

    @Test
    fun `prefill is 0 kg for 10 reps with no history`() {
        assertEquals(SetDraft(0.0, LoggingRules.DEFAULT_REPS), LoggingRules.prefill(emptyList(), emptyList()))
        assertEquals(10, LoggingRules.DEFAULT_REPS)
    }

    @Test
    fun `prefill counts a null weight as 0 kg`() {
        assertEquals(SetDraft(0.0, 10), LoggingRules.prefill(listOf(set(0, null, 10)), emptyList()))
    }

    // sameAgain

    @Test
    fun `same again is last time's set at the index of the next set`() {
        val lastTime = listOf(set(0, 40.0, 10), set(1, 42.5, 8), set(2, 45.0, 6))
        val thisSession = listOf(set(0, 40.0, 10))

        assertEquals(SetDraft(42.5, 8), LoggingRules.sameAgain(lastTime, thisSession))
    }

    @Test
    fun `same again is last time's first set when nothing is logged this session`() {
        val lastTime = listOf(set(0, 40.0, 10), set(1, 42.5, 8))

        assertEquals(SetDraft(40.0, 10), LoggingRules.sameAgain(lastTime, emptyList()))
    }

    @Test
    fun `same again is absent when last time has no set at that index`() {
        val lastTime = listOf(set(0, 40.0, 10), set(1, 42.5, 8))
        val thisSession = listOf(set(0, 40.0, 10), set(1, 40.0, 10))

        assertNull(LoggingRules.sameAgain(lastTime, thisSession))
    }

    @Test
    fun `same again is absent when there is no last time`() {
        assertNull(LoggingRules.sameAgain(emptyList(), emptyList()))
    }

    @Test
    fun `same again is absent when this session has more sets than last time`() {
        val lastTime = listOf(set(0, 40.0, 10))
        val thisSession = listOf(set(0, 40.0, 10), set(1, 40.0, 10), set(2, 40.0, 10))

        assertNull(LoggingRules.sameAgain(lastTime, thisSession))
    }

    // stepWeight: on the grid

    @Test
    fun `a weight on the grid goes one step up and down`() {
        assertEquals(42.5, LoggingRules.stepWeight(40.0, 2.5, up = true), 0.0)
        assertEquals(37.5, LoggingRules.stepWeight(40.0, 2.5, up = false), 0.0)
    }

    @Test
    fun `a 5 kg grid steps by 5 kg`() {
        assertEquals(125.0, LoggingRules.stepWeight(120.0, 5.0, up = true), 0.0)
        assertEquals(115.0, LoggingRules.stepWeight(120.0, 5.0, up = false), 0.0)
    }

    // stepWeight: off the grid snaps to the next grid point in the direction

    @Test
    fun `an off-grid weight steps up to the next grid point`() {
        assertEquals(42.5, LoggingRules.stepWeight(41.0, 2.5, up = true), 0.0)
    }

    @Test
    fun `an off-grid weight steps down to the grid point below`() {
        assertEquals(40.0, LoggingRules.stepWeight(41.0, 2.5, up = false), 0.0)
    }

    @Test
    fun `an off-grid weight on a 5 kg grid snaps both ways`() {
        assertEquals(45.0, LoggingRules.stepWeight(43.0, 5.0, up = true), 0.0)
        assertEquals(40.0, LoggingRules.stepWeight(43.0, 5.0, up = false), 0.0)
    }

    // stepWeight: float noise

    @Test
    fun `a weight within 0 001 of a grid point counts as on the grid going up`() {
        assertEquals(45.0, LoggingRules.stepWeight(42.49999, 2.5, up = true), 0.0)
    }

    @Test
    fun `a weight within 0 001 of a grid point counts as on the grid going down`() {
        assertEquals(40.0, LoggingRules.stepWeight(42.49999, 2.5, up = false), 0.0)
    }

    @Test
    fun `a weight just above a grid point counts as on the grid too`() {
        assertEquals(45.0, LoggingRules.stepWeight(42.5001, 2.5, up = true), 0.0)
        assertEquals(40.0, LoggingRules.stepWeight(42.5001, 2.5, up = false), 0.0)
    }

    @Test
    fun `the result has no float noise`() {
        // 0.3 is 2.9999... steps of 0.1; the result is rounded to 0.001.
        assertEquals(0.4, LoggingRules.stepWeight(0.3, 0.1, up = true), 0.0)
    }

    // stepWeight: clamping

    @Test
    fun `stepping down from 0 stays at 0`() {
        assertEquals(0.0, LoggingRules.stepWeight(0.0, 2.5, up = false), 0.0)
    }

    @Test
    fun `stepping down from a small off-grid weight clamps at 0`() {
        assertEquals(0.0, LoggingRules.stepWeight(1.0, 2.5, up = false), 0.0)
    }

    @Test
    fun `stepping up at the maximum stays at 1000 kg`() {
        assertEquals(1000.0, LoggingRules.stepWeight(1000.0, 2.5, up = true), 0.0)
    }

    @Test
    fun `stepping up near the maximum clamps at 1000 kg`() {
        assertEquals(1000.0, LoggingRules.stepWeight(999.0, 2.5, up = true), 0.0)
    }

    // stepWeight: a non-positive step returns the clamped weight unchanged

    @Test
    fun `a zero step returns the weight unchanged`() {
        assertEquals(40.0, LoggingRules.stepWeight(40.0, 0.0, up = true), 0.0)
        assertEquals(40.0, LoggingRules.stepWeight(40.0, 0.0, up = false), 0.0)
    }

    @Test
    fun `a negative step returns the weight unchanged`() {
        assertEquals(40.0, LoggingRules.stepWeight(40.0, -2.5, up = true), 0.0)
    }

    @Test
    fun `a zero step still clamps the weight`() {
        assertEquals(1000.0, LoggingRules.stepWeight(1500.0, 0.0, up = false), 0.0)
        assertEquals(0.0, LoggingRules.stepWeight(-5.0, 0.0, up = true), 0.0)
    }

    // stepReps

    @Test
    fun `reps step by one in either direction`() {
        assertEquals(11, LoggingRules.stepReps(10, up = true))
        assertEquals(9, LoggingRules.stepReps(10, up = false))
    }

    @Test
    fun `reps stop at 100`() {
        assertEquals(100, LoggingRules.stepReps(100, up = true))
        assertEquals(100, LoggingRules.stepReps(101, up = true))
        assertEquals(100, LoggingRules.MAX_REPS)
    }

    @Test
    fun `reps stop at 1`() {
        assertEquals(1, LoggingRules.stepReps(1, up = false))
        assertEquals(1, LoggingRules.stepReps(0, up = false))
    }

    @Test
    fun `reps from 0 going up reach 1`() {
        assertEquals(1, LoggingRules.stepReps(0, up = true))
    }

    // prefill with a progression suggestion

    @Test
    fun `prefill starts on the suggestion when nothing is logged this session`() {
        val lastTime = listOf(set(0, 120.0, 12), set(1, 120.0, 12), set(2, 120.0, 12))

        assertEquals(
            SetDraft(125.0, 8),
            LoggingRules.prefill(lastTime, emptyList(), Suggestion.Up(125.0, 8)),
        )
    }

    @Test
    fun `prefill starts on a down suggestion when nothing is logged this session`() {
        val lastTime = listOf(set(0, 120.0, 7), set(1, 120.0, 8), set(2, 120.0, 8))

        assertEquals(
            SetDraft(115.0, 8),
            LoggingRules.prefill(lastTime, emptyList(), Suggestion.Down(115.0, 8)),
        )
    }

    @Test
    fun `prefill starts on the suggestion when there is no last time`() {
        assertEquals(
            SetDraft(42.5, 8),
            LoggingRules.prefill(emptyList(), emptyList(), Suggestion.Up(42.5, 8)),
        )
    }

    @Test
    fun `prefill follows this session once it has a set, whatever the suggestion`() {
        val lastTime = listOf(set(0, 120.0, 12))
        val thisSession = listOf(set(0, 122.5, 10))

        assertEquals(
            SetDraft(122.5, 10),
            LoggingRules.prefill(lastTime, thisSession, Suggestion.Up(125.0, 8)),
        )
    }
}

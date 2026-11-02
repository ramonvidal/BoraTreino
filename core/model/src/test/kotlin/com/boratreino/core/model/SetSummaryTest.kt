package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SetSummaryTest {
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

    @Test
    fun `no sets give no summary`() {
        assertNull(SetSummary.of(emptyList()))
    }

    @Test
    fun `identical sets are uniform`() {
        val sets = listOf(set(0, 40.0, 10), set(1, 40.0, 10), set(2, 40.0, 10))

        assertEquals(SetSummary.Uniform(count = 3, weightKg = 40.0, reps = 10), SetSummary.of(sets))
    }

    @Test
    fun `a single set is uniform with a count of one`() {
        assertEquals(SetSummary.Uniform(count = 1, weightKg = 40.0, reps = 10), SetSummary.of(listOf(set(0, 40.0, 10))))
    }

    @Test
    fun `a null weight counts as 0 kg`() {
        val sets = listOf(set(0, null, 10), set(1, null, 10))

        assertEquals(SetSummary.Uniform(count = 2, weightKg = 0.0, reps = 10), SetSummary.of(sets))
    }

    @Test
    fun `same weight with different reps is same weight`() {
        val sets = listOf(set(0, 40.0, 10), set(1, 40.0, 10), set(2, 40.0, 8))

        assertEquals(SetSummary.SameWeight(weightKg = 40.0, reps = listOf(10, 10, 8)), SetSummary.of(sets))
    }

    @Test
    fun `different weights are mixed, one entry per set`() {
        val sets = listOf(set(0, 40.0, 10), set(1, 42.5, 8))

        assertEquals(
            SetSummary.Mixed(listOf(SetDraft(40.0, 10), SetDraft(42.5, 8))),
            SetSummary.of(sets),
        )
    }

    @Test
    fun `weights within 0 001 are the same weight`() {
        val sets = listOf(set(0, 40.0, 10), set(1, 40.0005, 10))

        assertEquals(SetSummary.Uniform(count = 2, weightKg = 40.0, reps = 10), SetSummary.of(sets))
    }

    @Test
    fun `weights 0 002 apart are different`() {
        val sets = listOf(set(0, 40.0, 10), set(1, 40.002, 10))

        assertEquals(
            SetSummary.Mixed(listOf(SetDraft(40.0, 10), SetDraft(40.002, 10))),
            SetSummary.of(sets),
        )
    }

    @Test
    fun `sets are summarised in position order, not list order`() {
        val sets = listOf(set(2, 42.5, 8), set(0, 40.0, 10), set(1, 40.0, 10))

        assertEquals(
            SetSummary.Mixed(listOf(SetDraft(40.0, 10), SetDraft(40.0, 10), SetDraft(42.5, 8))),
            SetSummary.of(sets),
        )
    }

    @Test
    fun `same weight reps are listed in position order`() {
        val sets = listOf(set(2, 40.0, 8), set(0, 40.0, 10), set(1, 40.0, 10))

        assertEquals(SetSummary.SameWeight(weightKg = 40.0, reps = listOf(10, 10, 8)), SetSummary.of(sets))
    }
}

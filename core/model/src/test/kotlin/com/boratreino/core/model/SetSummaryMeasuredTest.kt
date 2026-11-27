package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SetSummaryMeasuredTest {
    private val at = Instant.parse("2026-11-20T09:00:00Z")

    private fun timed(position: Int, weightKg: Double?, seconds: Int, type: SetType = SetType.NORMAL) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 1L, position = position,
        weightKg = weightKg, reps = null, seconds = seconds, calories = null, distanceM = null, loggedAt = at, type = type,
    )

    private fun metres(position: Int, weightKg: Double?, distanceM: Int) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 1L, position = position,
        weightKg = weightKg, reps = null, seconds = null, calories = null, distanceM = distanceM, loggedAt = at,
    )

    @Test
    fun `equal timed sets with a load are a uniform measured summary in seconds`() {
        val summary = SetSummary.of(listOf(timed(0, 47.5, 30), timed(1, 47.5, 30), timed(2, 47.5, 30)))

        assertEquals(
            SetSummary.Measured(SetMeasure.SECONDS, List(3) { SetDraft(47.5, 0, seconds = 30) }),
            summary,
        )
        assertTrue((summary as SetSummary.Measured).uniform)
    }

    @Test
    fun `timed sets at 0 kg are a measured summary with no load`() {
        val summary = SetSummary.of(listOf(timed(0, 0.0, 45), timed(1, 0.0, 45)))

        assertEquals(SetSummary.Measured(SetMeasure.SECONDS, List(2) { SetDraft(0.0, 0, seconds = 45) }), summary)
    }

    @Test
    fun `timed sets that differ are not uniform and keep their position order`() {
        val summary = SetSummary.of(listOf(timed(1, 45.0, 25), timed(0, 47.5, 30)))

        assertEquals(
            SetSummary.Measured(SetMeasure.SECONDS, listOf(SetDraft(47.5, 0, seconds = 30), SetDraft(45.0, 0, seconds = 25))),
            summary,
        )
        assertFalse((summary as SetSummary.Measured).uniform)
    }

    @Test
    fun `equal distance sets are a uniform measured summary in metres`() {
        val summary = SetSummary.of(listOf(metres(0, 40.0, 25), metres(1, 40.0, 25)))

        assertEquals(SetSummary.Measured(SetMeasure.METRES, List(2) { SetDraft(40.0, 0, distanceM = 25) }), summary)
        assertTrue((summary as SetSummary.Measured).uniform)
    }

    @Test
    fun `distance sets that differ in metres are not uniform`() {
        val summary = SetSummary.of(listOf(metres(0, 40.0, 25), metres(1, 40.0, 30)))

        assertFalse((summary as SetSummary.Measured).uniform)
    }

    @Test
    fun `warm-up timed sets are left out of the summary`() {
        val summary = SetSummary.of(listOf(timed(0, 20.0, 10, SetType.WARMUP), timed(1, 47.5, 30)))

        assertEquals(SetSummary.Measured(SetMeasure.SECONDS, listOf(SetDraft(47.5, 0, seconds = 30))), summary)
    }

    @Test
    fun `no working sets is no summary`() {
        assertNull(SetSummary.of(listOf(timed(0, 20.0, 10, SetType.WARMUP))))
        assertNull(SetSummary.of(emptyList()))
    }

    @Test
    fun `cardio shaped sets are still a cardio summary, not a measured one`() {
        val cardio = WorkoutSet(
            id = 1L, sessionId = 1L, machineId = 1L, position = 0,
            weightKg = null, reps = null, seconds = 600, calories = 90, distanceM = 2000, loggedAt = at,
        )

        assertTrue(SetSummary.of(listOf(cardio)) is SetSummary.Cardio)
    }
}

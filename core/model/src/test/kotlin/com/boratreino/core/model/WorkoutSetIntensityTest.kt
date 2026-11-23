package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** A set's intensity is optional and lies in 0.0..100.0. */
class WorkoutSetIntensityTest {
    private fun set(intensity: Double?) = WorkoutSet(
        id = 1L,
        sessionId = 1L,
        machineId = 1L,
        position = 0,
        weightKg = null,
        reps = null,
        seconds = 600,
        calories = null,
        distanceM = null,
        loggedAt = Instant.parse("2026-11-02T10:00:00Z"),
        intensity = intensity,
    )

    @Test
    fun `intensity is not logged by default`() {
        assertNull(set(null).intensity)
    }

    @Test
    fun `a decimal intensity keeps its decimals`() {
        assertEquals(0.79, set(0.79).intensity!!, 0.0)
    }

    @Test
    fun `the range ends 0 and 100 are accepted`() {
        assertEquals(0.0, set(0.0).intensity!!, 0.0)
        assertEquals(100.0, set(CardioRules.MAX_INTENSITY).intensity!!, 0.0)
    }

    @Test
    fun `intensity below 0 is rejected`() {
        assertTrue(runCatching { set(-0.01) }.isFailure)
    }

    @Test
    fun `intensity above 100 is rejected`() {
        assertTrue(runCatching { set(100.01) }.isFailure)
    }
}

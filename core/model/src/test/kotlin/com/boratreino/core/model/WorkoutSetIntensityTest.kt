package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

/** A set's intensity is optional and lies in 0..999. */
class WorkoutSetIntensityTest {
    private val at = Instant.parse("2026-11-02T18:00:00Z")

    private fun set(intensity: Int?) = WorkoutSet(
        id = 1L,
        sessionId = 1L,
        machineId = 1L,
        position = 0,
        weightKg = null,
        reps = null,
        seconds = 600,
        calories = null,
        distanceM = 2000,
        loggedAt = at,
        intensity = intensity,
    )

    @Test
    fun `intensity is not logged by default`() {
        assertNull(set(null).intensity)
    }

    @Test
    fun `intensity 0 and 999 are accepted`() {
        assertEquals(0, set(0).intensity)
        assertEquals(999, set(CardioRules.MAX_INTENSITY).intensity)
    }

    @Test
    fun `intensity below 0 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { set(-1) }
    }

    @Test
    fun `intensity above 999 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { set(1000) }
    }
}

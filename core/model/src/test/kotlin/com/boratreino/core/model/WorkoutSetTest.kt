package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class WorkoutSetTest {
    private val loggedAt: Instant = Instant.parse("2026-10-29T18:05:00Z")

    private fun set(
        weightKg: Double? = 40.0,
        reps: Int? = 10,
        seconds: Int? = null,
        calories: Int? = null,
        distanceM: Int? = null,
    ) = WorkoutSet(
        id = 1L,
        sessionId = 1L,
        machineId = 2L,
        position = 0,
        weightKg = weightKg,
        reps = reps,
        seconds = seconds,
        calories = calories,
        distanceM = distanceM,
        loggedAt = loggedAt,
    )

    @Test
    fun `a set with weight and reps is accepted`() {
        val set = set()
        assertEquals(40.0, set.weightKg!!, 0.0)
        assertEquals(10, set.reps)
    }

    @Test
    fun `zero weight, reps and cardio numbers are accepted`() {
        val set = set(weightKg = 0.0, reps = 0, seconds = 0, calories = 0, distanceM = 0)
        assertEquals(0, set.reps)
        assertEquals(0, set.seconds)
        assertEquals(0, set.calories)
        assertEquals(0, set.distanceM)
    }

    @Test
    fun `a negative weight is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { set(weightKg = -2.5) }
    }

    @Test
    fun `negative reps are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { set(reps = -1) }
    }

    @Test
    fun `negative seconds are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { set(seconds = -1) }
    }

    @Test
    fun `negative calories are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { set(calories = -1) }
    }

    @Test
    fun `negative distance is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { set(distanceM = -1) }
    }

    @Test
    fun `every measurement may be null`() {
        val set = set(weightKg = null, reps = null, seconds = null, calories = null, distanceM = null)
        assertNull(set.weightKg)
        assertNull(set.reps)
        assertNull(set.seconds)
        assertNull(set.calories)
        assertNull(set.distanceM)
    }
}

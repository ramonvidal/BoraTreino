package com.boratreino.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CardioSetShapeTest {
    private val at = Instant.parse("2026-11-20T09:00:00Z")

    private fun set(
        weightKg: Double? = null,
        reps: Int? = null,
        seconds: Int? = null,
        calories: Int? = null,
        distanceM: Int? = null,
        intensity: Double? = null,
    ) = WorkoutSet(
        id = 1L, sessionId = 1L, machineId = 1L, position = 0,
        weightKg = weightKg, reps = reps, seconds = seconds, calories = calories, distanceM = distanceM,
        loggedAt = at, intensity = intensity,
    )

    @Test
    fun `a set with no weight and no reps but seconds is cardio`() {
        assertTrue(CardioRules.isCardio(set(seconds = 600)))
    }

    @Test
    fun `a set with no weight and no reps but calories is cardio`() {
        assertTrue(CardioRules.isCardio(set(calories = 250)))
    }

    @Test
    fun `a set with no weight and no reps but distance is cardio`() {
        assertTrue(CardioRules.isCardio(set(distanceM = 5000)))
    }

    @Test
    fun `a set with no weight and no reps but an intensity is cardio`() {
        assertTrue(CardioRules.isCardio(set(intensity = 0.79)))
    }

    @Test
    fun `a set with no weight, no reps and no cardio field is not cardio`() {
        assertFalse(CardioRules.isCardio(set()))
    }

    @Test
    fun `a set with a weight is not cardio even without reps`() {
        assertFalse(CardioRules.isCardio(set(weightKg = 47.5, seconds = 30)))
        assertFalse(CardioRules.isCardio(set(weightKg = 40.0, distanceM = 25)))
    }

    @Test
    fun `a set with reps is not cardio even without a weight`() {
        assertFalse(CardioRules.isCardio(set(reps = 12, seconds = 30)))
    }

    @Test
    fun `a timed strength set with weight 0 kg is not cardio`() {
        assertFalse(CardioRules.isCardio(set(weightKg = 0.0, seconds = 45)))
    }

    @Test
    fun `a distance strength set with weight 0 kg is not cardio`() {
        assertFalse(CardioRules.isCardio(set(weightKg = 0.0, distanceM = 20)))
    }

    @Test
    fun `a strength set logged by LoggingRules is never cardio, whatever its kind`() {
        val draft = SetDraft(weightKg = 0.0, reps = 0, seconds = 30, distanceM = 20)
        for (kind in MachineKind.entries.filter { it.isStrength }) {
            val logged = LoggingRules.toSet(1L, 1L, 1L, 0, draft, kind, at)
            assertFalse(kind.name, CardioRules.isCardio(logged))
        }
    }
}

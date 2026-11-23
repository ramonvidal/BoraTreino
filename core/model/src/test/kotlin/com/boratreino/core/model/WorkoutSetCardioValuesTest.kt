package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

/** The bike console values on a set (phase 42): ranges, and nulls for "not logged". */
class WorkoutSetCardioValuesTest {
    private val at = Instant.parse("2026-11-23T18:00:00Z")

    private fun set(
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
    ) = WorkoutSet(
        id = 1L,
        sessionId = 1L,
        machineId = 1L,
        position = 0,
        weightKg = null,
        reps = null,
        seconds = 1200,
        calories = null,
        distanceM = 5000,
        loggedAt = at,
        watts = watts,
        heartRate = heartRate,
        wattsPerKg = wattsPerKg,
        tss = tss,
        intensityFactor = intensityFactor,
    )

    @Test
    fun `the console values are not logged by default`() {
        val plain = set()

        assertNull(plain.watts)
        assertNull(plain.heartRate)
        assertNull(plain.wattsPerKg)
        assertNull(plain.tss)
        assertNull(plain.intensityFactor)
    }

    @Test
    fun `the limits are 2000 W, 30 to 250 bpm, 10 W per kg, 999 TSS and an IF of 2 at most`() {
        assertEquals(2000, CardioRules.MAX_WATTS)
        assertEquals(30, CardioRules.MIN_HEART_RATE)
        assertEquals(250, CardioRules.MAX_HEART_RATE)
        assertEquals(10.0, CardioRules.MAX_WATTS_PER_KG, 0.0)
        assertEquals(999, CardioRules.MAX_TSS)
        assertEquals(2.0, CardioRules.MAX_INTENSITY_FACTOR, 0.0)
    }

    @Test
    fun `values at the limits are accepted`() {
        val top = set(watts = 2000, heartRate = 250, wattsPerKg = 10.0, tss = 999, intensityFactor = 2.0)
        val bottom = set(watts = 0, heartRate = 30, wattsPerKg = 0.0, tss = 0, intensityFactor = 0.0)

        assertEquals(2000, top.watts)
        assertEquals(250, top.heartRate)
        assertEquals(10.0, top.wattsPerKg!!, 0.0)
        assertEquals(999, top.tss)
        assertEquals(2.0, top.intensityFactor!!, 0.0)
        assertEquals(0, bottom.watts)
        assertEquals(30, bottom.heartRate)
        assertEquals(0.0, bottom.wattsPerKg!!, 0.0)
        assertEquals(0, bottom.tss)
        assertEquals(0.0, bottom.intensityFactor!!, 0.0)
    }

    @Test
    fun `watts above 2000 or below 0 are refused`() {
        assertThrows(IllegalArgumentException::class.java) { set(watts = 2001) }
        assertThrows(IllegalArgumentException::class.java) { set(watts = -1) }
    }

    @Test
    fun `heart rate outside 30 to 250 is refused`() {
        assertThrows(IllegalArgumentException::class.java) { set(heartRate = 29) }
        assertThrows(IllegalArgumentException::class.java) { set(heartRate = 251) }
        assertThrows(IllegalArgumentException::class.java) { set(heartRate = 0) }
    }

    @Test
    fun `watts per kg outside 0 to 10 is refused`() {
        assertThrows(IllegalArgumentException::class.java) { set(wattsPerKg = 10.01) }
        assertThrows(IllegalArgumentException::class.java) { set(wattsPerKg = -0.1) }
    }

    @Test
    fun `tss outside 0 to 999 is refused`() {
        assertThrows(IllegalArgumentException::class.java) { set(tss = 1000) }
        assertThrows(IllegalArgumentException::class.java) { set(tss = -1) }
    }

    @Test
    fun `intensity factor outside 0 to 2 is refused`() {
        assertThrows(IllegalArgumentException::class.java) { set(intensityFactor = 2.01) }
        assertThrows(IllegalArgumentException::class.java) { set(intensityFactor = -0.01) }
    }

    @Test
    fun `a set with only some of the values is valid`() {
        val partial = set(watts = 160, intensityFactor = 0.79)

        assertEquals(160, partial.watts)
        assertNull(partial.heartRate)
        assertNull(partial.tss)
    }
}

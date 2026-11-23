package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Averages of the bike console values (time weighted, seconds > 0 only) and the TSS total (phase 42). */
class CardioValueAveragesTest {
    private val at = Instant.parse("2026-11-23T18:00:00Z")

    private fun set(
        id: Long,
        seconds: Int? = null,
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
        intensity: Int? = null,
    ) = WorkoutSet(
        id = id,
        sessionId = 1L,
        machineId = 1L,
        position = id.toInt(),
        weightKg = null,
        reps = null,
        seconds = seconds,
        calories = null,
        distanceM = null,
        loggedAt = at,
        intensity = intensity,
        watts = watts,
        heartRate = heartRate,
        wattsPerKg = wattsPerKg,
        tss = tss,
        intensityFactor = intensityFactor,
    )

    // powerAverage

    @Test
    fun `power average is weighted by each set's time`() {
        val sets = listOf(set(1L, seconds = 600, watts = 100), set(2L, seconds = 1200, watts = 200))

        // (100 x 600 + 200 x 1200) / 1800
        assertEquals(500.0 / 3.0, CardioRules.powerAverage(sets)!!, 1e-9)
    }

    @Test
    fun `power average ignores sets without watts or without time`() {
        val sets = listOf(
            set(1L, seconds = 600, watts = 150),
            set(2L, seconds = 600),
            set(3L, watts = 900),
            set(4L, seconds = 0, watts = 900),
        )

        assertEquals(150.0, CardioRules.powerAverage(sets)!!, 1e-9)
    }

    @Test
    fun `power average is null when no set has watts and time`() {
        assertNull(CardioRules.powerAverage(listOf(set(1L, seconds = 600), set(2L, watts = 150))))
        assertNull(CardioRules.powerAverage(emptyList()))
    }

    @Test
    fun `a zero watts set counts in the power average`() {
        val sets = listOf(set(1L, seconds = 600, watts = 0), set(2L, seconds = 600, watts = 200))

        assertEquals(100.0, CardioRules.powerAverage(sets)!!, 1e-9)
    }

    // heartRateAverage

    @Test
    fun `heart rate average is weighted by time`() {
        val sets = listOf(set(1L, seconds = 300, heartRate = 130), set(2L, seconds = 900, heartRate = 150))

        assertEquals(145.0, CardioRules.heartRateAverage(sets)!!, 1e-9)
    }

    @Test
    fun `heart rate average ignores sets without a heart rate or without time`() {
        val sets = listOf(set(1L, seconds = 600, heartRate = 140), set(2L, heartRate = 90), set(3L, seconds = 600))

        assertEquals(140.0, CardioRules.heartRateAverage(sets)!!, 1e-9)
    }

    @Test
    fun `heart rate average is null when no set has a heart rate and time`() {
        assertNull(CardioRules.heartRateAverage(listOf(set(1L, seconds = 600))))
    }

    // wattsPerKgAverage

    @Test
    fun `watts per kg average is weighted by time`() {
        val sets = listOf(set(1L, seconds = 600, wattsPerKg = 2.0), set(2L, seconds = 1800, wattsPerKg = 3.0))

        assertEquals(2.75, CardioRules.wattsPerKgAverage(sets)!!, 1e-9)
    }

    @Test
    fun `watts per kg average ignores sets without the value or without time`() {
        val sets = listOf(set(1L, seconds = 600, wattsPerKg = 2.4), set(2L, wattsPerKg = 9.0), set(3L, seconds = 600))

        assertEquals(2.4, CardioRules.wattsPerKgAverage(sets)!!, 1e-9)
    }

    @Test
    fun `watts per kg average is null when no set has one`() {
        assertNull(CardioRules.wattsPerKgAverage(listOf(set(1L, seconds = 600))))
    }

    // intensityFactorAverage

    @Test
    fun `intensity factor average is weighted by time`() {
        val sets = listOf(set(1L, seconds = 1200, intensityFactor = 0.70), set(2L, seconds = 600, intensityFactor = 0.85))

        // (0.70 x 1200 + 0.85 x 600) / 1800
        assertEquals((0.70 * 1200 + 0.85 * 600) / 1800, CardioRules.intensityFactorAverage(sets)!!, 1e-9)
    }

    @Test
    fun `intensity factor average ignores sets without the value or without time`() {
        val sets = listOf(set(1L, seconds = 600, intensityFactor = 0.79), set(2L, intensityFactor = 1.5))

        assertEquals(0.79, CardioRules.intensityFactorAverage(sets)!!, 1e-9)
    }

    @Test
    fun `intensity factor average is null when no set has one`() {
        assertNull(CardioRules.intensityFactorAverage(listOf(set(1L, seconds = 600, watts = 150))))
    }

    @Test
    fun `the average does not use the intensity level`() {
        assertNull(CardioRules.intensityFactorAverage(listOf(set(1L, seconds = 600, intensity = 12))))
    }

    // tssTotal

    @Test
    fun `tss total is the sum of the sets' TSS`() {
        val sets = listOf(set(1L, seconds = 600, tss = 20), set(2L, seconds = 600, tss = 28), set(3L, seconds = 600))

        assertEquals(48, CardioRules.tssTotal(sets))
    }

    @Test
    fun `tss total counts a set's TSS even without time`() {
        assertEquals(30, CardioRules.tssTotal(listOf(set(1L, tss = 30))))
    }

    @Test
    fun `a single TSS of zero is a total of zero, not null`() {
        assertEquals(0, CardioRules.tssTotal(listOf(set(1L, seconds = 600, tss = 0))))
    }

    @Test
    fun `tss total is null when no set has a TSS`() {
        assertNull(CardioRules.tssTotal(listOf(set(1L, seconds = 600, watts = 150))))
        assertNull(CardioRules.tssTotal(emptyList()))
    }
}

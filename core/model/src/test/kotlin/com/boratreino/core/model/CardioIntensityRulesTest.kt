package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Cardio intensity (0.0..100.0, 2 decimals): the stepper, its rounding, and the averages of sets and blocks. */
class CardioIntensityRulesTest {
    private val at: Instant = Instant.parse("2026-11-02T10:00:00Z")

    private fun set(
        id: Long = 1L,
        sessionId: Long = 1L,
        position: Int = 0,
        seconds: Int? = null,
        distanceM: Int? = null,
        intensity: Double? = null,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = 1L,
        position = position,
        weightKg = null,
        reps = null,
        seconds = seconds,
        calories = null,
        distanceM = distanceM,
        loggedAt = at,
        intensity = intensity,
    )

    // The stepper

    @Test
    fun `the intensity maximum is 100 and the start is 0 point 70`() {
        assertEquals(100.0, CardioRules.MAX_INTENSITY, 0.0)
        assertEquals(0.70, CardioRules.START_INTENSITY, 0.0)
    }

    @Test
    fun `up from not logged starts at 0 point 70`() {
        assertEquals(0.70, CardioRules.stepIntensity(null, up = true)!!, 0.0)
    }

    @Test
    fun `down from not logged stays not logged`() {
        assertNull(CardioRules.stepIntensity(null, up = false))
    }

    @Test
    fun `intensity steps by 0 point 01 in both directions`() {
        assertEquals(0.71, CardioRules.stepIntensity(0.70, up = true)!!, 0.0)
        assertEquals(0.78, CardioRules.stepIntensity(0.79, up = false)!!, 0.0)
        assertEquals(12.01, CardioRules.stepIntensity(12.0, up = true)!!, 0.0)
    }

    @Test
    fun `ten up steps from 0 point 70 are exactly 0 point 80`() {
        var value: Double? = 0.70

        repeat(10) { value = CardioRules.stepIntensity(value, up = true) }

        assertEquals(0.80, value!!, 0.0)
    }

    @Test
    fun `steps from a decimal do not drift`() {
        var value: Double? = 0.29

        value = CardioRules.stepIntensity(value, up = true)

        assertEquals(0.30, value!!, 0.0)
    }

    @Test
    fun `intensity stops at 0 and stays logged there`() {
        assertEquals(0.0, CardioRules.stepIntensity(0.01, up = false)!!, 0.0)
        assertEquals(0.0, CardioRules.stepIntensity(0.0, up = false)!!, 0.0)
    }

    @Test
    fun `intensity stops at 100`() {
        assertEquals(100.0, CardioRules.stepIntensity(99.99, up = true)!!, 0.0)
        assertEquals(100.0, CardioRules.stepIntensity(100.0, up = true)!!, 0.0)
    }

    @Test
    fun `a value between hundredths is rounded to 2 decimals`() {
        assertEquals(0.79, CardioRules.roundIntensity(0.785), 0.0)
        assertEquals(0.78, CardioRules.roundIntensity(0.784), 0.0)
        assertEquals(12.0, CardioRules.roundIntensity(12.0), 0.0)
    }

    @Test
    fun `rounding keeps the value inside 0 to 100`() {
        assertEquals(0.0, CardioRules.roundIntensity(-1.0), 0.0)
        assertEquals(100.0, CardioRules.roundIntensity(150.0), 0.0)
    }

    // Speed

    @Test
    fun `speed is distance over time in km per hour`() {
        assertEquals(9.0, CardioRules.speedKmh(distanceM = 3000, seconds = 1200)!!, 1e-9)
    }

    @Test
    fun `speed is null unless both distance and time are above zero`() {
        assertNull(CardioRules.speedKmh(distanceM = null, seconds = 1200))
        assertNull(CardioRules.speedKmh(distanceM = 3000, seconds = null))
        assertNull(CardioRules.speedKmh(distanceM = 0, seconds = 1200))
        assertNull(CardioRules.speedKmh(distanceM = 3000, seconds = 0))
    }

    @Test
    fun `average speed is total distance over total time`() {
        val sets = listOf(set(seconds = 600, distanceM = 2000), set(id = 2L, seconds = 1200, distanceM = 3000))

        assertEquals(5000.0 / 1800.0 * 3.6, CardioRules.averageSpeedKmh(sets)!!, 1e-9)
    }

    @Test
    fun `average speed ignores sets missing distance or time`() {
        val sets = listOf(
            set(seconds = 600, distanceM = 2000),
            set(id = 2L, seconds = 600),
            set(id = 3L, distanceM = 9000),
        )

        assertEquals(2000.0 / 600.0 * 3.6, CardioRules.averageSpeedKmh(sets)!!, 1e-9)
    }

    @Test
    fun `average speed is null when no set has both distance and time`() {
        assertNull(CardioRules.averageSpeedKmh(listOf(set(seconds = 600), set(id = 2L, distanceM = 2000))))
    }

    // Intensity average

    @Test
    fun `intensity average is weighted by time`() {
        val sets = listOf(set(seconds = 600, intensity = 0.10), set(id = 2L, seconds = 1200, intensity = 0.20))

        assertEquals((0.10 * 600 + 0.20 * 1200) / 1800.0, CardioRules.intensityAverage(sets)!!, 1e-9)
    }

    @Test
    fun `intensity average is a decimal`() {
        val sets = listOf(set(seconds = 600, intensity = 0.79), set(id = 2L, seconds = 600, intensity = 0.80))

        assertEquals(0.795, CardioRules.intensityAverage(sets)!!, 1e-9)
    }

    @Test
    fun `intensity average ignores sets without an intensity or without time`() {
        val sets = listOf(
            set(seconds = 600, intensity = 10.0),
            set(id = 2L, seconds = 600),
            set(id = 3L, intensity = 50.0),
            set(id = 4L, seconds = 0, intensity = 50.0),
        )

        assertEquals(10.0, CardioRules.intensityAverage(sets)!!, 1e-9)
    }

    @Test
    fun `intensity average is null when no set has an intensity and time`() {
        assertNull(CardioRules.intensityAverage(emptyList()))
        assertNull(CardioRules.intensityAverage(listOf(set(seconds = 600))))
    }

    // 7-day blocks

    @Test
    fun `a block carries its minutes, distance, sessions, intensity and speed`() {
        val sets = listOf(
            LocalDate.of(2026, 10, 15) to set(id = 1L, sessionId = 1L, seconds = 600, distanceM = 2000, intensity = 0.10),
            LocalDate.of(2026, 10, 21) to set(id = 2L, sessionId = 2L, seconds = 1200, distanceM = 3000, intensity = 0.20),
        )

        val block = CardioRules.blocks(sets, LocalDate.of(2026, 10, 15)).single()

        assertEquals(30, block.minutes)
        assertEquals(5000, block.distanceM)
        assertEquals(2, block.sessions)
        assertEquals((0.10 * 600 + 0.20 * 1200) / 1800.0, block.intensityAvg!!, 1e-9)
        assertEquals(5000.0 / 1800.0 * 3.6, block.speedKmh!!, 1e-9)
    }

    @Test
    fun `blocks hold one entry per 7-day block with cardio, oldest first`() {
        val start = LocalDate.of(2026, 10, 15)
        val sets = listOf(
            LocalDate.of(2026, 10, 22) to set(id = 2L, sessionId = 2L, seconds = 600, intensity = 0.30),
            LocalDate.of(2026, 10, 15) to set(id = 1L, sessionId = 1L, seconds = 600, intensity = 0.10),
        )

        val blocks = CardioRules.blocks(sets, start)

        assertEquals(listOf(start, start.plusWeeks(1)), blocks.map { it.weekStart })
        assertEquals(0.10, blocks[0].intensityAvg!!, 1e-9)
        assertEquals(0.30, blocks[1].intensityAvg!!, 1e-9)
    }

    @Test
    fun `a block with no intensity or distance has null averages`() {
        val block = CardioRules.blocks(
            listOf(LocalDate.of(2026, 10, 15) to set(seconds = 600)),
            LocalDate.of(2026, 10, 15),
        ).single()

        assertNull(block.intensityAvg)
        assertNull(block.speedKmh)
    }

    @Test
    fun `no cardio gives no blocks`() {
        assertEquals(emptyList<CardioBlock>(), CardioRules.blocks(emptyList(), LocalDate.of(2026, 10, 15)))
    }
}

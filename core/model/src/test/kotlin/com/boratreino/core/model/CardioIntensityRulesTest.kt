package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Cardio intensity and speed: the stepper, the averages and the 7-day blocks, from the phase 41 contract. */
class CardioIntensityRulesTest {
    private val at = Instant.parse("2026-11-02T18:00:00Z")

    private fun set(
        id: Long = 1L,
        sessionId: Long = 1L,
        position: Int = 0,
        seconds: Int? = null,
        calories: Int? = null,
        distanceM: Int? = null,
        intensity: Int? = null,
        type: SetType = SetType.NORMAL,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = 1L,
        position = position,
        weightKg = null,
        reps = null,
        seconds = seconds,
        calories = calories,
        distanceM = distanceM,
        loggedAt = at,
        type = type,
        intensity = intensity,
    )

    private fun draft(minutes: Int = 0, calories: Int = 0, distanceM: Int = 0, intensity: Int? = null) =
        CardioDraft(minutes, calories, distanceM, intensity)

    // stepIntensity

    @Test
    fun `the intensity maximum is 999`() {
        assertEquals(999, CardioRules.MAX_INTENSITY)
    }

    @Test
    fun `an unset intensity goes to 1 when stepped up and stays unset when stepped down`() {
        assertEquals(1, CardioRules.stepIntensity(null, up = true))
        assertNull(CardioRules.stepIntensity(null, up = false))
    }

    @Test
    fun `intensity steps by one in both directions`() {
        assertEquals(13, CardioRules.stepIntensity(12, up = true))
        assertEquals(11, CardioRules.stepIntensity(12, up = false))
    }

    @Test
    fun `intensity stops at 0`() {
        assertEquals(0, CardioRules.stepIntensity(1, up = false))
        assertEquals(0, CardioRules.stepIntensity(0, up = false))
    }

    @Test
    fun `intensity stops at 999`() {
        assertEquals(999, CardioRules.stepIntensity(999, up = true))
        assertEquals(998, CardioRules.stepIntensity(999, up = false))
    }

    // speedKmh

    @Test
    fun `speed is distance over time in km per hour`() {
        assertEquals(10.0, CardioRules.speedKmh(5000, 1800)!!, 1e-9)
        assertEquals(1.0, CardioRules.speedKmh(1000, 3600)!!, 1e-9)
    }

    @Test
    fun `speed is null unless both distance and time are above zero`() {
        assertNull(CardioRules.speedKmh(null, 1800))
        assertNull(CardioRules.speedKmh(5000, null))
        assertNull(CardioRules.speedKmh(0, 1800))
        assertNull(CardioRules.speedKmh(5000, 0))
    }

    // prefill, sameAgain, toSet, toDraft carry the intensity

    @Test
    fun `prefill carries the intensity of this session's last set`() {
        val lastTime = listOf(set(seconds = 1200, distanceM = 3000, intensity = 9))
        val thisSession = listOf(set(id = 2L, seconds = 900, intensity = 12))

        assertEquals(draft(minutes = 15, intensity = 12), CardioRules.prefill(lastTime, thisSession))
    }

    @Test
    fun `prefill takes last time's intensity when this session has none`() {
        val lastTime = listOf(set(seconds = 1200, distanceM = 3000, intensity = 9))

        assertEquals(draft(minutes = 20, distanceM = 3000, intensity = 9), CardioRules.prefill(lastTime, emptyList()))
    }

    @Test
    fun `prefill with no intensity logged before leaves the intensity unset`() {
        assertNull(CardioRules.prefill(listOf(set(seconds = 1200)), emptyList()).intensity)
        assertNull(CardioRules.prefill(emptyList(), emptyList()).intensity)
    }

    @Test
    fun `same again carries last time's intensity at the same index`() {
        val lastTime = listOf(set(seconds = 600, intensity = 7), set(id = 2L, position = 1, seconds = 300, intensity = 15))

        assertEquals(draft(minutes = 5, intensity = 15), CardioRules.sameAgain(lastTime, listOf(set(seconds = 600))))
    }

    @Test
    fun `to set writes the intensity`() {
        val saved = CardioRules.toSet(
            id = 5L,
            sessionId = 2L,
            machineId = 3L,
            position = 0,
            draft = draft(minutes = 10, intensity = 14),
            loggedAt = at,
        )

        assertEquals(600, saved.seconds)
        assertEquals(14, saved.intensity)
        assertNull(saved.calories)
        assertNull(saved.distanceM)
    }

    @Test
    fun `to set leaves the intensity unset when the draft has none`() {
        val saved = CardioRules.toSet(1L, 2L, 3L, 0, draft(minutes = 10), at)

        assertNull(saved.intensity)
    }

    @Test
    fun `to set keeps an out of range intensity inside 0 to 999`() {
        val saved = CardioRules.toSet(1L, 2L, 3L, 0, draft(minutes = 10, intensity = 1500), at)

        assertEquals(999, saved.intensity)
    }

    @Test
    fun `to draft reads the intensity back`() {
        val read = CardioRules.toDraft(set(seconds = 1500, calories = 300, distanceM = 5000, intensity = 11))

        assertEquals(draft(minutes = 25, calories = 300, distanceM = 5000, intensity = 11), read)
    }

    @Test
    fun `to draft of a set without intensity has none`() {
        assertNull(CardioRules.toDraft(set(seconds = 600)).intensity)
    }

    // isCardio, canLog

    @Test
    fun `a set with only an intensity is cardio`() {
        assertTrue(CardioRules.isCardio(set(intensity = 5)))
    }

    @Test
    fun `a set with no cardio values is not cardio`() {
        assertFalse(CardioRules.isCardio(set()))
    }

    @Test
    fun `an intensity alone cannot be logged`() {
        assertFalse(CardioRules.canLog(draft(intensity = 12)))
        assertFalse(CardioRules.canLog(draft(distanceM = 1000, intensity = 12)))
    }

    @Test
    fun `minutes or calories are still what lets a cardio set be logged`() {
        assertTrue(CardioRules.canLog(draft(minutes = 1)))
        assertTrue(CardioRules.canLog(draft(calories = 10, intensity = 3)))
    }

    // intensityAverage

    @Test
    fun `intensity average is weighted by time`() {
        val sets = listOf(set(seconds = 600, intensity = 10), set(id = 2L, seconds = 1200, intensity = 20))

        assertEquals(50.0 / 3.0, CardioRules.intensityAverage(sets)!!, 1e-9)
    }

    @Test
    fun `intensity average ignores sets without an intensity or without time`() {
        val sets = listOf(
            set(seconds = 600, intensity = 10),
            set(id = 2L, seconds = 600),
            set(id = 3L, intensity = 50),
            set(id = 4L, seconds = 0, intensity = 50),
        )

        assertEquals(10.0, CardioRules.intensityAverage(sets)!!, 1e-9)
    }

    @Test
    fun `intensity average is null when no set has an intensity and time`() {
        assertNull(CardioRules.intensityAverage(emptyList()))
        assertNull(CardioRules.intensityAverage(listOf(set(seconds = 600))))
    }

    // averageSpeedKmh

    @Test
    fun `average speed is total distance over total time`() {
        val sets = listOf(set(seconds = 1800, distanceM = 5000), set(id = 2L, seconds = 1800, distanceM = 3000))

        assertEquals(8.0, CardioRules.averageSpeedKmh(sets)!!, 1e-9)
    }

    @Test
    fun `average speed ignores sets missing distance or time`() {
        val sets = listOf(
            set(seconds = 1800, distanceM = 5000),
            set(id = 2L, seconds = 1800),
            set(id = 3L, distanceM = 9000),
        )

        assertEquals(10.0, CardioRules.averageSpeedKmh(sets)!!, 1e-9)
    }

    @Test
    fun `average speed is null when no set has both distance and time`() {
        assertNull(CardioRules.averageSpeedKmh(emptyList()))
        assertNull(CardioRules.averageSpeedKmh(listOf(set(seconds = 600))))
    }

    // blocks

    @Test
    fun `blocks group cardio into 7-day blocks from the period start, oldest first, and skip empty blocks`() {
        val start = LocalDate.of(2026, 10, 15)
        val weightSet = WorkoutSet(9L, 1L, 2L, 0, 40.0, 10, null, null, null, at)
        val pairs = listOf(
            LocalDate.of(2026, 10, 14) to set(id = 10L, sessionId = 9L, seconds = 6000, distanceM = 9000),
            LocalDate.of(2026, 10, 15) to set(id = 1L, sessionId = 1L, seconds = 600, distanceM = 2000, intensity = 10),
            LocalDate.of(2026, 10, 15) to weightSet,
            LocalDate.of(2026, 10, 21) to set(id = 2L, sessionId = 2L, seconds = 1200, distanceM = 3000, intensity = 20),
            LocalDate.of(2026, 10, 22) to set(id = 3L, sessionId = 3L, seconds = 300),
            LocalDate.of(2026, 11, 11) to set(id = 4L, sessionId = 4L, seconds = 900),
        )

        val blocks = CardioRules.blocks(pairs, start)

        assertEquals(3, blocks.size)
        assertEquals(LocalDate.of(2026, 10, 15), blocks[0].weekStart)
        assertEquals(LocalDate.of(2026, 10, 22), blocks[1].weekStart)
        assertEquals(LocalDate.of(2026, 11, 5), blocks[2].weekStart)
    }

    @Test
    fun `a block carries its minutes, distance, distinct sessions, intensity and speed`() {
        val start = LocalDate.of(2026, 10, 15)
        val pairs = listOf(
            LocalDate.of(2026, 10, 15) to set(id = 1L, sessionId = 1L, seconds = 600, distanceM = 2000, intensity = 10),
            LocalDate.of(2026, 10, 21) to set(id = 2L, sessionId = 2L, seconds = 1200, distanceM = 3000, intensity = 20),
            LocalDate.of(2026, 10, 21) to set(id = 3L, sessionId = 2L, seconds = 30),
        )

        val block = CardioRules.blocks(pairs, start).single()

        assertEquals(31, block.minutes)
        assertEquals(5000, block.distanceM)
        assertEquals(2, block.sessions)
        assertEquals(50.0 / 3.0, block.intensityAvg!!, 1e-9)
        // The 30 s set has no distance, so speed uses only the first two sets: 5000 m in 1800 s.
        assertEquals(10.0, block.speedKmh!!, 1e-9)
    }

    @Test
    fun `a block with no intensity or distance has null averages`() {
        val pairs = listOf(LocalDate.of(2026, 10, 22) to set(id = 3L, sessionId = 3L, seconds = 300))

        val block = CardioRules.blocks(pairs, LocalDate.of(2026, 10, 15)).single()

        assertEquals(5, block.minutes)
        assertEquals(0, block.distanceM)
        assertEquals(1, block.sessions)
        assertNull(block.intensityAvg)
        assertNull(block.speedKmh)
    }

    @Test
    fun `no cardio gives no blocks`() {
        assertTrue(CardioRules.blocks(emptyList(), LocalDate.of(2026, 10, 15)).isEmpty())
    }
}

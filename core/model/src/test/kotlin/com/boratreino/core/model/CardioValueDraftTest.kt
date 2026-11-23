package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** A cardio draft and its set: intensity is carried by the draft, the set, prefill and same-again. */
class CardioValueDraftTest {
    private val at: Instant = Instant.parse("2026-11-02T10:00:00Z")

    private fun set(
        id: Long = 1L,
        sessionId: Long = 1L,
        position: Int = 0,
        seconds: Int? = null,
        calories: Int? = null,
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
        calories = calories,
        distanceM = distanceM,
        loggedAt = at,
        intensity = intensity,
    )

    private fun toSet(draft: CardioDraft): WorkoutSet = CardioRules.toSet(1L, 2L, 3L, 0, draft, at)

    // toSet

    @Test
    fun `toSet writes the intensity with 2 decimals`() {
        assertEquals(0.79, toSet(CardioDraft(20, 0, 0, intensity = 0.79)).intensity!!, 0.0)
        assertEquals(0.79, toSet(CardioDraft(20, 0, 0, intensity = 0.789)).intensity!!, 1e-9)
        assertEquals(0.71, toSet(CardioDraft(20, 0, 0, intensity = 0.712)).intensity!!, 1e-9)
    }

    @Test
    fun `toSet leaves the intensity null when none is logged`() {
        assertNull(toSet(CardioDraft(20, 0, 0)).intensity)
    }

    @Test
    fun `toSet keeps an intensity of zero as zero, not as not logged`() {
        assertEquals(0.0, toSet(CardioDraft(20, 0, 0, intensity = 0.0)).intensity!!, 0.0)
    }

    @Test
    fun `toSet keeps the intensity inside 0 to 100`() {
        assertEquals(100.0, toSet(CardioDraft(20, 0, 0, intensity = 100.0)).intensity!!, 0.0)
        assertEquals(100.0, toSet(CardioDraft(20, 0, 0, intensity = 150.0)).intensity!!, 0.0)
    }

    @Test
    fun `toSet writes minutes, calories, distance and intensity together`() {
        val logged = toSet(CardioDraft(minutes = 45, calories = 520, distanceM = 18_400, intensity = 0.79))

        assertEquals(2700, logged.seconds)
        assertEquals(520, logged.calories)
        assertEquals(18_400, logged.distanceM)
        assertEquals(0.79, logged.intensity!!, 0.0)
        assertNull(logged.weightKg)
        assertNull(logged.reps)
    }

    // toDraft

    @Test
    fun `toDraft reads the intensity back`() {
        val read = CardioRules.toDraft(set(seconds = 1500, calories = 300, distanceM = 5000, intensity = 0.79))

        assertEquals(CardioDraft(minutes = 25, calories = 300, distanceM = 5000, intensity = 0.79), read)
    }

    @Test
    fun `toDraft of a set without intensity has none`() {
        assertNull(CardioRules.toDraft(set(seconds = 600)).intensity)
    }

    @Test
    fun `toDraft keeps an intensity of zero`() {
        assertEquals(0.0, CardioRules.toDraft(set(seconds = 600, intensity = 0.0)).intensity!!, 0.0)
    }

    @Test
    fun `a draft with an intensity round trips through a set`() {
        val draft = CardioDraft(minutes = 10, calories = 0, distanceM = 0, intensity = 12.0)

        assertEquals(draft, CardioRules.toDraft(toSet(draft)))
    }

    // isCardio and canLog

    @Test
    fun `a set with only an intensity is cardio`() {
        assertTrue(CardioRules.isCardio(set(intensity = 0.0)))
    }

    @Test
    fun `a set with no cardio values is not cardio`() {
        assertFalse(CardioRules.isCardio(set()))
    }

    @Test
    fun `an intensity alone cannot be logged`() {
        assertFalse(CardioRules.canLog(CardioDraft(0, 0, 0, intensity = 12.0)))
        assertFalse(CardioRules.canLog(CardioDraft(0, 0, 1000, intensity = 12.0)))
    }

    @Test
    fun `minutes or calories are what lets a cardio set be logged`() {
        assertTrue(CardioRules.canLog(CardioDraft(10, 0, 0, intensity = 0.79)))
        assertTrue(CardioRules.canLog(CardioDraft(0, 10, 0, intensity = 0.79)))
    }

    // prefill and sameAgain

    @Test
    fun `prefill carries the intensity of this session's last set`() {
        val lastTime = listOf(set(seconds = 1200, distanceM = 3000, intensity = 0.09))
        val thisSession = listOf(set(id = 2L, seconds = 900, intensity = 0.12))

        assertEquals(CardioDraft(15, 0, 0, intensity = 0.12), CardioRules.prefill(lastTime, thisSession))
    }

    @Test
    fun `prefill takes last time's intensity when this session has none`() {
        val lastTime = listOf(set(seconds = 1200, distanceM = 3000, intensity = 0.09))

        assertEquals(CardioDraft(20, 0, 3000, intensity = 0.09), CardioRules.prefill(lastTime, emptyList()))
    }

    @Test
    fun `prefill with no intensity logged before leaves the intensity null`() {
        assertNull(CardioRules.prefill(listOf(set(seconds = 1200)), emptyList()).intensity)
        assertNull(CardioRules.prefill(emptyList(), emptyList()).intensity)
    }

    @Test
    fun `sameAgain carries last time's intensity at the same index`() {
        val lastTime = listOf(set(seconds = 600, intensity = 0.07), set(id = 2L, position = 1, seconds = 300, intensity = 0.15))

        assertEquals(CardioDraft(5, 0, 0, intensity = 0.15), CardioRules.sameAgain(lastTime, listOf(set(seconds = 600))))
    }

    @Test
    fun `sameAgain is null when last time has no set at that index`() {
        assertNull(CardioRules.sameAgain(listOf(set(seconds = 600, intensity = 0.07)), listOf(set(seconds = 600), set(id = 2L))))
    }
}

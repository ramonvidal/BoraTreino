package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Bike console values carried by the cardio draft, the set it logs, and what the steppers start from (phase 42). */
class CardioValueDraftTest {
    private val at = Instant.parse("2026-11-23T18:00:00Z")

    private fun set(
        id: Long,
        sessionId: Long = 1L,
        seconds: Int? = null,
        calories: Int? = null,
        distanceM: Int? = null,
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
        position: Int = 0,
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
        watts = watts,
        heartRate = heartRate,
        wattsPerKg = wattsPerKg,
        tss = tss,
        intensityFactor = intensityFactor,
    )

    private fun toSet(draft: CardioDraft): WorkoutSet =
        CardioRules.toSet(id = 7L, sessionId = 2L, machineId = 1L, position = 3, draft = draft, loggedAt = at)

    private val full = CardioDraft(
        minutes = 20,
        calories = 180,
        distanceM = 5000,
        intensity = 12,
        watts = 160,
        heartRate = 142,
        wattsPerKg = 2.4,
        tss = 48,
        intensityFactor = 0.79,
    )

    // toSet

    @Test
    fun `toSet writes every value of the draft`() {
        val logged = toSet(full)

        assertEquals(1200, logged.seconds)
        assertEquals(180, logged.calories)
        assertEquals(5000, logged.distanceM)
        assertEquals(12, logged.intensity)
        assertEquals(160, logged.watts)
        assertEquals(142, logged.heartRate)
        assertEquals(2.4, logged.wattsPerKg!!, 1e-9)
        assertEquals(48, logged.tss)
        assertEquals(0.79, logged.intensityFactor!!, 1e-9)
    }

    @Test
    fun `toSet leaves the values that are not logged null`() {
        val logged = toSet(CardioDraft(minutes = 20, calories = 0, distanceM = 0))

        assertNull(logged.watts)
        assertNull(logged.heartRate)
        assertNull(logged.wattsPerKg)
        assertNull(logged.tss)
        assertNull(logged.intensityFactor)
    }

    @Test
    fun `toSet rounds watts per kg to one decimal`() {
        assertEquals(3.1, toSet(CardioDraft(20, 0, 0, wattsPerKg = 3.14)).wattsPerKg!!, 1e-9)
        assertEquals(3.2, toSet(CardioDraft(20, 0, 0, wattsPerKg = 3.16)).wattsPerKg!!, 1e-9)
    }

    @Test
    fun `toSet rounds the intensity factor to two decimals`() {
        assertEquals(0.79, toSet(CardioDraft(20, 0, 0, intensityFactor = 0.789)).intensityFactor!!, 1e-9)
        assertEquals(0.71, toSet(CardioDraft(20, 0, 0, intensityFactor = 0.712)).intensityFactor!!, 1e-9)
    }

    @Test
    fun `toSet keeps zero values as zero, not as not logged`() {
        val logged = toSet(CardioDraft(20, 0, 0, watts = 0, heartRate = 30, wattsPerKg = 0.0, tss = 0, intensityFactor = 0.0))

        assertEquals(0, logged.watts)
        assertEquals(30, logged.heartRate)
        assertEquals(0.0, logged.wattsPerKg!!, 0.0)
        assertEquals(0, logged.tss)
        assertEquals(0.0, logged.intensityFactor!!, 0.0)
    }

    @Test
    fun `toSet keeps the values inside their ranges`() {
        val logged = toSet(
            CardioDraft(20, 0, 0, watts = 2500, heartRate = 20, wattsPerKg = 12.0, tss = 1200, intensityFactor = 2.5),
        )

        assertEquals(2000, logged.watts)
        assertEquals(30, logged.heartRate)
        assertEquals(10.0, logged.wattsPerKg!!, 1e-9)
        assertEquals(999, logged.tss)
        assertEquals(2.0, logged.intensityFactor!!, 1e-9)
    }

    @Test
    fun `a draft with only a value and no time still writes that value`() {
        val logged = toSet(CardioDraft(0, 0, 0, watts = 150))

        assertNull(logged.seconds)
        assertEquals(150, logged.watts)
    }

    // toDraft and the round trip

    @Test
    fun `toDraft reads every value back from the set`() {
        val draft = CardioRules.toDraft(toSet(full))

        assertEquals(full, draft)
    }

    @Test
    fun `a draft with no values round trips to a draft with no values`() {
        val plain = CardioDraft(minutes = 15, calories = 90, distanceM = 0)

        assertEquals(plain, CardioRules.toDraft(toSet(plain)))
    }

    @Test
    fun `a draft with zero values round trips with the zeros kept`() {
        val zeros = CardioDraft(minutes = 15, calories = 0, distanceM = 0, watts = 0, tss = 0, intensityFactor = 0.0)

        assertEquals(zeros, CardioRules.toDraft(toSet(zeros)))
    }

    // isCardio

    @Test
    fun `a set with any one console value is cardio`() {
        assertTrue(CardioRules.isCardio(set(1L, watts = 150)))
        assertTrue(CardioRules.isCardio(set(2L, heartRate = 140)))
        assertTrue(CardioRules.isCardio(set(3L, wattsPerKg = 2.1)))
        assertTrue(CardioRules.isCardio(set(4L, tss = 0)))
        assertTrue(CardioRules.isCardio(set(5L, intensityFactor = 0.0)))
    }

    @Test
    fun `a set with no value at all is not cardio`() {
        assertFalse(CardioRules.isCardio(set(1L)))
    }

    // prefill and sameAgain

    @Test
    fun `prefill starts from this session's last set, values included`() {
        val lastTime = listOf(set(1L, sessionId = 9L, seconds = 600, watts = 90))
        val thisSession = listOf(set(2L, seconds = 1200, watts = 160, heartRate = 142, tss = 48, intensityFactor = 0.79))

        val draft = CardioRules.prefill(lastTime, thisSession)

        assertEquals(20, draft.minutes)
        assertEquals(160, draft.watts)
        assertEquals(142, draft.heartRate)
        assertEquals(48, draft.tss)
        assertEquals(0.79, draft.intensityFactor!!, 1e-9)
    }

    @Test
    fun `prefill falls back to last time's first set, values included, when this session has none`() {
        val lastTime = listOf(set(1L, sessionId = 9L, seconds = 600, watts = 90, wattsPerKg = 1.8))

        val draft = CardioRules.prefill(lastTime, emptyList())

        assertEquals(90, draft.watts)
        assertEquals(1.8, draft.wattsPerKg!!, 1e-9)
        assertNull(draft.tss)
    }

    @Test
    fun `prefill with no history has no values`() {
        val draft = CardioRules.prefill(emptyList(), emptyList())

        assertEquals(CardioDraft(CardioRules.DEFAULT_MINUTES, 0, 0), draft)
    }

    @Test
    fun `sameAgain carries the values of last time's set at the same index`() {
        val lastTime = listOf(
            set(1L, sessionId = 9L, seconds = 600, watts = 90),
            set(2L, sessionId = 9L, seconds = 900, watts = 170, intensityFactor = 0.85, position = 1),
        )

        val draft = CardioRules.sameAgain(lastTime, thisSession = listOf(set(3L, seconds = 600)))

        assertEquals(170, draft!!.watts)
        assertEquals(0.85, draft.intensityFactor!!, 1e-9)
    }

    @Test
    fun `sameAgain is null when last time has no set at that index`() {
        assertNull(CardioRules.sameAgain(listOf(set(1L, sessionId = 9L, seconds = 600)), listOf(set(2L), set(3L))))
    }
}

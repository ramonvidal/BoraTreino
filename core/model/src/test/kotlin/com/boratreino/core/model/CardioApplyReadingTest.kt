package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 43: CardioRules.applyReading fills the cardio draft from a bike console reading. */
class CardioApplyReadingTest {

    private val filled = CardioDraft(
        minutes = 20,
        calories = 100,
        distanceM = 500,
        intensity = 7,
        watts = 150,
        heartRate = 130,
        wattsPerKg = 2.0,
        tss = 40,
        intensityFactor = 0.75,
    )

    private fun reading(
        minutes: Int? = null,
        calories: Int? = null,
        distanceM: Int? = null,
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
    ) = BikeReading(minutes, calories, distanceM, watts, heartRate, wattsPerKg, tss, intensityFactor)

    @Test
    fun `every value the reading has replaces the draft's value`() {
        val full = reading(
            minutes = 46,
            calories = 397,
            distanceM = 17300,
            watts = 160,
            heartRate = 142,
            wattsPerKg = 1.2,
            tss = 48,
            intensityFactor = 0.79,
        )

        val result = CardioRules.applyReading(filled, full)

        assertEquals(46, result.minutes)
        assertEquals(397, result.calories)
        assertEquals(17300, result.distanceM)
        assertEquals(160, result.watts)
        assertEquals(142, result.heartRate)
        assertEquals(1.2, result.wattsPerKg!!, 0.0001)
        assertEquals(48, result.tss)
        assertEquals(0.79, result.intensityFactor!!, 0.0001)
    }

    @Test
    fun `values the reading does not have keep the draft's values`() {
        val result = CardioRules.applyReading(filled, reading(minutes = 46))

        assertEquals(CardioDraft(46, 100, 500, 7, 150, 130, 2.0, 40, 0.75), result)
    }

    @Test
    fun `a null on the draft stays null when the reading does not have the value`() {
        val draft = CardioDraft(20, 0, 0)

        val result = CardioRules.applyReading(draft, reading(calories = 397))

        assertEquals(397, result.calories)
        assertNull(result.watts)
        assertNull(result.heartRate)
        assertNull(result.wattsPerKg)
        assertNull(result.tss)
        assertNull(result.intensityFactor)
    }

    @Test
    fun `a value the reading has fills a draft that had none`() {
        val draft = CardioDraft(20, 0, 0)

        val result = CardioRules.applyReading(draft, reading(tss = 48, intensityFactor = 0.79))

        assertEquals(48, result.tss)
        assertEquals(0.79, result.intensityFactor!!, 0.0001)
        assertNull(result.watts)
    }

    @Test
    fun `an empty reading leaves the draft as it was`() {
        assertEquals(filled, CardioRules.applyReading(filled, reading()))
    }

    @Test
    fun `minutes above the stepper maximum are clamped`() {
        assertEquals(CardioRules.MAX_MINUTES, CardioRules.applyReading(filled, reading(minutes = 700)).minutes)
    }

    @Test
    fun `calories above the stepper maximum are clamped`() {
        assertEquals(CardioRules.MAX_CALORIES, CardioRules.applyReading(filled, reading(calories = 6000)).calories)
    }

    @Test
    fun `distance above the stepper maximum is clamped`() {
        assertEquals(CardioRules.MAX_DISTANCE_M, CardioRules.applyReading(filled, reading(distanceM = 250_000)).distanceM)
    }

    @Test
    fun `watts above the stepper maximum are clamped`() {
        assertEquals(CardioRules.MAX_WATTS, CardioRules.applyReading(filled, reading(watts = 2500)).watts)
    }

    @Test
    fun `a heart rate below the stepper minimum is clamped up`() {
        assertEquals(CardioRules.MIN_HEART_RATE, CardioRules.applyReading(filled, reading(heartRate = 20)).heartRate)
    }

    @Test
    fun `a heart rate above the stepper maximum is clamped down`() {
        assertEquals(CardioRules.MAX_HEART_RATE, CardioRules.applyReading(filled, reading(heartRate = 300)).heartRate)
    }

    @Test
    fun `watts per kilo above the stepper maximum are clamped`() {
        assertEquals(
            CardioRules.MAX_WATTS_PER_KG,
            CardioRules.applyReading(filled, reading(wattsPerKg = 12.0)).wattsPerKg!!,
            0.0001,
        )
    }

    @Test
    fun `TSS above the stepper maximum is clamped`() {
        assertEquals(CardioRules.MAX_TSS, CardioRules.applyReading(filled, reading(tss = 1500)).tss)
    }

    @Test
    fun `intensity factor above the stepper maximum is clamped`() {
        assertEquals(
            CardioRules.MAX_INTENSITY_FACTOR,
            CardioRules.applyReading(filled, reading(intensityFactor = 2.5)).intensityFactor!!,
            0.0001,
        )
    }

    @Test
    fun `the console intensity is never changed by a reading`() {
        assertEquals(7, CardioRules.applyReading(filled, reading(minutes = 46, intensityFactor = 0.79)).intensity)
        assertNull(CardioRules.applyReading(CardioDraft(20, 0, 0), reading(minutes = 46, tss = 48)).intensity)
    }
}

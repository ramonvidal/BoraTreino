package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The bike console steppers (phase 42): step sizes, start values, clamping, and null stays null going down. */
class CardioValueSteppersTest {

    // Start values

    @Test
    fun `the start values are 100 W, 120 bpm, 1 W per kg, 30 TSS and an IF of 0 point 70`() {
        assertEquals(100, CardioRules.START_WATTS)
        assertEquals(120, CardioRules.START_HEART_RATE)
        assertEquals(1.0, CardioRules.START_WATTS_PER_KG, 0.0)
        assertEquals(30, CardioRules.START_TSS)
        assertEquals(0.70, CardioRules.START_INTENSITY_FACTOR, 0.0)
    }

    // Watts: 5 W steps

    @Test
    fun `an unset watts goes to 100 when stepped up and stays unset when stepped down`() {
        assertEquals(100, CardioRules.stepWatts(null, up = true))
        assertNull(CardioRules.stepWatts(null, up = false))
    }

    @Test
    fun `watts step by 5 in both directions`() {
        assertEquals(165, CardioRules.stepWatts(160, up = true))
        assertEquals(155, CardioRules.stepWatts(160, up = false))
    }

    @Test
    fun `watts stop at 0 and at 2000`() {
        assertEquals(0, CardioRules.stepWatts(3, up = false))
        assertEquals(0, CardioRules.stepWatts(0, up = false))
        assertEquals(2000, CardioRules.stepWatts(2000, up = true))
        assertEquals(2000, CardioRules.stepWatts(1998, up = true))
    }

    // Heart rate: 1 bpm steps

    @Test
    fun `an unset heart rate goes to 120 when stepped up and stays unset when stepped down`() {
        assertEquals(120, CardioRules.stepHeartRate(null, up = true))
        assertNull(CardioRules.stepHeartRate(null, up = false))
    }

    @Test
    fun `heart rate steps by one in both directions`() {
        assertEquals(141, CardioRules.stepHeartRate(140, up = true))
        assertEquals(139, CardioRules.stepHeartRate(140, up = false))
    }

    @Test
    fun `heart rate stops at 30 and at 250`() {
        assertEquals(30, CardioRules.stepHeartRate(30, up = false))
        assertEquals(250, CardioRules.stepHeartRate(250, up = true))
        assertEquals(249, CardioRules.stepHeartRate(250, up = false))
    }

    // Watts per kg: 0.1 steps

    @Test
    fun `an unset watts per kg goes to 1 point 0 when stepped up and stays unset when stepped down`() {
        assertEquals(1.0, CardioRules.stepWattsPerKg(null, up = true)!!, 1e-9)
        assertNull(CardioRules.stepWattsPerKg(null, up = false))
    }

    @Test
    fun `watts per kg steps by 0 point 1 in both directions`() {
        assertEquals(1.1, CardioRules.stepWattsPerKg(1.0, up = true)!!, 1e-9)
        assertEquals(0.9, CardioRules.stepWattsPerKg(1.0, up = false)!!, 1e-9)
    }

    @Test
    fun `watts per kg stays on one decimal after many steps`() {
        var value: Double? = 2.3
        repeat(7) { value = CardioRules.stepWattsPerKg(value, up = true) }

        assertEquals(3.0, value!!, 1e-9)
    }

    @Test
    fun `watts per kg stops at 0 and at 10`() {
        assertEquals(0.0, CardioRules.stepWattsPerKg(0.0, up = false)!!, 1e-9)
        assertEquals(10.0, CardioRules.stepWattsPerKg(10.0, up = true)!!, 1e-9)
        assertEquals(9.9, CardioRules.stepWattsPerKg(10.0, up = false)!!, 1e-9)
    }

    // TSS: 1 steps

    @Test
    fun `an unset TSS goes to 30 when stepped up and stays unset when stepped down`() {
        assertEquals(30, CardioRules.stepTss(null, up = true))
        assertNull(CardioRules.stepTss(null, up = false))
    }

    @Test
    fun `TSS steps by one in both directions`() {
        assertEquals(51, CardioRules.stepTss(50, up = true))
        assertEquals(49, CardioRules.stepTss(50, up = false))
    }

    @Test
    fun `TSS stops at 0 and at 999`() {
        assertEquals(0, CardioRules.stepTss(0, up = false))
        assertEquals(999, CardioRules.stepTss(999, up = true))
        assertEquals(998, CardioRules.stepTss(999, up = false))
    }

    // Intensity factor: 0.01 steps

    @Test
    fun `an unset IF goes to 0 point 70 when stepped up and stays unset when stepped down`() {
        assertEquals(0.70, CardioRules.stepIntensityFactor(null, up = true)!!, 1e-9)
        assertNull(CardioRules.stepIntensityFactor(null, up = false))
    }

    @Test
    fun `IF steps by 0 point 01 in both directions`() {
        assertEquals(0.80, CardioRules.stepIntensityFactor(0.79, up = true)!!, 1e-9)
        assertEquals(0.78, CardioRules.stepIntensityFactor(0.79, up = false)!!, 1e-9)
    }

    @Test
    fun `IF has no float drift after many steps`() {
        var value: Double? = 0.70
        repeat(100) { value = CardioRules.stepIntensityFactor(value, up = true) }

        assertEquals(1.70, value!!, 1e-9)
    }

    @Test
    fun `IF stops at 0 and at 2 point 0`() {
        assertEquals(0.0, CardioRules.stepIntensityFactor(0.0, up = false)!!, 1e-9)
        assertEquals(2.0, CardioRules.stepIntensityFactor(2.0, up = true)!!, 1e-9)
        assertEquals(1.99, CardioRules.stepIntensityFactor(2.0, up = false)!!, 1e-9)
    }

    @Test
    fun `IF step from the start value lands on 0 point 71 going up`() {
        val first = CardioRules.stepIntensityFactor(null, up = true)

        assertEquals(0.71, CardioRules.stepIntensityFactor(first, up = true)!!, 1e-9)
    }
}

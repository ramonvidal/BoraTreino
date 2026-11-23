package com.boratreino.feature.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The cardio number formats: km, km/h, intensity, and hours with minutes. */
class ProgressCardioFormattingTest {
    private val us = Locale.US
    private val baianes = Locale("pt", "BR")

    @Test
    fun `km has one decimal`() {
        assertEquals("18.4", formatKm(18.4, us))
        assertEquals("20.0", formatKm(20.0, us))
    }

    @Test
    fun `metres become km with one decimal`() {
        assertEquals("18.4", formatKmFromMetres(18_432, us))
        assertEquals("92.4", formatKmFromMetres(92_400, us))
    }

    @Test
    fun `km uses the decimal comma in Baianês`() {
        assertEquals("18,4", formatKm(18.4, baianes))
    }

    @Test
    fun `speed has one decimal`() {
        assertEquals("24.5", formatSpeed(24.46, us))
        assertEquals("24.4", formatSpeed(24.44, us))
        assertEquals("10.0", formatSpeed(10.0, us))
    }

    @Test
    fun `a whole intensity has no decimal`() {
        assertEquals("12", formatIntensity(12.0, us))
        assertEquals("0", formatIntensity(0.0, us))
    }

    @Test
    fun `an intensity keeps up to two decimals`() {
        assertEquals("12.4", formatIntensity(12.4, us))
        assertEquals("0.79", formatIntensity(0.79, us))
        assertEquals("0.7", formatIntensity(0.7, us))
    }

    @Test
    fun `hours are whole hours of the minutes`() {
        assertEquals(6, hoursOf(375))
        assertEquals(0, hoursOf(59))
        assertEquals(1, hoursOf(60))
        assertEquals(0, hoursOf(-5))
    }

    @Test
    fun `the minutes left are the minutes after the whole hours`() {
        assertEquals(15, minutesPartOf(375))
        assertEquals(0, minutesPartOf(60))
        assertEquals(59, minutesPartOf(59))
        assertEquals(0, minutesPartOf(-5))
    }
}

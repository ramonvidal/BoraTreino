package com.boratreino.feature.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The bike console number formats (phase 42): watts and bpm, W/kg, TSS and IF. */
class ProgressBikeFormattingTest {
    private val us = Locale.US
    private val baianes = Locale("pt", "BR")

    @Test
    fun `watts are a whole number`() {
        assertEquals("160", formatPower(160.0, us))
        assertEquals("160", formatPower(159.6, us))
        assertEquals("0", formatPower(0.0, us))
    }

    @Test
    fun `heart rate in bpm is a whole number too`() {
        assertEquals("142", formatPower(142.0, us))
    }

    @Test
    fun `watts per kg has one decimal, even when it is a whole number`() {
        assertEquals("1.2", formatWattsPerKg(1.2, us))
        assertEquals("1.0", formatWattsPerKg(1.0, us))
        assertEquals("10.0", formatWattsPerKg(10.0, us))
    }

    @Test
    fun `watts per kg uses the decimal comma in Baianês`() {
        assertEquals("1,2", formatWattsPerKg(1.2, baianes))
    }

    @Test
    fun `TSS is a whole number`() {
        assertEquals("48", formatTss(48.0, us))
        assertEquals("48", formatTss(48.4, us))
        assertEquals("0", formatTss(0.0, us))
    }

    @Test
    fun `intensity factor always has two decimals`() {
        assertEquals("0.79", formatIntensityFactor(0.79, us))
        assertEquals("1.00", formatIntensityFactor(1.0, us))
        assertEquals("0.70", formatIntensityFactor(0.7, us))
        assertEquals("2.00", formatIntensityFactor(2.0, us))
    }

    @Test
    fun `intensity factor uses the decimal comma in Baianês`() {
        assertEquals("0,79", formatIntensityFactor(0.79, baianes))
    }
}

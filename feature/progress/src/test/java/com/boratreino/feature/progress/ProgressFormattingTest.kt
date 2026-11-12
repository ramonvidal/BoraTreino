package com.boratreino.feature.progress

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressFormattingTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun `a date in this year has no year`() {
        assertEquals("2 Nov", formatShortDate(LocalDate.of(2026, 11, 2), today, Locale.US))
    }

    @Test
    fun `the day has no leading zero`() {
        assertEquals("3 Sep", formatShortDate(LocalDate.of(2026, 9, 3), today, Locale.US))
    }

    @Test
    fun `a date in another year shows the year`() {
        assertEquals("3 Sep 2025", formatShortDate(LocalDate.of(2025, 9, 3), today, Locale.US))
    }

    @Test
    fun `a date in January of this year has no year`() {
        assertEquals("5 Jan", formatShortDate(LocalDate.of(2026, 1, 5), today, Locale.US))
    }

    // Days since

    @Test
    fun `days since a date three days back is three`() {
        assertEquals(3, daysSince(LocalDate.of(2026, 10, 30), today))
    }

    @Test
    fun `days since today is zero`() {
        assertEquals(0, daysSince(today, today))
    }

    @Test
    fun `days since a date in the future is zero`() {
        assertEquals(0, daysSince(LocalDate.of(2026, 11, 5), today))
    }

    // Trend change

    @Test
    fun `the trend change is the percent from the first value to the last`() {
        assertEquals(10.0, trendChangePercent(listOf(100.0, 110.0))!!, 1e-9)
        assertEquals(-10.0, trendChangePercent(listOf(100.0, 120.0, 90.0))!!, 1e-9)
    }

    @Test
    fun `the trend change ignores the values in between`() {
        assertEquals(0.0, trendChangePercent(listOf(80.0, 95.0, 60.0, 80.0))!!, 1e-9)
    }

    @Test
    fun `the trend change needs two values`() {
        assertNull(trendChangePercent(emptyList()))
        assertNull(trendChangePercent(listOf(100.0)))
    }

    @Test
    fun `the trend change is unknown from a first value of zero or less`() {
        assertNull(trendChangePercent(listOf(0.0, 5.0)))
        assertNull(trendChangePercent(listOf(-2.0, 5.0)))
    }

    // Signed percent

    @Test
    fun `a rise has a plus sign`() {
        assertEquals("+8 %", signedPercent(8.0))
    }

    @Test
    fun `a fall has a minus sign`() {
        assertEquals("-3 %", signedPercent(-3.0))
    }

    @Test
    fun `no change is a plain zero`() {
        assertEquals("0", signedPercent(0.0))
    }

    @Test
    fun `a change that rounds to nothing is a plain zero`() {
        assertEquals("0", signedPercent(0.4))
        assertEquals("0", signedPercent(-0.4))
    }

    @Test
    fun `the percent is rounded to a whole number`() {
        assertEquals("+8 %", signedPercent(7.6))
    }
}

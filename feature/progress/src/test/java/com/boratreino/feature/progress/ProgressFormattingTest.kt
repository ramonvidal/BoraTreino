package com.boratreino.feature.progress

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
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
}

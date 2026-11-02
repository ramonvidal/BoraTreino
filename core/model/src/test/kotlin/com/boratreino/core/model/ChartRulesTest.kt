package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartRulesTest {
    private fun day(year: Int, month: Int, dayOfMonth: Int): LocalDate = LocalDate.of(year, month, dayOfMonth)

    private fun assertEvenlySpaced(ticks: List<Double>) {
        val step = ticks[1] - ticks[0]
        assertTrue("step must be positive", step > 0)
        for (i in 1 until ticks.size) {
            assertEquals("gap $i", step, ticks[i] - ticks[i - 1], 1e-9)
        }
    }

    // niceScale

    @Test
    fun `zero to a hundred with four ticks gives clean ticks`() {
        val scale = ChartRules.niceScale(0.0, 100.0, targetTicks = 4)

        assertEquals(listOf(0.0, 50.0, 100.0), scale.ticks)
        assertEquals(0.0, scale.min, 0.0)
        assertEquals(100.0, scale.max, 0.0)
    }

    @Test
    fun `bounds are multiples of the step that enclose the data`() {
        val scale = ChartRules.niceScale(97.0, 123.0, targetTicks = 4)

        assertEquals(90.0, scale.min, 0.0)
        assertEquals(130.0, scale.max, 0.0)
        assertEquals(listOf(90.0, 100.0, 110.0, 120.0, 130.0), scale.ticks)
        assertEquals(scale.min, scale.ticks.first(), 0.0)
        assertEquals(scale.max, scale.ticks.last(), 0.0)
        assertEvenlySpaced(scale.ticks)
    }

    @Test
    fun `ticks have no floating point noise`() {
        val scale = ChartRules.niceScale(0.0, 0.3, targetTicks = 4)

        assertEquals(listOf(0.0, 0.1, 0.2, 0.3), scale.ticks)
        assertTrue(scale.ticks.all { it == it.toBigDecimal().setScale(9, java.math.RoundingMode.HALF_UP).toDouble() })
    }

    @Test
    fun `equal min and max is padded one step each side`() {
        val scale = ChartRules.niceScale(100.0, 100.0, targetTicks = 4)

        assertEquals(listOf(90.0, 100.0, 110.0), scale.ticks)
        assertEquals(90.0, scale.min, 0.0)
        assertEquals(110.0, scale.max, 0.0)
    }

    @Test
    fun `equal min and max away from zero still encloses the value`() {
        val scale = ChartRules.niceScale(5.0, 5.0, targetTicks = 4)

        assertTrue(scale.min < 5.0)
        assertTrue(scale.max > 5.0)
        assertTrue(5.0 in scale.ticks)
        assertEvenlySpaced(scale.ticks)
    }

    @Test
    fun `equal zero min and max is never below zero`() {
        val scale = ChartRules.niceScale(0.0, 0.0)

        assertTrue(scale.min >= 0.0)
        assertTrue(scale.max > scale.min)
        assertEvenlySpaced(scale.ticks)
    }

    @Test
    fun `a non-negative input never gives a scale below zero`() {
        val inputs = listOf(
            0.0 to 0.0,
            0.0 to 1.0,
            0.2 to 0.3,
            1.0 to 1.0,
            3.0 to 7.0,
            0.001 to 0.002,
            0.0 to 100.0,
            97.0 to 123.0,
            4000.0 to 4100.0,
        )
        for ((min, max) in inputs) {
            val scale = ChartRules.niceScale(min, max)
            assertTrue("min of $min..$max", scale.min >= 0.0)
            assertTrue("encloses min of $min..$max", scale.min <= min)
            assertTrue("encloses max of $min..$max", scale.max >= max)
            assertEquals(scale.min, scale.ticks.first(), 0.0)
            assertEquals(scale.max, scale.ticks.last(), 0.0)
            assertEvenlySpaced(scale.ticks)
        }
    }

    @Test
    fun `swapped min and max give the same scale`() {
        assertEquals(ChartRules.niceScale(97.0, 123.0), ChartRules.niceScale(123.0, 97.0))
    }

    @Test
    fun `non finite input counts as zero`() {
        assertEquals(ChartRules.niceScale(0.0, 100.0), ChartRules.niceScale(Double.NaN, 100.0))
        assertEquals(ChartRules.niceScale(0.0, 0.0), ChartRules.niceScale(Double.NaN, Double.POSITIVE_INFINITY))
        assertEquals(ChartRules.niceScale(0.0, 0.0), ChartRules.niceScale(0.0, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `fewer than two target ticks is treated as two`() {
        val two = ChartRules.niceScale(0.0, 100.0, targetTicks = 2)

        assertEquals(listOf(0.0, 100.0), two.ticks)
        assertEquals(two, ChartRules.niceScale(0.0, 100.0, targetTicks = 1))
        assertEquals(two, ChartRules.niceScale(0.0, 100.0, targetTicks = 0))
    }

    // timeFractions

    @Test
    fun `time fractions run from zero at the start to one at the end`() {
        val start = day(2026, 11, 1)
        val end = day(2026, 11, 11)
        val fractions = ChartRules.timeFractions(listOf(start, end), start, end)

        assertEquals(0f, fractions[0], 1e-6f)
        assertEquals(1f, fractions[1], 1e-6f)
    }

    @Test
    fun `time fractions are proportional to days`() {
        val start = day(2026, 11, 1)
        val end = day(2026, 11, 11)
        val dates = listOf(day(2026, 11, 3), day(2026, 11, 6))
        val fractions = ChartRules.timeFractions(dates, start, end)

        assertEquals(0.2f, fractions[0], 1e-6f)
        assertEquals(0.5f, fractions[1], 1e-6f)
    }

    @Test
    fun `a single day span gives one half for every date`() {
        val day = day(2026, 11, 2)

        assertEquals(listOf(0.5f, 0.5f), ChartRules.timeFractions(listOf(day, day), day, day))
    }

    @Test
    fun `an inverted span gives one half for every date`() {
        val fractions = ChartRules.timeFractions(
            listOf(day(2026, 11, 3)),
            day(2026, 11, 5),
            day(2026, 11, 1),
        )

        assertEquals(listOf(0.5f), fractions)
    }

    @Test
    fun `dates outside the span are clamped`() {
        val start = day(2026, 11, 1)
        val end = day(2026, 11, 11)
        val fractions = ChartRules.timeFractions(listOf(day(2026, 10, 25), day(2026, 11, 20)), start, end)

        assertEquals(0f, fractions[0], 0f)
        assertEquals(1f, fractions[1], 0f)
    }

    @Test
    fun `no dates give no fractions`() {
        assertTrue(ChartRules.timeFractions(emptyList(), day(2026, 11, 1), day(2026, 11, 11)).isEmpty())
    }

    // axisTicks

    @Test
    fun `a long span gives first of month ticks inside the range`() {
        val ticks = ChartRules.axisTicks(day(2026, 1, 15), day(2026, 12, 31))

        assertTrue(ticks.all { it.dayOfMonth == 1 })
        assertTrue(ticks.all { !it.isBefore(day(2026, 1, 15)) && !it.isAfter(day(2026, 12, 31)) })
    }

    @Test
    fun `a long span keeps at most maxTicks by thinning months`() {
        val ticks = ChartRules.axisTicks(day(2026, 1, 15), day(2026, 12, 31), maxTicks = 5)

        // Eleven months from February: every third month is kept, so four ticks.
        assertEquals(listOf(day(2026, 2, 1), day(2026, 5, 1), day(2026, 8, 1), day(2026, 11, 1)), ticks)
    }

    @Test
    fun `every month is kept when they fit`() {
        val ticks = ChartRules.axisTicks(day(2026, 1, 1), day(2026, 12, 31), maxTicks = 12)

        assertEquals(12, ticks.size)
        assertEquals(day(2026, 1, 1), ticks.first())
        assertEquals(day(2026, 12, 1), ticks.last())
    }

    @Test
    fun `a very long range stays within maxTicks and keeps yearly spacing`() {
        val ticks = ChartRules.axisTicks(day(2020, 1, 1), day(2030, 12, 31), maxTicks = 5)

        assertTrue("at most 5, was ${ticks.size}", ticks.size <= 5)
        assertTrue(ticks.all { it.dayOfMonth == 1 })
        val gaps = ticks.zipWithNext { a, b -> java.time.temporal.ChronoUnit.MONTHS.between(a, b) }
        assertTrue(gaps.isNotEmpty())
        assertTrue(gaps.all { it % 12 == 0L && it == gaps.first() })
    }

    @Test
    fun `a span of exactly 45 days uses months`() {
        val ticks = ChartRules.axisTicks(day(2026, 10, 1), day(2026, 11, 15))

        assertEquals(listOf(day(2026, 10, 1), day(2026, 11, 1)), ticks)
    }

    @Test
    fun `a span under 45 days uses every seven days from the start`() {
        val start = day(2026, 10, 1)
        val ticks = ChartRules.axisTicks(start, day(2026, 11, 14))

        assertEquals(listOf(day(2026, 10, 1), day(2026, 10, 8), day(2026, 10, 15), day(2026, 10, 22),
            day(2026, 10, 29), day(2026, 11, 5), day(2026, 11, 12)), ticks)
    }

    @Test
    fun `a 42 day span gives seven weekly ticks even with the default maxTicks`() {
        val start = day(2026, 10, 1)
        val ticks = ChartRules.axisTicks(start, day(2026, 11, 12))

        assertEquals(7, ticks.size)
        assertEquals(start, ticks.first())
        assertEquals(day(2026, 11, 12), ticks.last())
    }

    @Test
    fun `a span under 45 days is not thinned`() {
        val ticks = ChartRules.axisTicks(day(2026, 10, 1), day(2026, 11, 12), maxTicks = 2)

        assertEquals(7, ticks.size)
    }

    @Test
    fun `an inverted range gives no ticks`() {
        assertTrue(ChartRules.axisTicks(day(2026, 11, 5), day(2026, 11, 1)).isEmpty())
    }

    @Test
    fun `a single day range gives one tick at its start`() {
        assertEquals(listOf(day(2026, 11, 2)), ChartRules.axisTicks(day(2026, 11, 2), day(2026, 11, 2)))
    }
}

package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Weekly trend of a series. All expected numbers are worked out by hand from the least-squares formulas and the
 * Student t table (two-sided 95%). Day 0 is Monday 2 November 2026; "week w" means day 7 x w.
 */
class TrendRulesTest {
    private val base: LocalDate = LocalDate.of(2026, 11, 2)

    private fun on(day: Int): LocalDate = base.plusDays(day.toLong())

    private fun series(vararg values: Double, step: Int = 7): List<Pair<LocalDate, Double>> =
        values.mapIndexed { i, v -> on(i * step) to v }

    private fun set(id: Long, sessionId: Long, weightKg: Double, date: LocalDate) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = 1L,
        position = 0,
        weightKg = weightKg,
        reps = 1,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
    )

    private fun point(sessionId: Long, date: LocalDate, value: Double) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = value,
        topSet = set(sessionId, sessionId, value, date),
        setCount = 1,
    )

    private fun progress(
        points: List<SessionPoint> = emptyList(),
        oneRm: List<SessionPoint> = emptyList(),
        cardio: Map<ProgressMetric, List<SessionPoint>> = emptyMap(),
    ): MachineProgress {
        val machine = Machine(1L, "Machine", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false)
        return MachineProgress(machine, ProgressMetric.WEIGHT, points, oneRmPoints = oneRm, cardioPoints = cardio)
    }

    // Perfect lines

    @Test
    fun `three points a week apart give slope 2 per week with a collapsed interval`() {
        val fit = TrendRules.fit(listOf(on(0) to 1.0, on(7) to 3.0, on(14) to 5.0))

        assertNotNull(fit)
        assertEquals(3, fit!!.n)
        assertEquals(2.0, fit.slopePerWeek, 1e-9)
        assertEquals(2.0, fit.slopeLow, 1e-9)
        assertEquals(2.0, fit.slopeHigh, 1e-9)
        assertEquals(1.0, fit.r2, 1e-9)
        assertEquals(5.0, fit.fittedAtLast, 1e-9)
        assertEquals(14, fit.spanDays)
        assertEquals(0.0, fit.residualSd, 1e-12)
        assertEquals(12.706, fit.tCritical, 1e-9)
    }

    @Test
    fun `slope is per week when the points are one day apart`() {
        // value = 2 x day + 1, so 2 per day, which is 14 per week.
        val fit = TrendRules.fit(listOf(on(0) to 1.0, on(1) to 3.0, on(2) to 5.0))

        assertNotNull(fit)
        assertEquals(14.0, fit!!.slopePerWeek, 1e-9)
        assertEquals(14.0, fit.slopeHigh, 1e-9)
        assertEquals(2, fit.spanDays)
    }

    @Test
    fun `a fit needs at least three points`() {
        assertNull(TrendRules.fit(listOf(on(0) to 1.0, on(7) to 3.0)))
        assertNull(TrendRules.fit(emptyList()))
    }

    @Test
    fun `points that all share one date give no fit`() {
        assertNull(TrendRules.fit(listOf(on(3) to 1.0, on(3) to 2.0, on(3) to 9.0)))
    }

    @Test
    fun `several points on one date are all used`() {
        // Two points on day 0 (both value 1) and one on day 7 (value 3): the line through (0,1) and (7,3).
        val fit = TrendRules.fit(listOf(on(0) to 1.0, on(0) to 1.0, on(7) to 3.0))

        assertNotNull(fit)
        assertEquals(3, fit!!.n)
        assertEquals(2.0, fit.slopePerWeek, 1e-9)
        assertEquals(1.0, fit.r2, 1e-9)
        assertEquals(7, fit.spanDays)
    }

    @Test
    fun `a constant series has slope 0 and r2 0 with a collapsed interval`() {
        val fit = TrendRules.fit(series(5.0, 5.0, 5.0, 5.0, 5.0, step = 3))

        assertNotNull(fit)
        assertEquals(0.0, fit!!.slopePerWeek, 1e-12)
        assertEquals(0.0, fit.slopeLow, 1e-12)
        assertEquals(0.0, fit.slopeHigh, 1e-12)
        assertEquals(0.0, fit.r2, 1e-12)
        assertEquals(5.0, fit.fittedAtLast, 1e-12)
    }

    // A noisy series worked out by hand
    // Weeks 0..5 with values 1, 2, 4, 4, 6, 7. Mean week 2.5, mean value 4, Sxx = 17.5 (weeks),
    // Sxy = 21, so the slope is 21 / 17.5 = 1.2 per week. Fitted values 1, 2.2, 3.4, 4.6, 5.8, 7,
    // residuals 0, -0.2, 0.6, -0.6, 0.2, 0, so SSE = 0.8 and the residual SD is sqrt(0.8 / 4) = sqrt(0.2).
    // SYY = 26, r2 = 1 - 0.8 / 26 = 25.2 / 26. The interval is 2.776 x sqrt(0.2) / sqrt(17.5) = 0.296767 wide.

    private val noisy = series(1.0, 2.0, 4.0, 4.0, 6.0, 7.0)

    @Test
    fun `a noisy series gives the hand computed slope and interval`() {
        val fit = TrendRules.fit(noisy)

        assertNotNull(fit)
        assertEquals(6, fit!!.n)
        assertEquals(1.2, fit.slopePerWeek, 1e-9)
        assertEquals(0.903233, fit.slopeLow, 1e-4)
        assertEquals(1.496767, fit.slopeHigh, 1e-4)
        assertEquals(25.2 / 26.0, fit.r2, 1e-9)
        assertEquals(7.0, fit.fittedAtLast, 1e-9)
        assertEquals(35, fit.spanDays)
        assertEquals(Math.sqrt(0.2), fit.residualSd, 1e-9)
        assertEquals(2.776, fit.tCritical, 1e-9)
        assertEquals(17.5, fit.meanDay - base.toEpochDay(), 1e-9)
        assertEquals(857.5, fit.sxx, 1e-9)
    }

    @Test
    fun `a noisy rising series is RISING once it has five points over two weeks`() {
        val result = TrendRules.verdict(noisy)

        assertEquals(TrendVerdict.RISING, result.verdict)
        assertEquals(6, result.points)
        assertEquals(5, result.neededPoints)
        assertEquals(14, result.neededSpanDays)
    }

    @Test
    fun `a noisy falling series is FALLING with the mirrored interval`() {
        val falling = series(-1.0, -2.0, -4.0, -4.0, -6.0, -7.0)
        val fit = TrendRules.fit(falling)!!

        assertEquals(-1.2, fit.slopePerWeek, 1e-9)
        assertEquals(-1.496767, fit.slopeLow, 1e-4)
        assertEquals(-0.903233, fit.slopeHigh, 1e-4)
        assertEquals(TrendVerdict.FALLING, TrendRules.verdict(falling).verdict)
    }

    @Test
    fun `a noisy series whose interval crosses zero is PLATEAU`() {
        // Values 0, 3, 0, 3, 0, 3 over weeks 0..5: Sxy = 4.5, slope 4.5 / 17.5 = 0.2571429,
        // SSE = 13.5 - 4.5^2 / 17.5 = 12.342857, residual SD = sqrt(12.342857 / 4) = 1.756620,
        // margin = 2.776 x 1.756620 / sqrt(17.5) = 1.165681, so the interval is about -0.908538 .. 1.422824.
        val zigzag = series(0.0, 3.0, 0.0, 3.0, 0.0, 3.0)
        val fit = TrendRules.fit(zigzag)!!
        val result = TrendRules.verdict(zigzag)

        assertEquals(0.2571429, fit.slopePerWeek, 1e-6)
        assertEquals(-0.908538, fit.slopeLow, 1e-3)
        assertEquals(1.422824, fit.slopeHigh, 1e-3)
        assertTrue(fit.slopeLow < 0.0 && fit.slopeHigh > 0.0)
        assertEquals(TrendVerdict.PLATEAU, result.verdict)
    }

    @Test
    fun `a constant series with enough data is PLATEAU`() {
        val flat = listOf(on(0) to 5.0, on(3) to 5.0, on(7) to 5.0, on(10) to 5.0, on(14) to 5.0)
        val result = TrendRules.verdict(flat)

        assertEquals(TrendVerdict.PLATEAU, result.verdict)
        val fit = result.fit!!
        assertEquals(0.0, fit.slopePerWeek, 1e-12)
        assertEquals(14, fit.spanDays)
    }

    @Test
    fun `a perfect falling line is FALLING with slope minus 2`() {
        val falling = listOf(on(0) to 10.0, on(7) to 8.0, on(14) to 6.0, on(21) to 4.0, on(28) to 2.0)
        val result = TrendRules.verdict(falling)

        assertEquals(TrendVerdict.FALLING, result.verdict)
        val fit = result.fit!!
        assertEquals(-2.0, fit.slopePerWeek, 1e-9)
        assertEquals(-2.0, fit.slopeHigh, 1e-9)
    }

    // Verdict minimums

    @Test
    fun `four points are not enough even on a perfect line`() {
        val points = listOf(on(0) to 1.0, on(7) to 3.0, on(7) to 3.0, on(14) to 5.0)
        val result = TrendRules.verdict(points)

        assertEquals(TrendVerdict.NOT_ENOUGH_DATA, result.verdict)
        assertNotNull(result.fit)
        assertEquals(4, result.points)
        assertEquals(5, result.neededPoints)
        assertEquals(14, result.neededSpanDays)
    }

    @Test
    fun `five points spanning only 12 days are not enough`() {
        // value = day, so the slope is 1 per day = 7 per week, but the span is 12 days.
        val points = listOf(on(0) to 0.0, on(3) to 3.0, on(6) to 6.0, on(9) to 9.0, on(12) to 12.0)
        val result = TrendRules.verdict(points)

        assertEquals(TrendVerdict.NOT_ENOUGH_DATA, result.verdict)
        val fit = result.fit!!
        assertEquals(7.0, fit.slopePerWeek, 1e-9)
        assertEquals(12, fit.spanDays)
    }

    @Test
    fun `five points spanning exactly 14 days are enough`() {
        val points = listOf(on(0) to 0.0, on(3) to 3.0, on(7) to 7.0, on(10) to 10.0, on(14) to 14.0)
        val result = TrendRules.verdict(points)

        assertEquals(TrendVerdict.RISING, result.verdict)
        assertEquals(7.0, result.fit!!.slopePerWeek, 1e-9)
    }

    @Test
    fun `the default minimums are five points and fourteen days`() {
        assertEquals(5, TrendRules.MIN_POINTS)
        assertEquals(14, TrendRules.MIN_SPAN_DAYS)
    }

    @Test
    fun `the minimums can be lowered by the caller`() {
        val result = TrendRules.verdict(listOf(on(0) to 1.0, on(7) to 3.0, on(14) to 5.0), minPoints = 3)

        assertEquals(TrendVerdict.RISING, result.verdict)
        assertEquals(3, result.neededPoints)
    }

    @Test
    fun `fewer than three points give no fit and NOT_ENOUGH_DATA`() {
        val result = TrendRules.verdict(listOf(on(0) to 1.0, on(7) to 3.0))

        assertNull(result.fit)
        assertEquals(TrendVerdict.NOT_ENOUGH_DATA, result.verdict)
    }

    // t table

    @Test
    fun `the t table has the spot values for two-sided 95 percent`() {
        assertEquals(12.706, TrendRules.tCritical(1), 1e-9)
        assertEquals(12.706, TrendRules.tCritical(0), 1e-9)
        assertEquals(4.303, TrendRules.tCritical(2), 1e-9)
        assertEquals(2.776, TrendRules.tCritical(4), 1e-9)
        assertEquals(2.228, TrendRules.tCritical(10), 1e-9)
        assertEquals(2.042, TrendRules.tCritical(30), 1e-9)
        assertEquals(1.96, TrendRules.tCritical(31), 1e-9)
        assertEquals(1.96, TrendRules.tCritical(100), 1e-9)
    }

    // Forecast

    @Test
    fun `the forecast four weeks ahead is the line value with a widened prediction band`() {
        val fit = TrendRules.fit(noisy)!!
        val f = TrendRules.forecast(fit, 4.0)

        // Value: 7 + 1.2 x 4 = 11.8. Band: 2.776 x sqrt(0.2) x sqrt(1 + 1/6 + 6.5^2 / 17.5) = 2.349274.
        assertEquals(4.0, f.weeksAhead, 1e-12)
        assertEquals(11.8, f.value, 1e-9)
        assertEquals(9.450726, f.low, 1e-4)
        assertEquals(14.149274, f.high, 1e-4)
    }

    @Test
    fun `the prediction band widens with the distance from the data`() {
        val fit = TrendRules.fit(noisy)!!
        val now = TrendRules.forecast(fit, 0.0)
        val four = TrendRules.forecast(fit, 4.0)
        val eight = TrendRules.forecast(fit, 8.0)

        // At the last date the band is 2.776 x sqrt(0.2) x sqrt(1 + 1/6 + 2.5^2 / 17.5) = 1.532498.
        assertEquals(7.0, now.value, 1e-9)
        assertEquals(5.467502, now.low, 1e-3)
        assertEquals(8.532498, now.high, 1e-3)

        val widthNow = now.high - now.low
        val widthFour = four.high - four.low
        val widthEight = eight.high - eight.low
        assertTrue(widthNow < widthFour)
        assertTrue(widthFour < widthEight)
    }

    @Test
    fun `negative weeks step back along the fitted line`() {
        val fit = TrendRules.fit(noisy)!!
        val back = TrendRules.forecast(fit, -2.0)

        // 7 - 2 x 1.2 = 4.6, which is the fitted value at week 3.
        assertEquals(-2.0, back.weeksAhead, 1e-12)
        assertEquals(4.6, back.value, 1e-9)
        assertTrue(back.low < back.value && back.value < back.high)
    }

    // Series choice for a machine

    @Test
    fun `the estimated 1RM series is used when there is one`() {
        val oneRm = series(100.0, 102.0, 104.0, 106.0, 108.0).mapIndexed { i, p ->
            point(i + 1L, p.first, p.second)
        }
        val speed = series(10.0, 11.0, 12.0, 13.0, 14.0).mapIndexed { i, p ->
            point(i + 11L, p.first, p.second)
        }
        val result = TrendRules.forMachine(progress(oneRm = oneRm, cardio = mapOf(ProgressMetric.SPEED to speed)))

        assertEquals(TrendVerdict.RISING, result.verdict)
        assertEquals(2.0, result.fit!!.slopePerWeek, 1e-9)
    }

    @Test
    fun `cardio speed is used when there is no 1RM series`() {
        val speed = series(10.0, 11.0, 12.0, 13.0, 14.0).mapIndexed { i, p ->
            point(i + 11L, p.first, p.second)
        }
        val defaults = series(5.0, 4.0, 3.0, 2.0, 1.0).mapIndexed { i, p -> point(i + 1L, p.first, p.second) }
        val result = TrendRules.forMachine(progress(points = defaults, cardio = mapOf(ProgressMetric.SPEED to speed)))

        assertEquals(TrendVerdict.RISING, result.verdict)
        assertEquals(1.0, result.fit!!.slopePerWeek, 1e-9)
        assertEquals(5, result.points)
    }

    @Test
    fun `the default points are used when there is neither`() {
        val defaults = series(5.0, 4.0, 3.0, 2.0, 1.0).mapIndexed { i, p -> point(i + 1L, p.first, p.second) }
        val result = TrendRules.forMachine(progress(points = defaults))

        assertEquals(TrendVerdict.FALLING, result.verdict)
        assertEquals(-1.0, result.fit!!.slopePerWeek, 1e-9)
    }

    @Test
    fun `an empty cardio speed list does not count as a series`() {
        val defaults = series(5.0, 4.0, 3.0, 2.0, 1.0).mapIndexed { i, p -> point(i + 1L, p.first, p.second) }
        val result = TrendRules.forMachine(
            progress(points = defaults, cardio = mapOf(ProgressMetric.SPEED to emptyList())),
        )

        assertEquals(-1.0, result.fit!!.slopePerWeek, 1e-9)
    }
}

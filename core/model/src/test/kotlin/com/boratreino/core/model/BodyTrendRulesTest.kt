package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Smoothing of body measurements and the waist / hip ratio. Expected numbers are worked out by hand. */
class BodyTrendRulesTest {
    private fun nov(day: Int): LocalDate = LocalDate.of(2026, 11, day)

    private fun measurement(day: Int, type: MeasurementType, cm: Double) =
        BodyMeasurement(date = nov(day), type = type, valueCm = cm)

    @Test
    fun `the first ewma point is unchanged`() {
        val out = BodyTrendRules.ewma(listOf(nov(1) to 80.0, nov(8) to 78.0))

        assertEquals(nov(1), out[0].first)
        assertEquals(80.0, out[0].second, 1e-12)
    }

    @Test
    fun `a seven day gap moves the average by alpha`() {
        // Effective alpha for a 7 day gap is 1 - (1 - 0.3)^1 = 0.3, so 80 + 0.3 x (78 - 80) = 79.4.
        val out = BodyTrendRules.ewma(listOf(nov(1) to 80.0, nov(8) to 78.0))

        assertEquals(2, out.size)
        assertEquals(nov(8), out[1].first)
        assertEquals(79.4, out[1].second, 1e-9)
    }

    @Test
    fun `the default alpha is 0 point 3`() {
        val explicit = BodyTrendRules.ewma(listOf(nov(1) to 80.0, nov(8) to 78.0), alpha = 0.3)
        val default = BodyTrendRules.ewma(listOf(nov(1) to 80.0, nov(8) to 78.0))

        assertEquals(explicit, default)
    }

    @Test
    fun `a fourteen day gap uses effective alpha 0 point 51`() {
        // 1 - 0.7^2 = 0.51, so 80 + 0.51 x (78 - 80) = 78.98.
        val out = BodyTrendRules.ewma(listOf(nov(1) to 80.0, nov(15) to 78.0))

        assertEquals(78.98, out[1].second, 1e-9)
    }

    @Test
    fun `a three day gap uses the fractional effective alpha`() {
        // Effective alpha = 1 - 0.7^(3/7) = 0.1417508, so 80 + 0.1417508 x (-2) = 79.7164984.
        val out = BodyTrendRules.ewma(listOf(nov(1) to 80.0, nov(4) to 78.0))

        assertEquals(79.7165, out[1].second, 1e-4)
    }

    @Test
    fun `points on the same date are merged into their mean first`() {
        // Nov 1 merges to (80 + 82) / 2 = 81, then 81 + 0.3 x (78 - 81) = 80.1.
        val out = BodyTrendRules.ewma(listOf(nov(1) to 80.0, nov(1) to 82.0, nov(8) to 78.0))

        assertEquals(2, out.size)
        assertEquals(81.0, out[0].second, 1e-9)
        assertEquals(80.1, out[1].second, 1e-9)
    }

    @Test
    fun `ewma output is ascending by date even when the input is not`() {
        val out = BodyTrendRules.ewma(listOf(nov(15) to 78.0, nov(1) to 80.0, nov(8) to 79.0))

        assertEquals(listOf(nov(1), nov(8), nov(15)), out.map { it.first })
    }

    @Test
    fun `no points give no ewma`() {
        assertTrue(BodyTrendRules.ewma(emptyList()).isEmpty())
    }

    @Test
    fun `minimum body points is three`() {
        assertEquals(3, BodyTrendRules.MIN_BODY_POINTS)
    }

    @Test
    fun `waist to hip ratio uses only dates with both and is ascending`() {
        val out = BodyTrendRules.waistHipRatio(
            listOf(
                measurement(5, MeasurementType.WAIST, 88.0),
                measurement(5, MeasurementType.HIPS, 98.0),
                measurement(1, MeasurementType.WAIST, 90.0),
                measurement(1, MeasurementType.HIPS, 100.0),
                measurement(3, MeasurementType.WAIST, 85.0),
                measurement(4, MeasurementType.HIPS, 99.0),
                measurement(6, MeasurementType.CHEST, 100.0),
            ),
        )

        // Nov 1: 90 / 100 = 0.9. Nov 5: 88 / 98 = 0.897959... Nov 3 and Nov 4 have one side only. Chest is ignored.
        assertEquals(2, out.size)
        assertEquals(nov(1), out[0].first)
        assertEquals(0.9, out[0].second, 1e-12)
        assertEquals(nov(5), out[1].first)
        assertEquals(88.0 / 98.0, out[1].second, 1e-12)
    }

    @Test
    fun `waist to hip ratio is not rounded`() {
        val out = BodyTrendRules.waistHipRatio(
            listOf(measurement(2, MeasurementType.WAIST, 1.0), measurement(2, MeasurementType.HIPS, 3.0)),
        )

        assertEquals(1.0 / 3.0, out.single().second, 1e-15)
    }

    @Test
    fun `no waist or hip measurements give no ratio`() {
        assertTrue(BodyTrendRules.waistHipRatio(emptyList()).isEmpty())
        assertTrue(BodyTrendRules.waistHipRatio(listOf(measurement(2, MeasurementType.WAIST, 80.0))).isEmpty())
    }
}

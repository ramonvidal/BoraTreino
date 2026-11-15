package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MeasurementRulesTest {
    private val nov1 = LocalDate.of(2026, 11, 1)
    private val oct1 = LocalDate.of(2026, 10, 1)
    private val oct15 = LocalDate.of(2026, 10, 15)
    private val oct20 = LocalDate.of(2026, 10, 20)
    private val nov10 = LocalDate.of(2026, 11, 10)

    private fun row(id: Long, date: LocalDate, type: MeasurementType, cm: Double) =
        BodyMeasurement(id = id, date = date, type = type, valueCm = cm)

    // Constants

    @Test
    fun `the step is half a centimetre and the range is ten to three hundred centimetres`() {
        assertEquals(0.5, MeasurementRules.STEP_CM, 0.0)
        assertEquals(10.0, MeasurementRules.MIN_CM, 0.0)
        assertEquals(300.0, MeasurementRules.MAX_CM, 0.0)
    }

    @Test
    fun `the types are waist, hips, chest, arm, thigh and neck in that order`() {
        assertEquals(
            listOf(
                MeasurementType.WAIST,
                MeasurementType.HIPS,
                MeasurementType.CHEST,
                MeasurementType.ARM,
                MeasurementType.THIGH,
                MeasurementType.NECK,
            ),
            MeasurementType.entries.toList(),
        )
    }

    // clamp

    @Test
    fun `a value already on a half step stays as it is`() {
        assertEquals(80.5, MeasurementRules.clamp(80.5), 0.0)
        assertEquals(80.0, MeasurementRules.clamp(80.0), 0.0)
    }

    @Test
    fun `a quarter of a centimetre rounds up to the next half`() {
        assertEquals(82.5, MeasurementRules.clamp(82.25), 0.0)
    }

    @Test
    fun `below a quarter rounds down to the lower half`() {
        assertEquals(82.0, MeasurementRules.clamp(82.24), 0.0)
        assertEquals(82.5, MeasurementRules.clamp(82.26), 0.0)
    }

    @Test
    fun `three quarters of a centimetre rounds up to the next half`() {
        assertEquals(82.5, MeasurementRules.clamp(82.74), 0.0)
        assertEquals(83.0, MeasurementRules.clamp(82.75), 0.0)
    }

    @Test
    fun `a value below the smallest allowed is raised to the minimum`() {
        assertEquals(10.0, MeasurementRules.clamp(3.0), 0.0)
        assertEquals(10.0, MeasurementRules.clamp(0.0), 0.0)
        assertEquals(10.0, MeasurementRules.clamp(-20.0), 0.0)
        assertEquals(10.0, MeasurementRules.clamp(Double.NEGATIVE_INFINITY), 0.0)
    }

    @Test
    fun `a value above the largest allowed is lowered to the maximum`() {
        assertEquals(300.0, MeasurementRules.clamp(999.0), 0.0)
        assertEquals(300.0, MeasurementRules.clamp(300.2), 0.0)
        assertEquals(300.0, MeasurementRules.clamp(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun `the limits themselves are allowed`() {
        assertEquals(10.0, MeasurementRules.clamp(10.0), 0.0)
        assertEquals(300.0, MeasurementRules.clamp(300.0), 0.0)
    }

    @Test
    fun `a value just below the minimum that rounds up is still the minimum`() {
        assertEquals(10.0, MeasurementRules.clamp(9.9), 0.0)
    }

    @Test
    fun `not a number becomes the minimum`() {
        assertEquals(10.0, MeasurementRules.clamp(Double.NaN), 0.0)
    }

    @Test
    fun `a clamped value is always a whole number of halves`() {
        val samples = listOf(11.13, 47.77, 81.01, 150.49, 299.6)

        for (value in samples) {
            val halves = MeasurementRules.clamp(value) / MeasurementRules.STEP_CM
            assertEquals("clamp($value) is not a half step", halves, Math.rint(halves), 0.0)
        }
    }

    // series

    @Test
    fun `the series of a type is oldest first and holds only that type`() {
        val all = listOf(
            row(1, nov10, MeasurementType.WAIST, 82.0),
            row(2, oct1, MeasurementType.WAIST, 84.0),
            row(3, oct15, MeasurementType.ARM, 33.0),
            row(4, oct20, MeasurementType.WAIST, 83.0),
        )

        assertEquals(
            listOf(
                MeasurementPoint(oct1, 84.0),
                MeasurementPoint(oct20, 83.0),
                MeasurementPoint(nov10, 82.0),
            ),
            MeasurementRules.series(all, MeasurementType.WAIST),
        )
    }

    @Test
    fun `one point per date and the highest id of that date wins`() {
        val all = listOf(
            row(5, nov1, MeasurementType.WAIST, 81.5),
            row(2, nov1, MeasurementType.WAIST, 80.0),
            row(7, oct1, MeasurementType.WAIST, 79.0),
        )

        assertEquals(
            listOf(MeasurementPoint(oct1, 79.0), MeasurementPoint(nov1, 81.5)),
            MeasurementRules.series(all, MeasurementType.WAIST),
        )
    }

    @Test
    fun `the from date is included and earlier dates are left out`() {
        val all = listOf(
            row(1, oct1, MeasurementType.WAIST, 84.0),
            row(2, oct15, MeasurementType.WAIST, 83.5),
            row(3, oct20, MeasurementType.WAIST, 83.0),
        )

        assertEquals(
            listOf(MeasurementPoint(oct15, 83.5), MeasurementPoint(oct20, 83.0)),
            MeasurementRules.series(all, MeasurementType.WAIST, from = oct15),
        )
    }

    @Test
    fun `a from date after every measurement gives an empty series`() {
        val all = listOf(row(1, oct1, MeasurementType.WAIST, 84.0))

        assertTrue(MeasurementRules.series(all, MeasurementType.WAIST, from = nov1).isEmpty())
    }

    @Test
    fun `without a from date every measurement of the type is kept`() {
        val all = listOf(row(1, oct1, MeasurementType.WAIST, 84.0), row(2, nov1, MeasurementType.WAIST, 82.0))

        assertEquals(MeasurementRules.series(all, MeasurementType.WAIST), MeasurementRules.series(all, MeasurementType.WAIST, null))
        assertEquals(2, MeasurementRules.series(all, MeasurementType.WAIST, null).size)
    }

    @Test
    fun `a type with no measurements has an empty series`() {
        val all = listOf(row(1, oct1, MeasurementType.ARM, 33.0))

        assertTrue(MeasurementRules.series(all, MeasurementType.WAIST).isEmpty())
        assertTrue(MeasurementRules.series(emptyList(), MeasurementType.WAIST).isEmpty())
    }

    // latest

    @Test
    fun `the latest is the newest date, even when another row has a higher id`() {
        val all = listOf(
            row(1, oct1, MeasurementType.WAIST, 84.0),
            row(2, nov1, MeasurementType.WAIST, 82.5),
            row(3, oct20, MeasurementType.WAIST, 83.0),
        )

        assertEquals(2L, MeasurementRules.latest(all, MeasurementType.WAIST)?.id)
    }

    @Test
    fun `on the same date the latest is the highest id`() {
        val all = listOf(
            row(4, nov1, MeasurementType.WAIST, 80.0),
            row(9, nov1, MeasurementType.WAIST, 81.0),
            row(6, nov1, MeasurementType.WAIST, 79.0),
        )

        val latest = MeasurementRules.latest(all, MeasurementType.WAIST)

        assertEquals(9L, latest?.id)
        assertEquals(81.0, latest?.valueCm ?: Double.NaN, 0.0)
    }

    @Test
    fun `the latest looks at one type only`() {
        val all = listOf(
            row(1, oct1, MeasurementType.WAIST, 84.0),
            row(2, nov1, MeasurementType.ARM, 33.0),
        )

        assertEquals(1L, MeasurementRules.latest(all, MeasurementType.WAIST)?.id)
        assertEquals(2L, MeasurementRules.latest(all, MeasurementType.ARM)?.id)
    }

    @Test
    fun `the latest is null when the type has no measurements`() {
        val all = listOf(row(1, oct1, MeasurementType.ARM, 33.0))

        assertNull(MeasurementRules.latest(all, MeasurementType.WAIST))
        assertNull(MeasurementRules.latest(emptyList(), MeasurementType.ARM))
    }

    // change

    @Test
    fun `the change is null for an empty series`() {
        assertNull(MeasurementRules.change(emptyList()))
    }

    @Test
    fun `the change is null for a single point`() {
        assertNull(MeasurementRules.change(listOf(MeasurementPoint(oct1, 80.0))))
    }

    @Test
    fun `the change is positive when the value went up`() {
        val series = listOf(MeasurementPoint(oct1, 80.0), MeasurementPoint(nov1, 82.5))

        assertEquals(2.5, MeasurementRules.change(series) ?: Double.NaN, 0.0)
    }

    @Test
    fun `the change is negative when the value went down`() {
        val series = listOf(MeasurementPoint(oct1, 82.5), MeasurementPoint(nov1, 80.0))

        assertEquals(-2.5, MeasurementRules.change(series) ?: Double.NaN, 0.0)
    }

    @Test
    fun `the change is last minus first, not the highest or lowest value`() {
        val series = listOf(
            MeasurementPoint(oct1, 80.0),
            MeasurementPoint(oct15, 95.0),
            MeasurementPoint(nov1, 85.0),
        )

        assertEquals(5.0, MeasurementRules.change(series) ?: Double.NaN, 0.0)
    }

    // typesWithData

    @Test
    fun `the types with data come out once each, in enum order`() {
        val all = listOf(
            row(1, oct1, MeasurementType.NECK, 38.0),
            row(2, oct1, MeasurementType.WAIST, 84.0),
            row(3, nov1, MeasurementType.WAIST, 82.5),
            row(4, nov1, MeasurementType.ARM, 33.0),
        )

        assertEquals(
            listOf(MeasurementType.WAIST, MeasurementType.ARM, MeasurementType.NECK),
            MeasurementRules.typesWithData(all),
        )
    }

    @Test
    fun `no measurements means no types with data`() {
        assertEquals(emptyList<MeasurementType>(), MeasurementRules.typesWithData(emptyList()))
    }
}

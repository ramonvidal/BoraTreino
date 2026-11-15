package com.boratreino.feature.progress

import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The measurement card's pure helpers: which type is shown, where the log sheet starts, the history rows. */
class MeasurementHelpersTest {
    private val day1 = LocalDate.of(2026, 10, 1)
    private val day2 = LocalDate.of(2026, 10, 2)

    private fun entry(id: Long, date: LocalDate, type: MeasurementType, cm: Double) =
        BodyMeasurement(id = id, date = date, type = type, valueCm = cm)

    // The default type

    @Test
    fun `without any measurement the waist is the default type`() {
        assertEquals(MeasurementType.WAIST, defaultMeasurementType(emptyList()))
    }

    @Test
    fun `the default type is the first type in display order that has data`() {
        val all = listOf(
            entry(1, day1, MeasurementType.NECK, 37.0),
            entry(2, day1, MeasurementType.HIPS, 95.5),
            entry(3, day1, MeasurementType.CHEST, 100.0),
        )
        assertEquals(MeasurementType.HIPS, defaultMeasurementType(all))
    }

    @Test
    fun `a type with data is the default when the waist has none`() {
        val all = listOf(entry(1, day1, MeasurementType.CHEST, 100.0))
        assertEquals(MeasurementType.CHEST, defaultMeasurementType(all))
    }

    // The log sheet's start value

    @Test
    fun `without a measurement of the type the log sheet starts at 80 cm`() {
        val all = listOf(entry(1, day1, MeasurementType.HIPS, 95.5))
        assertEquals(80.0, measurementStart(all, MeasurementType.WAIST), 1e-9)
        assertEquals(80.0, measurementStart(emptyList(), MeasurementType.WAIST), 1e-9)
    }

    @Test
    fun `the log sheet starts at the newest value of the type`() {
        val all = listOf(
            entry(1, day2, MeasurementType.WAIST, 85.0),
            entry(2, day1, MeasurementType.WAIST, 90.0),
        )
        assertEquals(85.0, measurementStart(all, MeasurementType.WAIST), 1e-9)
    }

    @Test
    fun `on the same date the higher id gives the start value`() {
        val all = listOf(
            entry(1, day1, MeasurementType.WAIST, 80.5),
            entry(2, day1, MeasurementType.WAIST, 82.0),
        )
        assertEquals(82.0, measurementStart(all, MeasurementType.WAIST), 1e-9)
    }

    // The history rows

    @Test
    fun `history is the last five entries of the type, newest first`() {
        val waist = (1..7).map { entry(it.toLong(), day1.plusDays(it.toLong()), MeasurementType.WAIST, 70.0 + it) }
        val all = waist + entry(100, day1, MeasurementType.HIPS, 99.0)

        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), measurementHistory(all, MeasurementType.WAIST).map { it.id })
    }

    @Test
    fun `history on the same date lists the higher id first`() {
        val all = listOf(
            entry(1, day1, MeasurementType.ARM, 30.0),
            entry(2, day1, MeasurementType.ARM, 31.0),
        )
        assertEquals(listOf(2L, 1L), measurementHistory(all, MeasurementType.ARM).map { it.id })
    }

    @Test
    fun `history keeps fewer than five entries as they are`() {
        val all = listOf(
            entry(1, day1, MeasurementType.THIGH, 55.0),
            entry(2, day2, MeasurementType.THIGH, 55.5),
        )
        assertEquals(listOf(2L, 1L), measurementHistory(all, MeasurementType.THIGH).map { it.id })
    }

    @Test
    fun `history of a type without entries is empty`() {
        val all = listOf(entry(1, day1, MeasurementType.NECK, 37.0))
        assertEquals(emptyList<BodyMeasurement>(), measurementHistory(all, MeasurementType.WAIST))
    }
}

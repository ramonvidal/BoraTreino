package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupMeasurementsCodecTest {
    private val exportedAt: Instant = Instant.parse("2026-11-02T20:15:30.250Z")
    private val nov2 = LocalDate.of(2026, 11, 2)

    private val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    private fun withMeasurements(measurements: List<BodyMeasurement>) = empty.copy(measurements = measurements)

    private fun decoded(json: String): BackupDecodeResult = BackupCodec.decode(json)

    private fun assertInvalid(json: String) {
        assertTrue("expected Invalid for: ${json.take(160)}", decoded(json) is BackupDecodeResult.Invalid)
    }

    private fun assertOk(json: String): BackupDecodeResult.Ok {
        val result = decoded(json)
        assertTrue("expected Ok, got $result", result is BackupDecodeResult.Ok)
        return result as BackupDecodeResult.Ok
    }

    /** A file holding one waist measurement, to edit in the tests below. */
    private val oneRow: String = BackupCodec.encode(
        withMeasurements(listOf(BodyMeasurement(5, nov2, MeasurementType.WAIST, 82.5))),
        exportedAt,
    )

    // Round trip

    @Test
    fun `every measurement type with decimal values comes back unchanged`() {
        val all = listOf(
            BodyMeasurement(1, nov2, MeasurementType.WAIST, 82.5),
            BodyMeasurement(2, nov2, MeasurementType.HIPS, 95.25),
            BodyMeasurement(3, nov2, MeasurementType.CHEST, 100.0),
            BodyMeasurement(4, nov2, MeasurementType.ARM, 33.75),
            BodyMeasurement(5, nov2, MeasurementType.THIGH, 58.5),
            BodyMeasurement(6, nov2, MeasurementType.NECK, 38.0),
        )
        val data = withMeasurements(all)

        assertEquals(BackupDecodeResult.Ok(data, exportedAt), decoded(BackupCodec.encode(data, exportedAt)))
    }

    @Test
    fun `decimal values are written with their decimals`() {
        val json = BackupCodec.encode(withMeasurements(listOf(BodyMeasurement(1, nov2, MeasurementType.HIPS, 95.25))), exportedAt)

        assertTrue(json.contains("\"valueCm\":95.25"))
    }

    @Test
    fun `measurements are written with their id, date, type and value`() {
        assertTrue(oneRow.contains("\"measurements\":[{\"id\":5,\"date\":\"2026-11-02\",\"type\":\"WAIST\",\"valueCm\":82.5}]"))
    }

    @Test
    fun `measurements are written sorted by date, then type, then id`() {
        val unordered = listOf(
            BodyMeasurement(8, nov2, MeasurementType.NECK, 38.0),
            BodyMeasurement(3, LocalDate.of(2026, 10, 1), MeasurementType.ARM, 33.5),
            BodyMeasurement(2, nov2, MeasurementType.WAIST, 82.5),
            BodyMeasurement(7, LocalDate.of(2026, 10, 1), MeasurementType.WAIST, 84.0),
        )
        val sorted = listOf(
            BodyMeasurement(7, LocalDate.of(2026, 10, 1), MeasurementType.WAIST, 84.0),
            BodyMeasurement(3, LocalDate.of(2026, 10, 1), MeasurementType.ARM, 33.5),
            BodyMeasurement(2, nov2, MeasurementType.WAIST, 82.5),
            BodyMeasurement(8, nov2, MeasurementType.NECK, 38.0),
        )
        val json = BackupCodec.encode(withMeasurements(unordered), exportedAt)

        assertTrue(
            json.contains(
                "\"measurements\":[" +
                    "{\"id\":7,\"date\":\"2026-10-01\",\"type\":\"WAIST\",\"valueCm\":84.0}," +
                    "{\"id\":3,\"date\":\"2026-10-01\",\"type\":\"ARM\",\"valueCm\":33.5}," +
                    "{\"id\":2,\"date\":\"2026-11-02\",\"type\":\"WAIST\",\"valueCm\":82.5}," +
                    "{\"id\":8,\"date\":\"2026-11-02\",\"type\":\"NECK\",\"valueCm\":38.0}]",
            ),
        )
        assertEquals(BackupDecodeResult.Ok(withMeasurements(sorted), exportedAt), decoded(json))
    }

    // Optional list

    @Test
    fun `a file without the measurements list reads as no measurements`() {
        val withoutKey = BackupCodec.encode(empty, exportedAt).replace(",\"measurements\":[]", "")
        assertTrue(!withoutKey.contains("measurements"))

        assertEquals(emptyList<BodyMeasurement>(), assertOk(withoutKey).data.measurements)
    }

    @Test
    fun `an explicit null measurements list reads as no measurements`() {
        val withNull = BackupCodec.encode(empty, exportedAt).replace("\"measurements\":[]", "\"measurements\":null")

        assertEquals(emptyList<BodyMeasurement>(), assertOk(withNull).data.measurements)
    }

    @Test
    fun `an empty measurements list reads as no measurements`() {
        assertEquals(emptyList<BodyMeasurement>(), assertOk(BackupCodec.encode(empty, exportedAt)).data.measurements)
    }

    // Bad entries

    @Test
    fun `a measurement of an unknown type is invalid`() {
        assertInvalid(oneRow.replace("\"type\":\"WAIST\"", "\"type\":\"CALF\""))
    }

    @Test
    fun `a measurement type must be the exact enum name`() {
        assertInvalid(oneRow.replace("\"type\":\"WAIST\"", "\"type\":\"waist\""))
        assertInvalid(oneRow.replace("\"type\":\"WAIST\"", "\"type\":null"))
        assertInvalid(oneRow.replace("\"type\":\"WAIST\"", "\"type\":3"))
    }

    @Test
    fun `a measurement list that is not a list is invalid`() {
        val emptyText = BackupCodec.encode(empty, exportedAt)
        assertTrue(emptyText.contains("\"measurements\":[]"))

        assertInvalid(emptyText.replace("\"measurements\":[]", "\"measurements\":\"none\""))
        assertInvalid(emptyText.replace("\"measurements\":[]", "\"measurements\":{}"))
        assertInvalid(emptyText.replace("\"measurements\":[]", "\"measurements\":42"))
        assertInvalid(emptyText.replace("\"measurements\":[]", "\"measurements\":true"))
    }

    @Test
    fun `a measurement entry that is not an object is invalid`() {
        val emptyText = BackupCodec.encode(empty, exportedAt)

        assertInvalid(emptyText.replace("\"measurements\":[]", "\"measurements\":[42]"))
        assertInvalid(emptyText.replace("\"measurements\":[]", "\"measurements\":[\"WAIST\"]"))
    }

    @Test
    fun `a measurement value that is text is invalid`() {
        assertTrue(oneRow.contains("\"valueCm\":82.5"))
        assertInvalid(oneRow.replace("\"valueCm\":82.5", "\"valueCm\":\"82.5\""))
    }

    @Test
    fun `a measurement without a value is invalid`() {
        assertInvalid(oneRow.replace("\"valueCm\":82.5", "\"x\":1"))
    }

    @Test
    fun `a measurement without an id is invalid`() {
        assertTrue(oneRow.contains("{\"id\":5,"))
        assertInvalid(oneRow.replace("{\"id\":5,", "{"))
    }

    @Test
    fun `a measurement with a date that is not an ISO date is invalid`() {
        assertTrue(oneRow.contains("\"date\":\"2026-11-02\""))

        assertInvalid(oneRow.replace("\"date\":\"2026-11-02\"", "\"date\":\"02/11/2026\""))
        assertInvalid(oneRow.replace("\"date\":\"2026-11-02\"", "\"date\":\"2026-02-30\""))
        assertInvalid(oneRow.replace("\"date\":\"2026-11-02\"", "\"date\":20261102"))
    }

    @Test
    fun `unknown fields inside a measurement are ignored`() {
        val withExtra = oneRow.replace("\"valueCm\":82.5", "\"valueCm\":82.5,\"colour\":\"red\"")

        assertEquals(
            BackupDecodeResult.Ok(withMeasurements(listOf(BodyMeasurement(5, nov2, MeasurementType.WAIST, 82.5))), exportedAt),
            decoded(withExtra),
        )
    }
}

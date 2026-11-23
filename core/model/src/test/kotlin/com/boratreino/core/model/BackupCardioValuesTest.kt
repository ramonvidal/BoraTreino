package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Backup format 10 writes the bike console values of each set; older files still import (phase 42). */
class BackupCardioValuesTest {
    private val exportedAt: Instant = Instant.parse("2026-11-23T20:15:30.250Z")
    private val at = Instant.parse("2026-11-23T10:02:00Z")

    private val data = BackupData(
        machines = listOf(Machine(1L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)),
        plans = emptyList(),
        routines = emptyList(),
        items = emptyList(),
        sessions = listOf(Session(1L, Instant.parse("2026-11-23T10:00:00Z"), null, null, null)),
        sets = listOf(
            WorkoutSet(
                1L, 1L, 1L, 0, null, null, 1200, 180, 5000, at,
                intensity = 12, watts = 160, heartRate = 142, wattsPerKg = 2.4, tss = 48, intensityFactor = 0.79,
            ),
            WorkoutSet(
                2L, 1L, 1L, 1, null, null, 600, null, null, at,
                watts = 0, heartRate = 30, wattsPerKg = 0.0, tss = 0, intensityFactor = 0.0,
            ),
            WorkoutSet(
                3L, 1L, 1L, 2, null, null, 3600, null, null, at,
                watts = 2000, heartRate = 250, wattsPerKg = 10.0, tss = 999, intensityFactor = 2.0,
            ),
            WorkoutSet(4L, 1L, 1L, 3, null, null, 60, null, null, at),
        ),
    )

    private val text: String get() = BackupCodec.encode(data, exportedAt)

    /** Drops the five console keys, as a file from before phase 42 has them. */
    private fun withoutConsoleValues(json: String): String =
        json.replace(Regex(",\"(watts|heartRate|wattsPerKg|tss|intensityFactor)\":(null|-?[0-9.]+)"), "")

    private fun assertInvalid(json: String) {
        assertTrue("expected Invalid", BackupCodec.decode(json) is BackupDecodeResult.Invalid)
    }

    // Format

    @Test
    fun `the format is version 10`() {
        assertEquals(10, BackupCodec.FORMAT_VERSION)
        assertTrue(text.startsWith("{\"app\":\"boratreino\",\"version\":10,"))
    }

    @Test
    fun `each set writes its five console keys`() {
        assertTrue(text.contains("\"watts\":160,"))
        assertTrue(text.contains("\"heartRate\":142,"))
        assertTrue(text.contains("\"wattsPerKg\":2.4,"))
        assertTrue(text.contains("\"tss\":48,"))
        assertTrue(text.contains("\"intensityFactor\":0.79}"))
    }

    @Test
    fun `a set with no console values writes them as null`() {
        assertTrue(text.contains("\"watts\":null,\"heartRate\":null,\"wattsPerKg\":null,\"tss\":null,\"intensityFactor\":null}"))
    }

    // Round trip

    @Test
    fun `the values come back unchanged, at their limits and at zero`() {
        assertEquals(BackupDecodeResult.Ok(data, exportedAt), BackupCodec.decode(text))
    }

    @Test
    fun `zero values stay zero and are not read as not logged`() {
        val ok = BackupCodec.decode(text) as BackupDecodeResult.Ok
        val zeros = ok.data.sets[1]

        assertEquals(0, zeros.watts)
        assertEquals(0.0, zeros.wattsPerKg!!, 0.0)
        assertEquals(0, zeros.tss)
        assertEquals(0.0, zeros.intensityFactor!!, 0.0)
    }

    // Missing and null

    @Test
    fun `a set without the console keys reads them as not logged`() {
        val ok = BackupCodec.decode(withoutConsoleValues(text)) as BackupDecodeResult.Ok

        assertEquals(listOf(null, null, null, null), ok.data.sets.map { it.watts })
        assertEquals(listOf(null, null, null, null), ok.data.sets.map { it.heartRate })
        assertEquals(listOf(null, null, null, null), ok.data.sets.map { it.wattsPerKg })
        assertEquals(listOf(null, null, null, null), ok.data.sets.map { it.tss })
        assertEquals(listOf(null, null, null, null), ok.data.sets.map { it.intensityFactor })
    }

    @Test
    fun `explicit nulls read as not logged`() {
        val json = text.replace("\"watts\":160,", "\"watts\":null,").replace("\"tss\":48,", "\"tss\":null,")

        val ok = BackupCodec.decode(json) as BackupDecodeResult.Ok

        assertEquals(null, ok.data.sets[0].watts)
        assertEquals(null, ok.data.sets[0].tss)
        assertEquals(142, ok.data.sets[0].heartRate)
    }

    @Test
    fun `a whole number is read for watts per kg`() {
        val json = text.replace("\"wattsPerKg\":2.4,", "\"wattsPerKg\":5,")

        val ok = BackupCodec.decode(json) as BackupDecodeResult.Ok

        assertEquals(5.0, ok.data.sets[0].wattsPerKg!!, 0.0)
    }

    // Wrong types

    @Test
    fun `a console value that is text is invalid`() {
        assertInvalid(text.replace("\"watts\":160,", "\"watts\":\"160\","))
        assertInvalid(text.replace("\"heartRate\":142,", "\"heartRate\":\"142\","))
        assertInvalid(text.replace("\"wattsPerKg\":2.4,", "\"wattsPerKg\":\"2.4\","))
        assertInvalid(text.replace("\"tss\":48,", "\"tss\":\"48\","))
        assertInvalid(text.replace("\"intensityFactor\":0.79}", "\"intensityFactor\":\"0.79\"}"))
    }

    @Test
    fun `a console value that is not a number is invalid`() {
        assertInvalid(text.replace("\"watts\":160,", "\"watts\":true,"))
        assertInvalid(text.replace("\"tss\":48,", "\"tss\":[48],"))
        assertInvalid(text.replace("\"intensityFactor\":0.79}", "\"intensityFactor\":{}}"))
    }

    @Test
    fun `a whole number field with a decimal is invalid`() {
        assertInvalid(text.replace("\"watts\":160,", "\"watts\":160.5,"))
        assertInvalid(text.replace("\"heartRate\":142,", "\"heartRate\":142.0,"))
        assertInvalid(text.replace("\"tss\":48,", "\"tss\":48.5,"))
    }

    // Out of range

    @Test
    fun `watts outside 0 to 2000 is invalid`() {
        assertInvalid(text.replace("\"watts\":160,", "\"watts\":2001,"))
        assertInvalid(text.replace("\"watts\":160,", "\"watts\":-1,"))
    }

    @Test
    fun `heart rate outside 30 to 250 is invalid`() {
        assertInvalid(text.replace("\"heartRate\":142,", "\"heartRate\":29,"))
        assertInvalid(text.replace("\"heartRate\":142,", "\"heartRate\":251,"))
    }

    @Test
    fun `watts per kg outside 0 to 10 is invalid`() {
        assertInvalid(text.replace("\"wattsPerKg\":2.4,", "\"wattsPerKg\":10.1,"))
        assertInvalid(text.replace("\"wattsPerKg\":2.4,", "\"wattsPerKg\":-0.1,"))
    }

    @Test
    fun `tss outside 0 to 999 is invalid`() {
        assertInvalid(text.replace("\"tss\":48,", "\"tss\":1000,"))
        assertInvalid(text.replace("\"tss\":48,", "\"tss\":-1,"))
    }

    @Test
    fun `intensity factor outside 0 to 2 is invalid`() {
        assertInvalid(text.replace("\"intensityFactor\":0.79}", "\"intensityFactor\":2.01}"))
        assertInvalid(text.replace("\"intensityFactor\":0.79}", "\"intensityFactor\":-0.01}"))
    }

    // Older files

    @Test
    fun `a version 9 file without the console keys imports with none`() {
        val v9 = withoutConsoleValues(text).replace("\"version\":10", "\"version\":9")

        val ok = BackupCodec.decode(v9) as BackupDecodeResult.Ok

        assertEquals(4, ok.data.sets.size)
        assertEquals(data.sets.map { it.seconds }, ok.data.sets.map { it.seconds })
        assertTrue(ok.data.sets.all { it.watts == null && it.intensityFactor == null })
    }

    @Test
    fun `a version 8 file without the console keys imports with none`() {
        val v8 = withoutConsoleValues(text).replace("\"version\":10", "\"version\":8")

        val ok = BackupCodec.decode(v8) as BackupDecodeResult.Ok

        assertEquals(4, ok.data.sets.size)
        assertTrue(ok.data.sets.all { it.tss == null && it.heartRate == null })
    }

    @Test
    fun `a version 9 file that has the intensity but not the console keys keeps the intensity`() {
        val v9 = withoutConsoleValues(text).replace("\"version\":10", "\"version\":9")

        val ok = BackupCodec.decode(v9) as BackupDecodeResult.Ok

        assertEquals(12, ok.data.sets[0].intensity)
    }

    // Too new

    @Test
    fun `a version 11 file is too new`() {
        assertEquals(BackupDecodeResult.TooNew(11), BackupCodec.decode(text.replace("\"version\":10", "\"version\":11")))
    }
}

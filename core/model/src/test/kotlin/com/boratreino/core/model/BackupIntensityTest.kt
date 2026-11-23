package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Backup format 9 writes each set's optional intensity; older files still import. */
class BackupIntensityTest {
    private val exportedAt: Instant = Instant.parse("2026-11-02T20:15:30.250Z")
    private val at = Instant.parse("2026-11-02T10:02:00Z")

    private val data = BackupData(
        machines = listOf(Machine(1L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)),
        plans = emptyList(),
        routines = emptyList(),
        items = emptyList(),
        sessions = listOf(Session(1L, Instant.parse("2026-11-02T10:00:00Z"), null, null, null)),
        sets = listOf(
            WorkoutSet(1L, 1L, 1L, 0, null, null, 1200, 250, 5000, at, intensity = 12),
            WorkoutSet(2L, 1L, 1L, 1, null, null, 600, null, null, at),
            WorkoutSet(3L, 1L, 1L, 2, null, null, 60, null, null, at, intensity = 0),
            WorkoutSet(4L, 1L, 1L, 3, null, null, 60, null, null, at, intensity = 999),
        ),
    )

    private val text: String get() = BackupCodec.encode(data, exportedAt)

    private fun withoutIntensity(json: String): String =
        json.replace(Regex(",\"intensity\":(null|-?\\d+)"), "")

    @Test
    fun `the format is version 10`() {
        assertEquals(10, BackupCodec.FORMAT_VERSION)
        assertTrue(text.startsWith("{\"app\":\"boratreino\",\"version\":10,"))
    }

    @Test
    fun `sets with an intensity come back unchanged, including 0 and 999`() {
        assertEquals(BackupDecodeResult.Ok(data, exportedAt), BackupCodec.decode(text))
    }

    @Test
    fun `each set writes its intensity, or null when none is logged`() {
        assertTrue(text.contains("\"intensity\":12,"))
        assertTrue(text.contains("\"intensity\":null,"))
        assertTrue(text.contains("\"intensity\":999,"))
    }

    @Test
    fun `a set without the intensity field reads as not logged`() {
        val older = withoutIntensity(text)

        val ok = BackupCodec.decode(older) as BackupDecodeResult.Ok

        assertEquals(listOf(null, null, null, null), ok.data.sets.map { it.intensity })
        assertEquals(data.sets.map { it.seconds }, ok.data.sets.map { it.seconds })
    }

    @Test
    fun `a version 8 file without intensities imports`() {
        val v8 = withoutIntensity(text).replace("\"version\":10", "\"version\":8")

        val ok = BackupCodec.decode(v8) as BackupDecodeResult.Ok

        assertEquals(4, ok.data.sets.size)
        assertTrue(ok.data.sets.all { it.intensity == null })
    }

    @Test
    fun `an intensity that is not a number is invalid`() {
        for (value in listOf("\"lots\"", "true", "[1]", "{}", "12.5")) {
            val json = text.replace("\"intensity\":12,", "\"intensity\":$value,")

            assertTrue("intensity $value", BackupCodec.decode(json) is BackupDecodeResult.Invalid)
        }
    }

    @Test
    fun `an intensity outside 0 to 999 is invalid`() {
        for (value in listOf("1000", "-1")) {
            val json = text.replace("\"intensity\":12,", "\"intensity\":$value,")

            assertTrue("intensity $value", BackupCodec.decode(json) is BackupDecodeResult.Invalid)
        }
    }

    @Test
    fun `a newer file is too new`() {
        assertEquals(BackupDecodeResult.TooNew(11), BackupCodec.decode(text.replace("\"version\":10", "\"version\":11")))
    }
}

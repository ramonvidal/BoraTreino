package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Backup format 11 writes each set's intensity as a decimal; older files still import. */
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
            WorkoutSet(1L, 1L, 1L, 0, null, null, 1200, 250, 5000, at, intensity = 0.79),
            WorkoutSet(2L, 1L, 1L, 1, null, null, 600, null, null, at),
            WorkoutSet(3L, 1L, 1L, 2, null, null, 60, null, null, at, intensity = 0.0),
            WorkoutSet(4L, 1L, 1L, 3, null, null, 60, null, null, at, intensity = 100.0),
            WorkoutSet(5L, 1L, 1L, 4, null, null, 60, null, null, at, intensity = 12.0),
        ),
    )

    private val text: String get() = BackupCodec.encode(data, exportedAt)

    private fun withoutIntensity(json: String): String =
        json.replace(Regex(",\"intensity\":(null|-?[0-9.]+)"), "")

    /** Replaces the first set's intensity of 12 (written as 12 or 12.0) with [value]. */
    private fun withFirstIntensity(json: String, value: String): String {
        val replaced = json.replace(Regex("\"intensity\":12(?:\\.0)?([,}])")) { m -> "\"intensity\":$value" + m.groupValues[1] }
        assertTrue("the test file has an intensity of 12", replaced != json)
        return replaced
    }

    @Test
    fun `the format is version 11`() {
        assertEquals(11, BackupCodec.FORMAT_VERSION)
        assertTrue(text.startsWith("{\"app\":\"boratreino\",\"version\":11,"))
    }

    @Test
    fun `decimal intensities come back unchanged, including 0 and 100`() {
        assertEquals(BackupDecodeResult.Ok(data, exportedAt), BackupCodec.decode(text))
    }

    @Test
    fun `each set writes its intensity as a decimal, or null when none is logged`() {
        assertTrue(text.contains(Regex("\"intensity\":0\\.79[,}]")))
        assertTrue(text.contains(Regex("\"intensity\":null[,}]")))
        assertTrue(text.contains(Regex("\"intensity\":100(?:\\.0)?[,}]")))
    }

    @Test
    fun `a set without the intensity field reads as not logged`() {
        val older = withoutIntensity(text)

        val ok = BackupCodec.decode(older) as BackupDecodeResult.Ok

        assertTrue(ok.data.sets.all { it.intensity == null })
        assertEquals(data.sets.map { it.seconds }, ok.data.sets.map { it.seconds })
    }

    @Test
    fun `a whole-number intensity in a version 11 file reads as a decimal`() {
        val ok = BackupCodec.decode(withFirstIntensity(text, "12")) as BackupDecodeResult.Ok

        assertEquals(12.0, ok.data.sets[4].intensity!!, 0.0)
    }

    @Test
    fun `an intensity with more than 2 decimals is rounded half up when read`() {
        val ok = BackupCodec.decode(withFirstIntensity(text, "0.785")) as BackupDecodeResult.Ok

        assertEquals(0.79, ok.data.sets[4].intensity!!, 1e-9)
    }

    @Test
    fun `a version 10 file with the old console keys imports its intensity as a decimal and ignores the old keys`() {
        val v10 = text
            .replace("\"version\":11", "\"version\":10")
            .replace(Regex("\"intensity\":12(?:\\.0)?([,}])")) { m ->
                "\"intensity\":12,\"watts\":160,\"heartRate\":142,\"wattsPerKg\":2.4,\"tss\":48,\"intensityFactor\":0.79" +
                    m.groupValues[1]
            }
        assertTrue("the old keys are in the file", v10.contains("\"watts\":160"))

        val ok = BackupCodec.decode(v10) as BackupDecodeResult.Ok

        assertEquals(12.0, ok.data.sets[4].intensity!!, 0.0)
        assertEquals(data.sets.map { it.seconds }, ok.data.sets.map { it.seconds })
        assertEquals(data.sets.map { it.calories }, ok.data.sets.map { it.calories })
    }

    @Test
    fun `a version 8 file without intensities imports`() {
        val v8 = withoutIntensity(text).replace("\"version\":11", "\"version\":8")

        val ok = BackupCodec.decode(v8) as BackupDecodeResult.Ok

        assertEquals(5, ok.data.sets.size)
        assertTrue(ok.data.sets.all { it.intensity == null })
    }

    @Test
    fun `an intensity that is not a number is invalid`() {
        for (value in listOf("\"lots\"", "true", "[1]", "{}")) {
            val json = withFirstIntensity(text, value)

            assertTrue("intensity $value", BackupCodec.decode(json) is BackupDecodeResult.Invalid)
        }
    }

    @Test
    fun `an intensity outside 0 to 100 is invalid`() {
        for (value in listOf("150", "100.5", "-1")) {
            val json = withFirstIntensity(text, value)

            assertTrue("intensity $value", BackupCodec.decode(json) is BackupDecodeResult.Invalid)
        }
    }

    @Test
    fun `a null intensity reads as not logged`() {
        val ok = BackupCodec.decode(withFirstIntensity(text, "null")) as BackupDecodeResult.Ok

        assertNull(ok.data.sets[4].intensity)
    }

    @Test
    fun `a version 12 file is too new`() {
        assertEquals(BackupDecodeResult.TooNew(12), BackupCodec.decode(text.replace("\"version\":11", "\"version\":12")))
    }
}

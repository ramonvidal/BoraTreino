package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupKindsTest {
    private val exportedAt: Instant = Instant.parse("2026-11-20T20:15:30.250Z")
    private val at = Instant.parse("2026-11-20T09:02:00Z")

    private val timedAndDistance = BackupData(
        machines = listOf(
            Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, 120, false),
            Machine(2L, "Farmer's walk", MuscleGroup.ARMS, MachineKind.WEIGHT_TIME, 2.0, null, null, false),
            Machine(3L, "Sled push", MuscleGroup.LEGS, MachineKind.WEIGHT_DISTANCE, 5.0, null, null, false),
            Machine(4L, "Plank", MuscleGroup.CORE, MachineKind.TIME, 2.5, null, null, false),
        ),
        plans = emptyList(),
        routines = emptyList(),
        items = emptyList(),
        sessions = listOf(Session(1L, Instant.parse("2026-11-20T09:00:00Z"), null, null, null)),
        sets = listOf(
            WorkoutSet(1L, 1L, 1L, 0, 100.0, 5, null, null, null, at),
            WorkoutSet(2L, 1L, 2L, 0, 47.5, null, 30, null, null, at),
            WorkoutSet(3L, 1L, 3L, 0, 40.0, null, null, null, 25, at),
            WorkoutSet(4L, 1L, 4L, 0, 0.0, null, 45, null, null, at),
        ),
    )

    private val weightAndReps = BackupData(
        machines = listOf(Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, 120, false)),
        plans = emptyList(),
        routines = emptyList(),
        items = emptyList(),
        sessions = listOf(Session(1L, Instant.parse("2026-11-02T10:00:00Z"), null, null, null)),
        sets = listOf(WorkoutSet(1L, 1L, 1L, 0, 100.0, 5, null, null, null, at)),
    )

    @Test
    fun `the new kinds and their sets come back unchanged from a version 12 file`() {
        val text = BackupCodec.encode(timedAndDistance, exportedAt)

        assertTrue(text.startsWith("{\"app\":\"boratreino\",\"version\":12,"))
        assertTrue(text.contains("\"kind\":\"WEIGHT_TIME\""))
        assertTrue(text.contains("\"kind\":\"WEIGHT_DISTANCE\""))
        assertTrue(text.contains("\"kind\":\"TIME\""))
        assertEquals(BackupDecodeResult.Ok(timedAndDistance, exportedAt), BackupCodec.decode(text))
    }

    @Test
    fun `a version 11 file with reps sets still imports unchanged`() {
        val v11 = BackupCodec.encode(weightAndReps, exportedAt).replace("\"version\":12", "\"version\":11")

        assertEquals(BackupDecodeResult.Ok(weightAndReps, exportedAt), BackupCodec.decode(v11))
    }

    @Test
    fun `a version 11 file imports its reps sets with their numbers in place`() {
        val v11 = BackupCodec.encode(timedAndDistance.copy(
            machines = listOf(Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, 120, false)),
            sets = listOf(WorkoutSet(1L, 1L, 1L, 0, 100.0, 5, null, null, null, at)),
        ), exportedAt).replace("\"version\":12", "\"version\":11")

        val ok = BackupCodec.decode(v11) as BackupDecodeResult.Ok

        assertEquals(listOf(100.0), ok.data.sets.map { it.weightKg })
        assertEquals(listOf(5), ok.data.sets.map { it.reps })
    }

    @Test
    fun `the format version is 12`() {
        assertEquals(12, BackupCodec.FORMAT_VERSION)
    }
}

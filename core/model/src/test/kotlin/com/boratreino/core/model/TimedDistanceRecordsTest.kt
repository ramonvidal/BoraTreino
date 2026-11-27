package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TimedDistanceRecordsTest {
    private val at = Instant.parse("2026-11-20T09:00:00Z")

    private fun set(
        position: Int,
        weightKg: Double?,
        reps: Int? = null,
        seconds: Int? = null,
        distanceM: Int? = null,
        type: SetType = SetType.NORMAL,
    ) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 1L, position = position,
        weightKg = weightKg, reps = reps, seconds = seconds, calories = null, distanceM = distanceM,
        loggedAt = at, type = type,
    )

    @Test
    fun `the first timed set is not a record`() {
        assertNull(RecordRules.recordFor(set(0, 40.0, seconds = 30), emptyList()))
    }

    @Test
    fun `a longer hold at the same weight is a longest time record`() {
        val earlier = listOf(set(0, 40.0, seconds = 30))

        assertEquals(
            Record.LongestTime(40.0, 45),
            RecordRules.recordFor(set(1, 40.0, seconds = 45), earlier),
        )
    }

    @Test
    fun `a hold that is not longer than an earlier one at that weight is not a record`() {
        val earlier = listOf(set(0, 40.0, seconds = 30), set(1, 40.0, seconds = 50))

        assertNull(RecordRules.recordFor(set(2, 40.0, seconds = 45), earlier))
        assertNull(RecordRules.recordFor(set(2, 40.0, seconds = 50), earlier))
    }

    @Test
    fun `a longer hold with no load is a longest time record at 0 kg, never a heaviest weight`() {
        val earlier = listOf(set(0, 0.0, seconds = 30))

        assertEquals(
            Record.LongestTime(0.0, 40),
            RecordRules.recordFor(set(1, 0.0, seconds = 40), earlier),
        )
    }

    @Test
    fun `a time only set is not a heaviest weight even when every earlier set is loaded`() {
        val earlier = listOf(set(0, 40.0, reps = 10))

        assertNull(RecordRules.recordFor(set(1, 0.0, seconds = 60), earlier))
    }

    @Test
    fun `a heavier timed set is a heaviest weight record`() {
        val earlier = listOf(set(0, 40.0, seconds = 30))

        assertEquals(
            Record.HeaviestWeight(50.0),
            RecordRules.recordFor(set(1, 50.0, seconds = 10), earlier),
        )
    }

    @Test
    fun `a longer carry at the same weight is a longest distance record`() {
        val earlier = listOf(set(0, 40.0, distanceM = 20))

        assertEquals(
            Record.LongestDistance(40.0, 25),
            RecordRules.recordFor(set(1, 40.0, distanceM = 25), earlier),
        )
    }

    @Test
    fun `a carry that is not longer at that weight is not a record`() {
        val earlier = listOf(set(0, 40.0, distanceM = 20))

        assertNull(RecordRules.recordFor(set(1, 40.0, distanceM = 20), earlier))
    }

    @Test
    fun `a longer carry with no load is a longest distance record at 0 kg`() {
        val earlier = listOf(set(0, 0.0, distanceM = 20))

        assertEquals(
            Record.LongestDistance(0.0, 30),
            RecordRules.recordFor(set(1, 0.0, distanceM = 30), earlier),
        )
    }

    @Test
    fun `a heavier distance set is a heaviest weight record`() {
        val earlier = listOf(set(0, 40.0, distanceM = 20))

        assertEquals(
            Record.HeaviestWeight(60.0),
            RecordRules.recordFor(set(1, 60.0, distanceM = 5), earlier),
        )
    }

    @Test
    fun `reps sets still make most reps records`() {
        val earlier = listOf(set(0, 40.0, reps = 8))

        assertEquals(Record.MostReps(40.0, 10), RecordRules.recordFor(set(1, 40.0, reps = 10), earlier))
    }

    @Test
    fun `a warm-up timed set is never a record`() {
        val earlier = listOf(set(0, 40.0, seconds = 30))

        assertNull(RecordRules.recordFor(set(1, 40.0, seconds = 90, type = SetType.WARMUP), earlier))
    }

    @Test
    fun `a cardio set is never a record`() {
        val earlier = listOf(set(0, null, seconds = 600))

        assertNull(RecordRules.recordFor(set(1, null, seconds = 1200), earlier))
    }
}

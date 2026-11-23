package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Cardio progress by speed, intensity, distance and time: the metrics, the default and the points. */
class ProgressCardioMetricsTest {
    private val bike = Machine(1L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)

    private val day1 = LocalDate.of(2026, 11, 1)
    private val day2 = LocalDate.of(2026, 11, 2)
    private val dates = mapOf(1L to day1, 2L to day2)

    private fun set(
        id: Long,
        sessionId: Long,
        seconds: Int? = null,
        distanceM: Int? = null,
        intensity: Double? = null,
        position: Int = 0,
        type: SetType = SetType.NORMAL,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = 1L,
        position = position,
        weightKg = null,
        reps = null,
        seconds = seconds,
        calories = null,
        distanceM = distanceM,
        loggedAt = Instant.parse("2026-11-01T10:00:00Z"),
        type = type,
        intensity = intensity,
    )

    // Metrics

    @Test
    fun `the cardio metrics are speed, intensity, distance and time, in that order`() {
        assertEquals(
            listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.DISTANCE, ProgressMetric.MINUTES),
            ProgressRules.CARDIO_METRICS,
        )
    }

    // Default metric

    @Test
    fun `speed is the default when a set has distance and time`() {
        val sets = listOf(set(1L, 1L, seconds = 600, distanceM = 2000), set(2L, 1L, seconds = 600, intensity = 0.10))

        assertEquals(ProgressMetric.SPEED, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `intensity is the default when there is no distance but there is an intensity and time`() {
        val sets = listOf(set(1L, 1L, seconds = 600, intensity = 0.10), set(2L, 1L, seconds = 600))

        assertEquals(ProgressMetric.INTENSITY, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `time is the default when there is only time`() {
        assertEquals(ProgressMetric.MINUTES, ProgressRules.defaultCardioMetric(listOf(set(1L, 1L, seconds = 600))))
    }

    @Test
    fun `time is the default with no data at all`() {
        assertEquals(ProgressMetric.MINUTES, ProgressRules.defaultCardioMetric(emptyList()))
    }

    @Test
    fun `warm-up sets do not choose the default`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, distanceM = 2000, type = SetType.WARMUP),
            set(2L, 1L, seconds = 600, intensity = 0.10),
        )

        assertEquals(ProgressMetric.INTENSITY, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `metricFor a cardio machine is the default cardio metric`() {
        assertEquals(
            ProgressMetric.INTENSITY,
            ProgressRules.metricFor(bike, listOf(set(1L, 1L, seconds = 600, intensity = 0.10))),
        )
    }

    // Points

    @Test
    fun `speed points are the session's total distance over its total time`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, distanceM = 2000),
            set(2L, 1L, seconds = 1200, distanceM = 3000, position = 1),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.SPEED)

        assertEquals(1, points.size)
        assertEquals(5000.0 / 1800.0 * 3.6, points.single().value, 1e-9)
    }

    @Test
    fun `intensity points are the session's time-weighted average intensity as a decimal`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, intensity = 0.10),
            set(2L, 1L, seconds = 1200, intensity = 0.20, position = 1),
            set(3L, 2L, seconds = 900, intensity = 0.12),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.INTENSITY)

        assertEquals(listOf(day1, day2), points.map { it.date })
        assertEquals((0.10 * 600 + 0.20 * 1200) / 1800.0, points[0].value, 1e-9)
        assertEquals(0.12, points[1].value, 1e-9)
    }

    @Test
    fun `intensity points ignore warm-up sets`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, intensity = 0.10),
            set(2L, 1L, seconds = 600, intensity = 5.00, type = SetType.WARMUP, position = 1),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.INTENSITY)

        assertEquals(0.10, points.single().value, 1e-9)
    }

    @Test
    fun `sessions without an intensity and time have no intensity point`() {
        val sets = listOf(set(1L, 1L, seconds = 600), set(2L, 2L, seconds = 0, intensity = 0.10))

        assertTrue(ProgressRules.points(sets, dates, ProgressMetric.INTENSITY).isEmpty())
    }

    @Test
    fun `distance points are the session's total in km, and sessions with no distance are skipped`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, distanceM = 2000),
            set(2L, 1L, seconds = 600, distanceM = 1500, position = 1),
            set(3L, 2L, seconds = 600),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.DISTANCE)

        assertEquals(1, points.size)
        assertEquals(3.5, points.single().value, 1e-9)
    }

    @Test
    fun `minute points are the session's total minutes`() {
        val sets = listOf(set(1L, 1L, seconds = 600), set(2L, 1L, seconds = 1200, position = 1))

        val points = ProgressRules.points(sets, dates, ProgressMetric.MINUTES)

        assertEquals(30.0, points.single().value, 0.0)
    }

    @Test
    fun `points are not made for sessions outside the given dates`() {
        val sets = listOf(set(1L, 1L, seconds = 600, intensity = 0.10), set(2L, 9L, seconds = 600, intensity = 0.20))

        val points = ProgressRules.points(sets, dates, ProgressMetric.INTENSITY)

        assertEquals(listOf(1L), points.map { it.sessionId })
    }
}

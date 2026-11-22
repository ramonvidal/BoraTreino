package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Cardio progress by speed, intensity, distance and time, from the phase 41 contract. */
class ProgressCardioMetricsTest {
    private val at = Instant.parse("2026-11-02T18:00:00Z")
    private val bike = Machine(1L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)
    private val day1 = LocalDate.of(2026, 10, 1)
    private val day2 = LocalDate.of(2026, 10, 8)
    private val day3 = LocalDate.of(2026, 10, 15)
    private val dates = mapOf(1L to day1, 2L to day2, 3L to day3)

    private fun set(
        id: Long,
        sessionId: Long,
        seconds: Int? = null,
        distanceM: Int? = null,
        intensity: Int? = null,
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
        loggedAt = at,
        type = type,
        intensity = intensity,
    )

    // Metric list

    @Test
    fun `the three new metrics are the last entries of the enum`() {
        assertEquals(
            listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.DISTANCE),
            ProgressMetric.values().toList().takeLast(3),
        )
    }

    @Test
    fun `the cardio detail offers speed, intensity, distance and time in that order`() {
        assertEquals(
            listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.DISTANCE, ProgressMetric.MINUTES),
            ProgressRules.CARDIO_METRICS,
        )
    }

    // defaultCardioMetric

    @Test
    fun `speed is the default when a set has distance and time`() {
        val sets = listOf(set(1L, 1L, seconds = 600, distanceM = 2000), set(2L, 1L, seconds = 600, intensity = 10))

        assertEquals(ProgressMetric.SPEED, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `intensity is the default when there is no distance but there is an intensity and time`() {
        val sets = listOf(set(1L, 1L, seconds = 600, intensity = 10), set(2L, 1L, seconds = 600))

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
        val sets = listOf(set(1L, 1L, seconds = 600, distanceM = 2000, type = SetType.WARMUP))

        assertEquals(ProgressMetric.MINUTES, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `metricFor a cardio machine is the default cardio metric`() {
        assertEquals(
            ProgressMetric.INTENSITY,
            ProgressRules.metricFor(bike, listOf(set(1L, 1L, seconds = 600, intensity = 10))),
        )
    }

    // points: SPEED

    @Test
    fun `speed points are the session's total distance over its total time, one per session with distance`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, distanceM = 2000),
            set(2L, 1L, seconds = 1200, distanceM = 3000, position = 1),
            set(3L, 2L, seconds = 1800),
            set(4L, 3L, seconds = 1800, distanceM = 6000),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.SPEED)

        assertEquals(listOf(1L, 3L), points.map { it.sessionId })
        assertEquals(10.0, points[0].value, 1e-9)
        assertEquals(12.0, points[1].value, 1e-9)
    }

    @Test
    fun `speed points leave out warm-up sets`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, distanceM = 2000),
            set(2L, 1L, seconds = 1200, distanceM = 3000, position = 1),
            set(3L, 1L, seconds = 600, distanceM = 9000, type = SetType.WARMUP),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.SPEED)

        assertEquals(10.0, points.single().value, 1e-9)
    }

    @Test
    fun `the best speed session is the fastest one`() {
        val sets = listOf(
            set(1L, 1L, seconds = 1800, distanceM = 5000),
            set(2L, 3L, seconds = 1800, distanceM = 6000),
            set(3L, 2L, seconds = 1800, distanceM = 4000),
        )
        val points = ProgressRules.points(sets, dates, ProgressMetric.SPEED)

        assertEquals(3L, ProgressRules.best(points)!!.sessionId)
    }

    // points: INTENSITY

    @Test
    fun `intensity points are the session's time-weighted average intensity`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, intensity = 10),
            set(2L, 1L, seconds = 1200, intensity = 20, position = 1),
            set(3L, 1L, seconds = 600, position = 2),
            set(4L, 2L, seconds = 900, intensity = 12),
            set(5L, 3L, seconds = 600, distanceM = 1000),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.INTENSITY)

        assertEquals(listOf(1L, 2L), points.map { it.sessionId })
        assertEquals(50.0 / 3.0, points[0].value, 1e-9)
        assertEquals(12.0, points[1].value, 1e-9)
    }

    @Test
    fun `intensity points ignore warm-up sets`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, intensity = 10),
            set(2L, 1L, seconds = 600, intensity = 500, type = SetType.WARMUP),
        )

        assertEquals(10.0, ProgressRules.points(sets, dates, ProgressMetric.INTENSITY).single().value, 1e-9)
    }

    // points: DISTANCE and MINUTES

    @Test
    fun `distance points are the session's total in km, and sessions with no distance are skipped`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, distanceM = 2000),
            set(2L, 1L, seconds = 1200, distanceM = 3000, position = 1),
            set(3L, 2L, seconds = 900),
            set(4L, 3L, seconds = 600, distanceM = 1500),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.DISTANCE)

        assertEquals(listOf(1L, 3L), points.map { it.sessionId })
        assertEquals(5.0, points[0].value, 1e-9)
        assertEquals(1.5, points[1].value, 1e-9)
    }

    @Test
    fun `minute points are the session's total minutes`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, distanceM = 2000),
            set(2L, 1L, seconds = 1200, distanceM = 3000, position = 1),
            set(3L, 2L, seconds = 900),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.MINUTES)

        assertEquals(30.0, points[0].value, 1e-9)
        assertEquals(15.0, points[1].value, 1e-9)
    }

    @Test
    fun `points are not made for sessions outside the given dates`() {
        val sets = listOf(set(1L, 99L, seconds = 600, distanceM = 2000))

        assertEquals(emptyList<SessionPoint>(), ProgressRules.points(sets, dates, ProgressMetric.DISTANCE))
        assertNull(ProgressRules.best(ProgressRules.points(sets, dates, ProgressMetric.SPEED)))
    }
}

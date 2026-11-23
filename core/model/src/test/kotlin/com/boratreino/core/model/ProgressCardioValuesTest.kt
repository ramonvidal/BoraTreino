package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Cardio progress by the bike console values: power, heart rate, W/kg, TSS and IF (phase 42). */
class ProgressCardioValuesTest {
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
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
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
        watts = watts,
        heartRate = heartRate,
        wattsPerKg = wattsPerKg,
        tss = tss,
        intensityFactor = intensityFactor,
    )

    // defaultCardioMetric with the console values

    @Test
    fun `power is the default when a set has watts and time and no distance`() {
        val sets = listOf(set(1L, 1L, seconds = 600, watts = 150), set(2L, 1L, seconds = 600, intensityFactor = 0.8))

        assertEquals(ProgressMetric.POWER, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `speed still beats power`() {
        val sets = listOf(set(1L, 1L, seconds = 600, distanceM = 2000), set(2L, 1L, seconds = 600, watts = 150))

        assertEquals(ProgressMetric.SPEED, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `power beats intensity factor and intensity`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, intensityFactor = 0.8, intensity = 10),
            set(2L, 1L, seconds = 600, watts = 150),
        )

        assertEquals(ProgressMetric.POWER, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `intensity factor is the default when there is an IF and time but no watts`() {
        val sets = listOf(set(1L, 1L, seconds = 600, intensityFactor = 0.8), set(2L, 1L, seconds = 600, intensity = 10))

        assertEquals(ProgressMetric.INTENSITY_FACTOR, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `watts without time do not choose power`() {
        val sets = listOf(set(1L, 1L, watts = 150))

        assertEquals(ProgressMetric.MINUTES, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `an IF without time does not choose intensity factor`() {
        val sets = listOf(set(1L, 1L, intensityFactor = 0.8), set(2L, 1L, seconds = 600, intensity = 10))

        assertEquals(ProgressMetric.INTENSITY, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `warm-up sets do not choose power`() {
        val sets = listOf(set(1L, 1L, seconds = 600, watts = 150, type = SetType.WARMUP))

        assertEquals(ProgressMetric.MINUTES, ProgressRules.defaultCardioMetric(sets))
    }

    @Test
    fun `metricFor a cardio machine with watts is power`() {
        assertEquals(ProgressMetric.POWER, ProgressRules.metricFor(bike, listOf(set(1L, 1L, seconds = 600, watts = 150))))
    }

    // points: POWER

    @Test
    fun `power points are the session's time-weighted average watts, one per session with watts and time`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, watts = 100),
            set(2L, 1L, seconds = 1200, watts = 200, position = 1),
            set(3L, 2L, seconds = 600),
            set(4L, 3L, watts = 300),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.POWER)

        assertEquals(listOf(1L), points.map { it.sessionId })
        assertEquals(500.0 / 3.0, points.single().value, 1e-9)
    }

    @Test
    fun `power point topSet is the longest contributing set and setCount counts every set of the session`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, watts = 100),
            set(2L, 1L, seconds = 1200, watts = 200, position = 1),
            set(3L, 1L, seconds = 1800, position = 2),
        )

        val point = ProgressRules.points(sets, dates, ProgressMetric.POWER).single()

        assertEquals(2L, point.topSet.id)
        assertEquals(3, point.setCount)
    }

    @Test
    fun `power points leave out warm-up sets`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, watts = 100),
            set(2L, 1L, seconds = 600, watts = 400, type = SetType.WARMUP, position = 1),
        )

        assertEquals(100.0, ProgressRules.points(sets, dates, ProgressMetric.POWER).single().value, 1e-9)
    }

    @Test
    fun `power points skip sets whose session is unknown`() {
        val sets = listOf(set(1L, 99L, seconds = 600, watts = 100))

        assertTrue(ProgressRules.points(sets, dates, ProgressMetric.POWER).isEmpty())
    }

    // points: HEART_RATE

    @Test
    fun `heart rate points are the session's time-weighted average bpm`() {
        val sets = listOf(
            set(1L, 1L, seconds = 300, heartRate = 130),
            set(2L, 1L, seconds = 900, heartRate = 150, position = 1),
            set(3L, 2L, seconds = 600),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.HEART_RATE)

        assertEquals(listOf(1L), points.map { it.sessionId })
        assertEquals(145.0, points.single().value, 1e-9)
    }

    // points: WATTS_PER_KG

    @Test
    fun `watts per kg points are the session's time-weighted average`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, wattsPerKg = 2.0),
            set(2L, 1L, seconds = 600, wattsPerKg = 3.0, position = 1),
            set(3L, 2L, wattsPerKg = 4.0),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.WATTS_PER_KG)

        assertEquals(listOf(1L), points.map { it.sessionId })
        assertEquals(2.5, points.single().value, 1e-9)
    }

    // points: INTENSITY_FACTOR

    @Test
    fun `intensity factor points are the session's time-weighted average`() {
        val sets = listOf(
            set(1L, 1L, seconds = 1200, intensityFactor = 0.70),
            set(2L, 1L, seconds = 600, intensityFactor = 0.85, position = 1),
            set(3L, 2L, seconds = 600, intensity = 12),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.INTENSITY_FACTOR)

        assertEquals(listOf(1L), points.map { it.sessionId })
        assertEquals((0.70 * 1200 + 0.85 * 600) / 1800, points.single().value, 1e-9)
    }

    // points: TSS

    @Test
    fun `tss points are the sum of the session's TSS`() {
        val sets = listOf(
            set(1L, 1L, seconds = 600, tss = 20),
            set(2L, 1L, seconds = 600, tss = 28, position = 1),
            set(3L, 2L, seconds = 600),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.TSS)

        assertEquals(listOf(1L), points.map { it.sessionId })
        assertEquals(48.0, points.single().value, 1e-9)
    }

    @Test
    fun `a session with one set shows that set's TSS`() {
        val points = ProgressRules.points(listOf(set(1L, 2L, tss = 30)), dates, ProgressMetric.TSS)

        assertEquals(30.0, points.single().value, 1e-9)
    }

    @Test
    fun `tss points do not need time`() {
        val points = ProgressRules.points(listOf(set(1L, 1L, tss = 35)), dates, ProgressMetric.TSS)

        assertEquals(35.0, points.single().value, 1e-9)
    }

    @Test
    fun `a session with a TSS of zero still has a point`() {
        val points = ProgressRules.points(listOf(set(1L, 1L, seconds = 600, tss = 0)), dates, ProgressMetric.TSS)

        assertEquals(0.0, points.single().value, 1e-9)
    }

    @Test
    fun `tss points are ascending by date`() {
        val sets = listOf(set(1L, 3L, tss = 50), set(2L, 1L, tss = 10))

        val points = ProgressRules.points(sets, dates, ProgressMetric.TSS)

        assertEquals(listOf(day1, day3), points.map { it.date })
    }
}

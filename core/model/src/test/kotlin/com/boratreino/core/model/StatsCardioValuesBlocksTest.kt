package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * StatsReport.cardioBlocks with the bike console values (phase 42). "Today" is Wednesday 2026-11-11; a four week
 * period has 7-day blocks starting on 15 Oct, 22 Oct, 29 Oct and 5 Nov.
 */
class StatsCardioValuesBlocksTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val at = Instant.parse("2026-11-02T18:00:00Z")
    private val bike = Machine(1L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)

    private fun day(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private fun session(id: Long, date: LocalDate) =
        Session(id, date.atTime(10, 0).toInstant(ZoneOffset.UTC), null, null, null)

    private fun cardio(
        id: Long,
        sessionId: Long,
        seconds: Int? = 600,
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = bike.id,
        position = 0,
        weightKg = null,
        reps = null,
        seconds = seconds,
        calories = null,
        distanceM = null,
        loggedAt = at,
        watts = watts,
        heartRate = heartRate,
        wattsPerKg = wattsPerKg,
        tss = tss,
        intensityFactor = intensityFactor,
    )

    private fun report(sets: List<WorkoutSet>): StatsReport = StatsRules.report(
        period = StatsPeriod.FOUR_WEEKS,
        today = today,
        firstDayOfWeek = DayOfWeek.MONDAY,
        weeklyGoal = 3,
        machines = listOf(bike),
        sessions = listOf(
            session(1L, day(10, 16)),
            session(2L, day(10, 20)),
            session(3L, day(10, 25)),
            session(4L, day(11, 9)),
        ),
        sets = sets,
        zone = ZoneOffset.UTC,
    )

    private val sets = listOf(
        // Block 1 (15 to 21 Oct): two sessions, both values on the first set, power and IF on the second.
        cardio(1L, 1L, seconds = 600, watts = 100, heartRate = 120, wattsPerKg = 1.8, tss = 20, intensityFactor = 0.70),
        cardio(2L, 2L, seconds = 1200, watts = 200, heartRate = 150, tss = 30, intensityFactor = 0.85),
        // Block 2 (22 to 28 Oct): cardio with no console values.
        cardio(3L, 3L, seconds = 900),
        // Block 4 (5 to 11 Nov): TSS only.
        cardio(4L, 4L, seconds = 600, tss = 40),
    )

    @Test
    fun `a block averages power, heart rate and IF by time`() {
        val block = report(sets).cardioBlocks[0]

        assertEquals(day(10, 15), block.weekStart)
        assertEquals((100.0 * 600 + 200.0 * 1200) / 1800, block.powerAvg!!, 1e-9)
        assertEquals((120.0 * 600 + 150.0 * 1200) / 1800, block.heartRateAvg!!, 1e-9)
        assertEquals((0.70 * 600 + 0.85 * 1200) / 1800, block.intensityFactorAvg!!, 1e-9)
    }

    @Test
    fun `a block totals the TSS of its sets`() {
        assertEquals(50, report(sets).cardioBlocks[0].tssTotal)
    }

    @Test
    fun `a block with cardio and no console values has null values`() {
        val block = report(sets).cardioBlocks[1]

        assertEquals(15, block.minutes)
        assertNull(block.powerAvg)
        assertNull(block.heartRateAvg)
        assertNull(block.tssTotal)
        assertNull(block.intensityFactorAvg)
    }

    @Test
    fun `a block with only TSS has a TSS total and no averages`() {
        val block = report(sets).cardioBlocks[2]

        assertEquals(40, block.tssTotal)
        assertNull(block.powerAvg)
        assertNull(block.intensityFactorAvg)
    }

    @Test
    fun `the existing totals still come from the same sets`() {
        val block = report(sets).cardioBlocks[0]

        assertEquals(30, block.minutes)
        assertEquals(2, block.sessions)
    }
}

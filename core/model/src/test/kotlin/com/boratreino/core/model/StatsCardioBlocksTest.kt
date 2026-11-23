package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * StatsReport.cardioBlocks. "Today" is Wednesday 2026-11-11. A four week period starts on 2026-10-15 and its
 * 7-day blocks start on 15 Oct, 22 Oct, 29 Oct and 5 Nov.
 */
class StatsCardioBlocksTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val at = Instant.parse("2026-11-02T18:00:00Z")
    private val bike = Machine(1L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)
    private val bench = Machine(2L, "Bench", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false)
    private val machines = listOf(bike, bench)

    private fun day(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private fun session(id: Long, date: LocalDate) =
        Session(id, date.atTime(10, 0).toInstant(ZoneOffset.UTC), null, null, null)

    private fun cardio(
        id: Long,
        sessionId: Long,
        seconds: Int?,
        distanceM: Int? = null,
        intensity: Double? = null,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = bike.id,
        position = 0,
        weightKg = null,
        reps = null,
        seconds = seconds,
        calories = null,
        distanceM = distanceM,
        loggedAt = at,
        intensity = intensity,
    )

    private fun strength(id: Long, sessionId: Long) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = bench.id,
        position = 0,
        weightKg = 40.0,
        reps = 10,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = at,
    )

    private fun report(
        sessions: List<Session>,
        sets: List<WorkoutSet>,
        period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
    ): StatsReport = StatsRules.report(
        period = period,
        today = today,
        firstDayOfWeek = DayOfWeek.MONDAY,
        weeklyGoal = 3,
        machines = machines,
        sessions = sessions,
        sets = sets,
        zone = ZoneOffset.UTC,
    )

    private val sessions = listOf(
        session(1L, day(10, 16)),
        session(2L, day(10, 20)),
        session(3L, day(10, 25)),
        session(4L, day(11, 9)),
        session(5L, day(10, 14)),
    )

    private val sets = listOf(
        cardio(1L, 1L, seconds = 600, distanceM = 2000, intensity = 0.10),
        cardio(2L, 2L, seconds = 1200, distanceM = 3000, intensity = 0.20),
        strength(3L, 2L),
        strength(4L, 3L),
        cardio(5L, 4L, seconds = 900),
        cardio(6L, 5L, seconds = 6000, distanceM = 9000),
    )

    @Test
    fun `cardio blocks hold one entry per block with cardio, oldest first, from the period start`() {
        val blocks = report(sessions, sets).cardioBlocks

        assertEquals(listOf(day(10, 15), day(11, 5)), blocks.map { it.weekStart })
    }

    @Test
    fun `the first block totals its minutes, distance, sessions, intensity and speed`() {
        val block = report(sessions, sets).cardioBlocks[0]

        assertEquals(30, block.minutes)
        assertEquals(5000, block.distanceM)
        assertEquals(2, block.sessions)
        assertEquals((0.10 * 600 + 0.20 * 1200) / 1800.0, block.intensityAvg!!, 1e-9)
        assertEquals(10.0, block.speedKmh!!, 1e-9)
    }

    @Test
    fun `a block with cardio and no distance or intensity has null averages`() {
        val block = report(sessions, sets).cardioBlocks[1]

        assertEquals(15, block.minutes)
        assertEquals(0, block.distanceM)
        assertEquals(1, block.sessions)
        assertNull(block.intensityAvg)
        assertNull(block.speedKmh)
    }

    @Test
    fun `sessions outside the period do not count`() {
        val blocks = report(sessions, sets, StatsPeriod.ONE_WEEK).cardioBlocks

        assertEquals(listOf(day(11, 5)), blocks.map { it.weekStart })
        assertEquals(15, blocks.single().minutes)
    }

    @Test
    fun `a period with no cardio has no cardio blocks`() {
        val onlyStrength = listOf(strength(1L, 2L), strength(2L, 3L))

        assertTrue(report(sessions, onlyStrength).cardioBlocks.isEmpty())
    }
}

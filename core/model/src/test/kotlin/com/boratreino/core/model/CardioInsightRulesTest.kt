package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Cardio efficiency (speed km/h per unit of intensity) and the speed and efficiency trends. Expected numbers are
 * worked out by hand; see the comments on each test.
 */
class CardioInsightRulesTest {
    private fun oct(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    private fun set(id: Long, sessionId: Long, machineId: Long, date: LocalDate) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = machineId,
        position = 0,
        weightKg = null,
        reps = null,
        seconds = 600,
        calories = null,
        distanceM = 1500,
        loggedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
    )

    private fun point(sessionId: Long, date: LocalDate, value: Double, machineId: Long = 1L) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = value,
        topSet = set(sessionId, sessionId, machineId, date),
        setCount = 1,
    )

    private fun cardio(id: Long, speed: List<SessionPoint>, intensity: List<SessionPoint> = emptyList()): MachineProgress {
        val machine = Machine(id, "Cardio $id", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)
        return MachineProgress(
            machine = machine,
            metric = ProgressMetric.SPEED,
            points = speed,
            cardioPoints = mapOf(
                ProgressMetric.SPEED to speed,
                ProgressMetric.INTENSITY to intensity,
            ),
        )
    }

    /**
     * Machine 1, speed sessions 1..5 on 1, 8, 15, 22, 29 October with speeds 9, 10, 10, 12, 8 km/h.
     * Intensity on sessions 1 (0.6), 2 (0.5), 4 (0.0), 5 (0.8) and 7 (0.7, no speed on that session), not 3.
     * Efficiency: s1 = 9 / 0.6 = 15, s2 = 10 / 0.5 = 20, s5 = 8 / 0.8 = 10; s3 has no intensity, s4 has 0.
     *
     * Machine 2 has one speed session on 30 October (session 6, 6 km/h, intensity 0.5), efficiency 12.
     */
    private val machineOne = cardio(
        id = 1L,
        speed = listOf(
            point(1L, oct(1), 9.0),
            point(2L, oct(8), 10.0),
            point(3L, oct(15), 10.0),
            point(4L, oct(22), 12.0),
            point(5L, oct(29), 8.0),
        ),
        intensity = listOf(
            point(1L, oct(1), 0.6),
            point(2L, oct(8), 0.5),
            point(4L, oct(22), 0.0),
            point(5L, oct(29), 0.8),
            point(7L, oct(20), 0.7),
        ),
    )

    private val machineTwo = cardio(
        id = 2L,
        speed = listOf(point(6L, oct(30), 6.0, machineId = 2L)),
        intensity = listOf(point(6L, oct(30), 0.5, machineId = 2L)),
    )

    // Efficiency per set

    @Test
    fun `efficiency is speed per unit of intensity`() {
        assertEquals(20.0, CardioInsightRules.efficiency(10.0, 0.5)!!, 1e-12)
        assertEquals(15.0, CardioInsightRules.efficiency(9.0, 0.6)!!, 1e-12)
    }

    @Test
    fun `efficiency is null without intensity or with an intensity of 0`() {
        assertNull(CardioInsightRules.efficiency(10.0, null))
        assertNull(CardioInsightRules.efficiency(10.0, 0.0))
        assertNull(CardioInsightRules.efficiency(10.0, -0.5))
    }

    @Test
    fun `efficiency is null without speed`() {
        assertNull(CardioInsightRules.efficiency(null, 0.5))
    }

    // Insight

    @Test
    fun `there is no insight without a cardio session with distance and time`() {
        val strength = Machine(3L, "Bench", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false)
        val lifting = MachineProgress(
            machine = strength,
            metric = ProgressMetric.WEIGHT,
            points = emptyList(),
            oneRmPoints = listOf(point(9L, oct(2), 80.0, machineId = 3L)),
        )

        assertNull(CardioInsightRules.insight(emptyList()))
        assertNull(CardioInsightRules.insight(listOf(lifting)))
        assertNull(CardioInsightRules.insight(listOf(cardio(4L, speed = emptyList(), intensity = listOf(point(8L, oct(3), 0.5, 4L))))))
    }

    @Test
    fun `sessions count distinct sessions with a speed across cardio machines`() {
        val insight = CardioInsightRules.insight(listOf(machineOne, machineTwo))

        assertNotNull(insight)
        assertEquals(6, insight!!.sessions)
    }

    @Test
    fun `latest efficiency is the most recent session and best is the highest`() {
        val insight = CardioInsightRules.insight(listOf(machineOne, machineTwo))!!

        // Latest: session 6 on 30 October (12). Best: session 2 (20), not the latest.
        assertEquals(12.0, insight.efficiencyLatest!!, 1e-12)
        assertEquals(20.0, insight.efficiencyBest!!, 1e-12)
    }

    @Test
    fun `the speed trend follows the single machine with the most speed sessions`() {
        val insight = CardioInsightRules.insight(listOf(machineOne, machineTwo))!!

        // Machine 1 only: 5 points over 28 days, speeds 9, 10, 10, 12, 8 at weeks 0..4. The slope is exactly 0
        // (sum of (week - 2)(speed - 9.8) = 0), the residual sum of squares is 8.8, so the residual SD is
        // sqrt(8.8 / 3) and the margin is 3.182 x sqrt(8.8 / 3) / sqrt(10) = 1.7237. Interval contains 0.
        assertEquals(5, insight.speedTrend.points)
        val fit = insight.speedTrend.fit!!
        assertEquals(0.0, fit.slopePerWeek, 1e-9)
        assertEquals(-1.7237, fit.slopeLow, 1e-3)
        assertEquals(1.7237, fit.slopeHigh, 1e-3)
        assertEquals(TrendVerdict.PLATEAU, insight.speedTrend.verdict)
    }

    @Test
    fun `the efficiency trend follows the single machine with the most efficiency points`() {
        val insight = CardioInsightRules.insight(listOf(machineOne, machineTwo))!!

        // Machine 1 has efficiencies 15 (1 Oct), 20 (8 Oct), 10 (29 Oct): 3 points, machine 2 has 1.
        // Weeks 0, 1, 4 with mean week 5/3 and Sxx = 26/3, Sxy = -15, so the slope is -45/26 per week.
        assertEquals(3, insight.efficiencyTrend.points)
        assertEquals(TrendVerdict.NOT_ENOUGH_DATA, insight.efficiencyTrend.verdict)
        assertEquals(-45.0 / 26.0, insight.efficiencyTrend.fit!!.slopePerWeek, 1e-9)
    }

    @Test
    fun `a machine with speed but no intensity has no efficiency`() {
        val speedOnly = cardio(
            id = 5L,
            speed = listOf(point(20L, oct(5), 10.0, machineId = 5L), point(21L, oct(12), 11.0, machineId = 5L)),
        )
        val insight = CardioInsightRules.insight(listOf(speedOnly))

        assertNotNull(insight)
        assertEquals(2, insight!!.sessions)
        assertNull(insight.efficiencyLatest)
        assertNull(insight.efficiencyBest)
        assertEquals(TrendVerdict.NOT_ENOUGH_DATA, insight.efficiencyTrend.verdict)
        assertEquals(0, insight.efficiencyTrend.points)
        assertNull(insight.efficiencyTrend.fit)
    }

    @Test
    fun `a session with intensity but no speed is not counted`() {
        // Session 7 on 20 October has intensity 0.7 but no speed, so it is not a session of the insight.
        val insight = CardioInsightRules.insight(listOf(machineOne))!!

        assertEquals(5, insight.sessions)
        assertEquals(20.0, insight.efficiencyBest!!, 1e-12)
    }
}

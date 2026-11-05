package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class OneRepMaxRulesTest {
    private val t0 = Instant.parse("2026-10-01T18:00:00Z")

    private fun set(
        id: Long,
        sessionId: Long = 1L,
        weightKg: Double? = 80.0,
        reps: Int? = 8,
        position: Int = 0,
        type: SetType = SetType.NORMAL,
        seconds: Int? = null,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = 1L,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = seconds,
        calories = null,
        distanceM = null,
        loggedAt = t0.plusSeconds(id * 60),
        type = type,
    )

    // estimate(weight, reps)

    @Test
    fun `eight reps at 80 kg estimates 101 point 33`() {
        assertEquals(101.3333333, OneRepMaxRules.estimate(80.0, 8)!!, 0.0001)
    }

    @Test
    fun `one rep is its own weight exactly`() {
        assertEquals(100.0, OneRepMaxRules.estimate(100.0, 1)!!, 0.0)
    }

    @Test
    fun `twelve reps is still estimated`() {
        assertEquals(70.0, OneRepMaxRules.estimate(50.0, 12)!!, 0.0001)
    }

    @Test
    fun `thirteen reps gives null`() {
        assertNull(OneRepMaxRules.estimate(50.0, 13))
    }

    @Test
    fun `zero reps gives null`() {
        assertNull(OneRepMaxRules.estimate(50.0, 0))
    }

    @Test
    fun `zero or negative weight gives null`() {
        assertNull(OneRepMaxRules.estimate(0.0, 5))
        assertNull(OneRepMaxRules.estimate(-10.0, 5))
    }

    @Test
    fun `max reps constant is twelve`() {
        assertEquals(12, OneRepMaxRules.MAX_REPS)
    }

    // estimate(set)

    @Test
    fun `a working set is estimated`() {
        assertEquals(101.3333333, OneRepMaxRules.estimate(set(1))!!, 0.0001)
    }

    @Test
    fun `drop and failure sets count as working`() {
        assertNotNull(OneRepMaxRules.estimate(set(1, type = SetType.DROP)))
        assertNotNull(OneRepMaxRules.estimate(set(2, type = SetType.FAILURE)))
    }

    @Test
    fun `a warm-up gives null`() {
        assertNull(OneRepMaxRules.estimate(set(1, type = SetType.WARMUP)))
    }

    @Test
    fun `a set with null weight or null reps gives null`() {
        assertNull(OneRepMaxRules.estimate(set(1, weightKg = null)))
        assertNull(OneRepMaxRules.estimate(set(2, reps = null)))
    }

    @Test
    fun `a cardio set gives null`() {
        assertNull(OneRepMaxRules.estimate(set(1, weightKg = null, reps = null, seconds = 600)))
    }

    // bestSet

    @Test
    fun `best set has the highest estimate`() {
        val a = set(1, weightKg = 80.0, reps = 8) // 101.33
        val b = set(2, weightKg = 90.0, reps = 5) // 105
        val c = set(3, weightKg = 60.0, reps = 12) // 84
        assertEquals(b, OneRepMaxRules.bestSet(listOf(a, b, c)))
    }

    @Test
    fun `best set ties go to the heavier set`() {
        // 60 x 10 = 80, 80 x 0 not allowed; 72 x 4 = 81.6 ; use 80 x 1 = 80 and 60 x 10 = 80
        val light = set(1, weightKg = 60.0, reps = 10, position = 5)
        val heavy = set(2, weightKg = 80.0, reps = 1, position = 0)
        assertEquals(80.0, OneRepMaxRules.estimate(light)!!, 1e-9)
        assertEquals(80.0, OneRepMaxRules.estimate(heavy)!!, 1e-9)
        assertEquals(heavy, OneRepMaxRules.bestSet(listOf(light, heavy)))
        assertEquals(heavy, OneRepMaxRules.bestSet(listOf(heavy, light)))
    }

    @Test
    fun `best set full ties go to the later position`() {
        val early = set(1, position = 0)
        val late = set(2, position = 3)
        assertEquals(late, OneRepMaxRules.bestSet(listOf(late, early)))
        assertEquals(late, OneRepMaxRules.bestSet(listOf(early, late)))
    }

    @Test
    fun `best set ignores warm-ups and ineligible sets`() {
        val warm = set(1, weightKg = 200.0, reps = 5, type = SetType.WARMUP)
        val tooMany = set(2, weightKg = 100.0, reps = 20)
        val ok = set(3, weightKg = 40.0, reps = 10)
        assertEquals(ok, OneRepMaxRules.bestSet(listOf(warm, tooMany, ok)))
    }

    @Test
    fun `best set is null when none is eligible`() {
        assertNull(OneRepMaxRules.bestSet(emptyList()))
        assertNull(OneRepMaxRules.bestSet(listOf(set(1, type = SetType.WARMUP), set(2, reps = 13), set(3, weightKg = 0.0))))
    }

    // ProgressRules.oneRmPoints

    private val dates = mapOf(
        1L to LocalDate.of(2026, 10, 1),
        2L to LocalDate.of(2026, 10, 8),
        3L to LocalDate.of(2026, 10, 15),
    )

    @Test
    fun `one point per session with the session best`() {
        val s1a = set(1, sessionId = 1, weightKg = 80.0, reps = 8)
        val s1b = set(2, sessionId = 1, weightKg = 90.0, reps = 5, position = 1)
        val s2 = set(3, sessionId = 2, weightKg = 100.0, reps = 1)
        val points = ProgressRules.oneRmPoints(listOf(s1a, s1b, s2), dates)
        assertEquals(2, points.size)
        assertEquals(1L, points[0].sessionId)
        assertEquals(105.0, points[0].value, 1e-9)
        assertEquals(s1b, points[0].topSet)
        assertEquals(2, points[0].setCount)
        assertEquals(2L, points[1].sessionId)
        assertEquals(100.0, points[1].value, 1e-9)
        assertEquals(s2, points[1].topSet)
        assertEquals(1, points[1].setCount)
        assertEquals(LocalDate.of(2026, 10, 8), points[1].date)
    }

    @Test
    fun `set count is working sets and warm-ups are ignored`() {
        val warm = set(1, sessionId = 1, weightKg = 200.0, reps = 5, type = SetType.WARMUP)
        val w1 = set(2, sessionId = 1, weightKg = 80.0, reps = 8, position = 1)
        val w2 = set(3, sessionId = 1, weightKg = 70.0, reps = 10, position = 2)
        val points = ProgressRules.oneRmPoints(listOf(warm, w1, w2), dates)
        assertEquals(1, points.size)
        assertEquals(2, points[0].setCount)
        assertEquals(w1, points[0].topSet)
    }

    @Test
    fun `sets of unknown sessions are ignored`() {
        val points = ProgressRules.oneRmPoints(listOf(set(1, sessionId = 99), set(2, sessionId = 1)), dates)
        assertEquals(listOf(1L), points.map { it.sessionId })
    }

    @Test
    fun `points are ascending by date`() {
        val points = ProgressRules.oneRmPoints(
            listOf(set(1, sessionId = 3), set(2, sessionId = 1), set(3, sessionId = 2)),
            dates,
        )
        assertEquals(listOf(1L, 2L, 3L), points.map { it.sessionId })
        assertTrue(points.zipWithNext().all { (a, b) -> a.date <= b.date })
    }

    @Test
    fun `same date orders by session id`() {
        val same = mapOf(5L to LocalDate.of(2026, 10, 1), 4L to LocalDate.of(2026, 10, 1))
        val points = ProgressRules.oneRmPoints(listOf(set(1, sessionId = 5), set(2, sessionId = 4)), same)
        assertEquals(listOf(4L, 5L), points.map { it.sessionId })
    }

    @Test
    fun `empty input gives no points`() {
        assertTrue(ProgressRules.oneRmPoints(emptyList(), dates).isEmpty())
    }

    // ProgressMetric.ONE_RM through points()

    @Test
    fun `points with the one rm metric equals oneRmPoints`() {
        val sets = listOf(set(1, sessionId = 1), set(2, sessionId = 2, weightKg = 90.0, reps = 3))
        assertEquals(
            ProgressRules.oneRmPoints(sets, dates),
            ProgressRules.points(sets, dates, ProgressMetric.ONE_RM),
        )
    }
}

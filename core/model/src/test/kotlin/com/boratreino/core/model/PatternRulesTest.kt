package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Weekday and rest-day patterns of the estimated 1RM. Day 0 is Monday 2 November 2026, so 3 November is Tuesday,
 * 8 November is Sunday and 11 November is Wednesday. Percent differences are against each machine's own mean.
 */
class PatternRulesTest {
    private val monday: LocalDate = LocalDate.of(2026, 11, 2)
    private var nextSessionId = 1L

    private fun date(day: Int): LocalDate = monday.plusDays(day.toLong())

    private fun progress(id: Long, points: List<Pair<LocalDate, Double>>): MachineProgress {
        val machine = Machine(id, "Machine $id", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false)
        val sessionPoints = points.map { (d, value) ->
            val sessionId = nextSessionId++
            val topSet = WorkoutSet(
                id = sessionId,
                sessionId = sessionId,
                machineId = id,
                position = 0,
                weightKg = value,
                reps = 1,
                seconds = null,
                calories = null,
                distanceM = null,
                loggedAt = d.atStartOfDay(ZoneOffset.UTC).toInstant(),
            )
            SessionPoint(sessionId, d, value, topSet, 1)
        }
        return MachineProgress(machine, ProgressMetric.WEIGHT, emptyList(), oneRmPoints = sessionPoints)
    }

    private fun bucket(buckets: List<PatternBucket>, key: Int): PatternBucket? = buckets.firstOrNull { it.key == key }

    // Weekday

    @Test
    fun `weekday buckets hold the percent difference from the machine's own mean`() {
        // Mean of 100, 110, 90 is 100, so the indices are 0, +10 and -10.
        val report = PatternRules.analyze(listOf(progress(1L, listOf(date(0) to 100.0, date(2) to 110.0, date(4) to 90.0))))

        assertEquals(listOf(1, 3, 5), report.weekday.map { it.key })
        assertEquals(0.0, bucket(report.weekday, 1)!!.meanIndex, 1e-9)
        assertEquals(10.0, bucket(report.weekday, 3)!!.meanIndex, 1e-9)
        assertEquals(-10.0, bucket(report.weekday, 5)!!.meanIndex, 1e-9)
        assertEquals(1, bucket(report.weekday, 3)!!.n)
    }

    @Test
    fun `a bucket with one session has no standard error`() {
        val report = PatternRules.analyze(listOf(progress(1L, listOf(date(0) to 100.0, date(2) to 110.0, date(4) to 90.0))))

        assertNull(bucket(report.weekday, 3)!!.standardError)
    }

    @Test
    fun `weekday keys run from Monday 1 to Sunday 7`() {
        val week = (0..6).map { date(it) to 100.0 }
        val report = PatternRules.analyze(listOf(progress(1L, week)))

        assertEquals((1..7).toList(), report.weekday.map { it.key })
        assertTrue(report.weekday.all { it.n == 1 })
    }

    @Test
    fun `the first session of a machine is only in the weekday buckets`() {
        val report = PatternRules.analyze(listOf(progress(1L, listOf(date(0) to 100.0, date(2) to 110.0, date(4) to 90.0))))

        assertEquals(3, report.weekday.sumOf { it.n })
        assertEquals(2, report.rest.sumOf { it.n })
    }

    // Rest days

    @Test
    fun `rest buckets split by days since the previous session on the same machine`() {
        // Values 100, 120, 90, 100 have mean 102.5, so indices -100/41, 700/41, -500/41, -100/41.
        // Gaps: 7 days (key 2), 4 days (key 1), 1 day (key 0).
        val report = PatternRules.analyze(
            listOf(progress(2L, listOf(date(0) to 100.0, date(7) to 120.0, date(11) to 90.0, date(12) to 100.0))),
        )

        assertEquals(listOf(0, 1, 2), report.rest.map { it.key })
        assertEquals(-100.0 / 41.0, bucket(report.rest, 0)!!.meanIndex, 1e-9)
        assertEquals(-500.0 / 41.0, bucket(report.rest, 1)!!.meanIndex, 1e-9)
        assertEquals(700.0 / 41.0, bucket(report.rest, 2)!!.meanIndex, 1e-9)
        assertTrue(report.rest.all { it.n == 1 })
    }

    @Test
    fun `rest boundaries are 3 days for key 0, 4 to 6 for key 1 and 7 or more for key 2`() {
        // Gaps 3, 4, 6 and 7 days, every value 100 so the indices are all 0.
        val report = PatternRules.analyze(
            listOf(progress(3L, listOf(date(0) to 100.0, date(3) to 100.0, date(7) to 100.0, date(13) to 100.0, date(20) to 100.0))),
        )

        assertEquals(1, bucket(report.rest, 0)!!.n)
        assertEquals(2, bucket(report.rest, 1)!!.n)
        assertEquals(1, bucket(report.rest, 2)!!.n)
    }

    @Test
    fun `the standard error of two sessions is the sample SD over the root of two`() {
        // Indices +10 and -10 in a rest bucket: sample SD = sqrt(200), SE = sqrt(200) / sqrt(2) = 10.
        val report = PatternRules.analyze(listOf(progress(1L, listOf(date(0) to 100.0, date(2) to 110.0, date(4) to 90.0))))

        val rest = bucket(report.rest, 0)!!
        assertEquals(2, rest.n)
        assertEquals(0.0, rest.meanIndex, 1e-9)
        assertEquals(10.0, rest.standardError!!, 1e-9)
    }

    // Machines and sample size

    @Test
    fun `machines with fewer than three points are skipped`() {
        val three = progress(1L, listOf(date(0) to 100.0, date(2) to 110.0, date(4) to 90.0))
        val two = progress(5L, listOf(date(0) to 50.0, date(1) to 150.0))

        val report = PatternRules.analyze(listOf(three, two))

        assertEquals(3, report.sessionsUsed)
        assertEquals(3, report.weekday.sumOf { it.n })
        assertEquals(2, report.rest.sumOf { it.n })
    }

    @Test
    fun `cardio machines without one rep max points are not used`() {
        val cardio = Machine(7L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)
        val speed = listOf(SessionPoint(90L, date(0), 10.0, WorkoutSet(90L, 90L, 7L, 0, null, null, 600, null, 1500, date(0).atStartOfDay(ZoneOffset.UTC).toInstant()), 1))
        val progress = MachineProgress(cardio, ProgressMetric.SPEED, speed, cardioPoints = mapOf(ProgressMetric.SPEED to speed))

        val report = PatternRules.analyze(listOf(progress))

        assertEquals(0, report.sessionsUsed)
        assertTrue(report.weekday.isEmpty())
        assertTrue(report.rest.isEmpty())
        assertFalse(report.enough)
    }

    @Test
    fun `fifteen sessions are enough and fourteen are not`() {
        // Machines with 3, 3, 4 and 5 sessions make 15; with 4 instead of 5 they make 14.
        fun machines(last: Int) = listOf(
            progress(1L, (0..2).map { date(it) to 100.0 }),
            progress(2L, (0..2).map { date(it) to 100.0 }),
            progress(3L, (0..3).map { date(it) to 100.0 }),
            progress(4L, (0..(last - 1)).map { date(it) to 100.0 }),
        )

        val fifteen = PatternRules.analyze(machines(5))
        assertEquals(15, fifteen.sessionsUsed)
        assertTrue(fifteen.enough)

        val fourteen = PatternRules.analyze(machines(4))
        assertEquals(14, fourteen.sessionsUsed)
        assertFalse(fourteen.enough)
    }

    @Test
    fun `the minimum sessions and per bucket constants are 15 and 5`() {
        assertEquals(15, PatternRules.MIN_SESSIONS)
        assertEquals(5, PatternRules.MIN_PER_BUCKET)
    }

    @Test
    fun `no machines give an empty report that is not enough`() {
        val report = PatternRules.analyze(emptyList())

        assertTrue(report.weekday.isEmpty())
        assertTrue(report.rest.isEmpty())
        assertEquals(0, report.sessionsUsed)
        assertFalse(report.enough)
    }

    // Significance

    @Test
    fun `buckets below five sessions are never tested`() {
        // Four sessions Monday to Thursday: each weekday bucket has one session and the rest bucket has three.
        val report = PatternRules.analyze(
            listOf(progress(1L, listOf(date(0) to 100.0, date(1) to 130.0, date(2) to 70.0, date(3) to 100.0))),
        )

        val all = report.weekday + report.rest
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.n < PatternRules.MIN_PER_BUCKET })
        assertTrue(all.all { it.pValue == null && !it.significant })
        assertFalse(report.anySignificant)
    }

    @Test
    fun `a clearly different weekday with five or more sessions is significant`() {
        // Five Mondays at 120 or 130 and every other day at 98 or 102, over five weeks.
        val points = (0..34).map { day ->
            val value = when {
                day % 7 == 0 -> if ((day / 7) % 2 == 0) 130.0 else 120.0
                day % 2 == 0 -> 98.0
                else -> 102.0
            }
            date(day) to value
        }

        val report = PatternRules.analyze(listOf(progress(1L, points)))

        val monday = bucket(report.weekday, 1)!!
        assertEquals(5, monday.n)
        assertTrue(monday.significant)
        assertTrue(monday.pValue!! < 0.05)
        assertTrue(monday.tValue!! > 0.0)
        assertTrue(report.anySignificant)
    }

    @Test
    fun `noisy values with no weekday effect are not significant`() {
        val points = (0..34).map { day -> date(day) to 100.0 + ((day * 37) % 11 - 5) }

        val report = PatternRules.analyze(listOf(progress(1L, points)))

        assertTrue((report.weekday + report.rest).none { it.significant })
        assertFalse(report.anySignificant)
    }

    // Statistics helpers

    @Test
    fun `two sided p at the 5 percent critical values is about 0 point 05`() {
        // Critical values of Student's t: 2.776 for 4 degrees of freedom and 2.228 for 10.
        assertEquals(0.05, PatternRules.twoSidedP(2.776, 4), 1e-3)
        assertEquals(0.05, PatternRules.twoSidedP(2.228, 10), 1e-3)
    }

    @Test
    fun `two sided p is 1 at t 0 and the same for a positive and a negative t`() {
        assertEquals(1.0, PatternRules.twoSidedP(0.0, 4), 1e-12)
        assertEquals(PatternRules.twoSidedP(2.0, 10), PatternRules.twoSidedP(-2.0, 10), 1e-12)
    }

    @Test
    fun `two sided p of a large t is close to 0`() {
        assertTrue(PatternRules.twoSidedP(100.0, 4) < 1e-6)
    }

    @Test
    fun `two sided p with one degree of freedom follows the Cauchy distribution`() {
        // P(|T| > t) = 1 - (2 / pi) arctan(t): exactly 0.5 at t = 1.
        assertEquals(0.5, PatternRules.twoSidedP(1.0, 1), 1e-9)
        assertEquals(1.0 - 2.0 / Math.PI * Math.atan(2.0), PatternRules.twoSidedP(2.0, 1), 1e-9)
    }

    @Test
    fun `regularized beta is 0 at x 0 and 1 at x 1`() {
        assertEquals(0.0, PatternRules.regularizedBeta(0.0, 2.0, 3.0), 0.0)
        assertEquals(1.0, PatternRules.regularizedBeta(1.0, 2.0, 3.0), 0.0)
    }

    @Test
    fun `regularized beta with both parameters 1 is x`() {
        for (x in listOf(0.1, 0.3, 0.5, 0.7, 0.9)) {
            assertEquals(x, PatternRules.regularizedBeta(x, 1.0, 1.0), 1e-9)
        }
    }

    @Test
    fun `holm keeps the p values that pass the step down thresholds`() {
        // Sorted tested p values: 0.01, 0.011, 0.03, 0.04 with m = 4. Thresholds 0.0125 and 0.01667 pass, 0.025 fails.
        val flags = PatternRules.holmFlags(listOf(0.01, 0.04, null, 0.011, 0.03))

        assertEquals(listOf(true, false, false, true, false), flags)
    }

    @Test
    fun `holm with only untested buckets flags nothing`() {
        assertEquals(listOf(false, false), PatternRules.holmFlags(listOf(null, null)))
    }

    @Test
    fun `holm flags a single p value below alpha`() {
        assertEquals(listOf(true), PatternRules.holmFlags(listOf(0.049)))
    }
}

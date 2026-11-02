package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class ProgressRulesTest {
    private val t0 = Instant.parse("2026-10-01T18:00:00Z")
    private val today = LocalDate.of(2026, 11, 2)

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    private fun machine(kind: MachineKind, id: Long = 1L) = Machine(
        id = id,
        name = "Machine $id",
        muscleGroup = MuscleGroup.CHEST,
        kind = kind,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun set(
        id: Long,
        sessionId: Long,
        weightKg: Double?,
        reps: Int?,
        machineId: Long = 1L,
        position: Int = 0,
        at: Instant = t0.plusSeconds(id * 60),
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = machineId,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = at,
    )

    private fun point(date: LocalDate, value: Double, sessionId: Long = 1L) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = value,
        topSet = set(sessionId, sessionId, value, 1),
        setCount = 1,
    )

    // metricFor

    @Test
    fun `a bodyweight machine with no weighted set measures reps`() {
        val sets = listOf(set(1, 1, null, 12), set(2, 1, 0.0, 10))
        assertEquals(ProgressMetric.REPS, ProgressRules.metricFor(machine(MachineKind.BODYWEIGHT_REPS), sets))
    }

    @Test
    fun `a bodyweight machine with no sets measures reps`() {
        assertEquals(ProgressMetric.REPS, ProgressRules.metricFor(machine(MachineKind.BODYWEIGHT_REPS), emptyList()))
    }

    @Test
    fun `a bodyweight machine with one weighted set measures weight`() {
        val sets = listOf(set(1, 1, null, 12), set(2, 1, 10.0, 12))
        assertEquals(ProgressMetric.WEIGHT, ProgressRules.metricFor(machine(MachineKind.BODYWEIGHT_REPS), sets))
    }

    @Test
    fun `a weight and reps machine measures weight even without weights`() {
        val sets = listOf(set(1, 1, null, 12))
        assertEquals(ProgressMetric.WEIGHT, ProgressRules.metricFor(machine(MachineKind.WEIGHT_REPS), sets))
    }

    // points

    @Test
    fun `a session gives one point with its top weight`() {
        val sets = listOf(
            set(1, 1, 100.0, 8, position = 0),
            set(2, 1, 120.0, 5, position = 1),
            set(3, 1, 110.0, 10, position = 2),
        )
        val points = ProgressRules.points(sets, mapOf(1L to day(11, 2)), ProgressMetric.WEIGHT)

        assertEquals(1, points.size)
        val p = points.single()
        assertEquals(1L, p.sessionId)
        assertEquals(day(11, 2), p.date)
        assertEquals(120.0, p.value, 0.0)
        assertEquals(120.0, p.topSet.weightKg!!, 0.0)
        assertEquals(5, p.topSet.reps)
        assertEquals(3, p.setCount)
    }

    @Test
    fun `weight ties go to the set with more reps`() {
        val sets = listOf(set(1, 1, 120.0, 5, position = 0), set(2, 1, 120.0, 8, position = 1))
        val p = ProgressRules.points(sets, mapOf(1L to day(11, 2)), ProgressMetric.WEIGHT).single()

        assertEquals(120.0, p.value, 0.0)
        assertEquals(8, p.topSet.reps)
    }

    @Test
    fun `weight and reps ties go to the later position`() {
        val sets = listOf(set(1, 1, 120.0, 8, position = 0), set(2, 1, 120.0, 8, position = 1))
        val p = ProgressRules.points(sets, mapOf(1L to day(11, 2)), ProgressMetric.WEIGHT).single()

        assertEquals(1, p.topSet.position)
    }

    @Test
    fun `a null weight counts as zero in a weight point`() {
        val sets = listOf(
            set(1, 1, null, 12, position = 0),
            set(2, 1, 20.0, 3, position = 1),
            set(3, 2, null, 12, position = 0),
        )
        val points = ProgressRules.points(sets, mapOf(1L to day(10, 20), 2L to day(10, 27)), ProgressMetric.WEIGHT)

        assertEquals(20.0, points[0].value, 0.0)
        assertEquals(0.0, points[1].value, 0.0)
        assertEquals(12, points[1].topSet.reps)
    }

    @Test
    fun `reps metric takes the most reps`() {
        val sets = listOf(
            set(1, 1, 100.0, 8, position = 0),
            set(2, 1, 60.0, 12, position = 1),
            set(3, 1, 40.0, 10, position = 2),
        )
        val p = ProgressRules.points(sets, mapOf(1L to day(11, 2)), ProgressMetric.REPS).single()

        assertEquals(12.0, p.value, 0.0)
        assertEquals(60.0, p.topSet.weightKg!!, 0.0)
    }

    @Test
    fun `reps ties go to the heavier set`() {
        val sets = listOf(set(1, 1, 60.0, 12, position = 0), set(2, 1, 80.0, 12, position = 1))
        val p = ProgressRules.points(sets, mapOf(1L to day(11, 2)), ProgressMetric.REPS).single()

        assertEquals(12.0, p.value, 0.0)
        assertEquals(80.0, p.topSet.weightKg!!, 0.0)
    }

    @Test
    fun `reps ties at the same weight go to the later position`() {
        val sets = listOf(set(1, 1, 80.0, 12, position = 0), set(2, 1, 80.0, 12, position = 2))
        val p = ProgressRules.points(sets, mapOf(1L to day(11, 2)), ProgressMetric.REPS).single()

        assertEquals(2, p.topSet.position)
    }

    @Test
    fun `a null weight counts as zero in a reps tie`() {
        val sets = listOf(set(1, 1, null, 10, position = 0), set(2, 1, 5.0, 10, position = 1))
        val p = ProgressRules.points(sets, mapOf(1L to day(11, 2)), ProgressMetric.REPS).single()

        assertEquals(5.0, p.topSet.weightKg!!, 0.0)
    }

    @Test
    fun `points are ascending by date then session id`() {
        val sets = listOf(
            set(1, 5, 100.0, 8),
            set(2, 3, 100.0, 8),
            set(3, 9, 100.0, 8),
        )
        val dates = mapOf(5L to day(10, 10), 3L to day(10, 10), 9L to day(10, 1))
        val points = ProgressRules.points(sets, dates, ProgressMetric.WEIGHT)

        assertEquals(listOf(9L, 3L, 5L), points.map { it.sessionId })
    }

    @Test
    fun `two sessions on the same day are both kept`() {
        val sets = listOf(set(1, 1, 100.0, 8), set(2, 2, 90.0, 8))
        val dates = mapOf(1L to day(11, 2), 2L to day(11, 2))
        val points = ProgressRules.points(sets, dates, ProgressMetric.WEIGHT)

        assertEquals(listOf(1L, 2L), points.map { it.sessionId })
        assertEquals(listOf(day(11, 2), day(11, 2)), points.map { it.date })
    }

    @Test
    fun `sets of a session without a date are ignored`() {
        val sets = listOf(set(1, 1, 100.0, 8), set(2, 99, 300.0, 1))
        val points = ProgressRules.points(sets, mapOf(1L to day(11, 2)), ProgressMetric.WEIGHT)

        assertEquals(1, points.size)
        assertEquals(100.0, points.single().value, 0.0)
    }

    @Test
    fun `a session with no sets of the machine gives no point`() {
        val sets = listOf(set(1, 1, 100.0, 8))
        val dates = mapOf(1L to day(11, 2), 2L to day(11, 1))
        val points = ProgressRules.points(sets, dates, ProgressMetric.WEIGHT)

        assertEquals(listOf(1L), points.map { it.sessionId })
    }

    @Test
    fun `no sets gives no points`() {
        assertTrue(ProgressRules.points(emptyList(), mapOf(1L to day(11, 2)), ProgressMetric.WEIGHT).isEmpty())
    }

    // inRange

    private val history = listOf(
        point(day(8, 1), 100.0),
        point(day(8, 2), 101.0),
        point(day(10, 1), 110.0),
        point(day(11, 2), 120.0),
    )

    @Test
    fun `three months includes the day exactly three calendar months back`() {
        val kept = ProgressRules.inRange(history, ProgressRange.THREE_MONTHS, today)

        assertEquals(listOf(day(8, 2), day(10, 1), day(11, 2)), kept.map { it.date })
    }

    @Test
    fun `three months leaves out the day before that`() {
        val kept = ProgressRules.inRange(listOf(point(day(8, 1), 100.0)), ProgressRange.THREE_MONTHS, today)

        assertTrue(kept.isEmpty())
    }

    @Test
    fun `three months from a month end counts calendar months`() {
        val end = LocalDate.of(2026, 11, 30)
        val points = listOf(point(LocalDate.of(2026, 8, 29), 1.0), point(LocalDate.of(2026, 8, 30), 2.0))
        val kept = ProgressRules.inRange(points, ProgressRange.THREE_MONTHS, end)

        assertEquals(listOf(LocalDate.of(2026, 8, 30)), kept.map { it.date })
    }

    @Test
    fun `one year includes the day exactly a year back and leaves out the day before`() {
        val points = listOf(point(day(11, 1).minusYears(1).minusDays(1), 1.0), point(day(11, 1).minusYears(1), 2.0))
        val kept = ProgressRules.inRange(points, ProgressRange.ONE_YEAR, day(11, 1))

        assertEquals(listOf(day(11, 1).minusYears(1)), kept.map { it.date })
    }

    @Test
    fun `all keeps everything in the same order`() {
        assertEquals(history, ProgressRules.inRange(history, ProgressRange.ALL, today))
    }

    // best

    @Test
    fun `best of nothing is null`() {
        assertNull(ProgressRules.best(emptyList()))
    }

    @Test
    fun `best is the highest value`() {
        val best = ProgressRules.best(history)

        assertEquals(120.0, best!!.value, 0.0)
        assertEquals(day(11, 2), best.date)
    }

    @Test
    fun `best ties go to the most recent point`() {
        val points = listOf(point(day(9, 1), 125.0, sessionId = 1), point(day(10, 1), 125.0, sessionId = 2))

        assertEquals(day(10, 1), ProgressRules.best(points)!!.date)
    }

    // change

    @Test
    fun `change needs at least two points`() {
        assertNull(ProgressRules.change(emptyList()))
        assertNull(ProgressRules.change(listOf(point(day(11, 2), 100.0))))
    }

    @Test
    fun `change is the last value minus the first`() {
        val points = listOf(point(day(9, 1), 100.0), point(day(10, 1), 120.0), point(day(11, 1), 110.0))

        assertEquals(10.0, ProgressRules.change(points)!!, 0.0)
    }

    @Test
    fun `change can be negative`() {
        val points = listOf(point(day(9, 1), 120.0), point(day(11, 1), 100.0))

        assertEquals(-20.0, ProgressRules.change(points)!!, 0.0)
    }

    // improvement

    @Test
    fun `a higher recent best than the earlier best is an improvement`() {
        val points = listOf(
            point(day(9, 1), 115.0, sessionId = 1),
            point(day(10, 20), 125.0, sessionId = 2),
        )
        val improvement = ProgressRules.improvement(points, today)!!

        assertEquals(115.0, improvement.from, 0.0)
        assertEquals(125.0, improvement.to, 0.0)
        assertEquals(10.0, improvement.delta, 0.0)
        assertEquals(10.0 / 115.0 * 100, improvement.percent, 1e-9)
    }

    @Test
    fun `no earlier points gives no improvement`() {
        val points = listOf(point(day(10, 20), 125.0))

        assertNull(ProgressRules.improvement(points, today))
    }

    @Test
    fun `no recent points gives no improvement`() {
        val points = listOf(point(day(9, 1), 115.0), point(day(9, 20), 125.0))

        assertNull(ProgressRules.improvement(points, today))
    }

    @Test
    fun `an equal recent best gives no improvement`() {
        val points = listOf(point(day(9, 1), 115.0), point(day(10, 20), 115.0))

        assertNull(ProgressRules.improvement(points, today))
    }

    @Test
    fun `a lower recent best gives no improvement`() {
        val points = listOf(point(day(9, 1), 115.0), point(day(10, 20), 105.0))

        assertNull(ProgressRules.improvement(points, today))
    }

    @Test
    fun `the day thirty days back counts as earlier`() {
        // today - 30 days is 3 October 2026.
        val boundary = today.minusDays(30)
        assertEquals(LocalDate.of(2026, 10, 3), boundary)
        val points = listOf(point(boundary, 110.0, sessionId = 1), point(boundary.plusDays(1), 120.0, sessionId = 2))

        val improvement = ProgressRules.improvement(points, today)!!

        assertEquals(110.0, improvement.from, 0.0)
        assertEquals(120.0, improvement.to, 0.0)
    }

    @Test
    fun `improvement percent is zero from a zero start`() {
        val improvement = Improvement(from = 0.0, to = 10.0)

        assertEquals(10.0, improvement.delta, 0.0)
        assertEquals(0.0, improvement.percent, 0.0)
    }

    // recordsInMonth

    private val november = YearMonth.of(2026, 11)

    @Test
    fun `the first ever set on a machine is not a record`() {
        val sets = listOf(set(1, 1, 100.0, 8))

        assertEquals(0, ProgressRules.recordsInMonth(sets, mapOf(1L to day(11, 2)), november))
    }

    @Test
    fun `a heavier set is a record`() {
        val sets = listOf(set(1, 1, 100.0, 8), set(2, 2, 110.0, 8))
        val dates = mapOf(1L to day(11, 1), 2L to day(11, 2))

        assertEquals(1, ProgressRules.recordsInMonth(sets, dates, november))
    }

    @Test
    fun `more reps at the same weight is a record`() {
        val sets = listOf(set(1, 1, 100.0, 8), set(2, 2, 100.0, 10))
        val dates = mapOf(1L to day(11, 1), 2L to day(11, 2))

        assertEquals(1, ProgressRules.recordsInMonth(sets, dates, november))
    }

    @Test
    fun `only records whose session is in the month are counted`() {
        // October's second set is a record in October, so it does not count for November.
        val sets = listOf(
            set(1, 1, 100.0, 8),
            set(2, 2, 105.0, 8),
            set(3, 3, 105.0, 8),
        )
        val dates = mapOf(1L to day(10, 5), 2L to day(10, 20), 3L to day(11, 2))

        assertEquals(0, ProgressRules.recordsInMonth(sets, dates, november))
    }

    @Test
    fun `earlier sets from before the month count as history`() {
        val sets = listOf(
            set(1, 1, 100.0, 8),
            set(2, 2, 110.0, 8),
        )
        val dates = mapOf(1L to day(10, 5), 2L to day(11, 2))

        assertEquals(1, ProgressRules.recordsInMonth(sets, dates, november))
    }

    @Test
    fun `sets whose session has no date are not counted but still count as history`() {
        val sets = listOf(
            set(1, 1, 100.0, 8),
            set(2, 99, 150.0, 8),
            set(3, 1, 120.0, 8),
        )
        val dates = mapOf(1L to day(11, 2))

        assertEquals(0, ProgressRules.recordsInMonth(sets, dates, november))
    }

    @Test
    fun `records are replayed in logged order, not list order`() {
        val sets = listOf(
            set(2, 2, 110.0, 8, at = t0.plusSeconds(600)),
            set(1, 1, 100.0, 8, at = t0.plusSeconds(60)),
        )
        val dates = mapOf(1L to day(11, 1), 2L to day(11, 2))

        assertEquals(1, ProgressRules.recordsInMonth(sets, dates, november))
    }

    @Test
    fun `equal logged times are replayed by position`() {
        val at = t0.plusSeconds(60)
        val sets = listOf(
            set(2, 1, 110.0, 8, position = 1, at = at),
            set(1, 1, 100.0, 8, position = 0, at = at),
        )

        assertEquals(1, ProgressRules.recordsInMonth(sets, mapOf(1L to day(11, 2)), november))
    }

    @Test
    fun `machines are replayed separately`() {
        val sets = listOf(
            set(1, 1, 100.0, 8, machineId = 1),
            set(2, 2, 50.0, 8, machineId = 2),
        )
        val dates = mapOf(1L to day(10, 5), 2L to day(11, 2))

        assertEquals(0, ProgressRules.recordsInMonth(sets, dates, november))
    }

    // trainingDaysInMonth

    @Test
    fun `training days are the distinct dates in the month`() {
        val dates = listOf(day(11, 2), day(11, 2), day(11, 5), day(10, 31), day(12, 1))

        assertEquals(2, ProgressRules.trainingDaysInMonth(dates, november))
    }

    @Test
    fun `training days include the first and last day of the month`() {
        val dates = listOf(day(11, 1), day(11, 30), day(10, 31), day(12, 1))

        assertEquals(2, ProgressRules.trainingDaysInMonth(dates, november))
    }

    @Test
    fun `no training days in the month gives zero`() {
        assertEquals(0, ProgressRules.trainingDaysInMonth(emptyList(), november))
    }
}

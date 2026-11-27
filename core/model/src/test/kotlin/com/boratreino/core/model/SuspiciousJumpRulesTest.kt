package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Suspicious load jumps, from the C6 contract (section 1.1 and the coordinator amendment in section 0). */
class SuspiciousJumpRulesTest {
    private val day0 = LocalDate.of(2026, 11, 1)
    private val wide = day0.minusYears(1) to day0.plusYears(1)

    private fun machine(kind: MachineKind = MachineKind.WEIGHT_REPS, step: Double = 2.5) =
        Machine(1, "Biceps curl", MuscleGroup.ARMS, kind, step, null, null, false)

    /** A session [dayOffset] days after day0 whose top working set weighs [kg]. */
    private fun point(dayOffset: Long, kg: Double, id: Long = 100L + dayOffset): SessionPoint {
        val set = WorkoutSet(
            id = id,
            sessionId = id,
            machineId = 1,
            position = 0,
            weightKg = kg,
            reps = 10,
            seconds = null,
            calories = null,
            distanceM = null,
            loggedAt = Instant.EPOCH,
        )
        return SessionPoint(id, day0.plusDays(dayOffset), kg, set, 1)
    }

    private fun find(
        points: List<SessionPoint>,
        machine: Machine = machine(),
        from: LocalDate = wide.first,
        to: LocalDate = wide.second,
    ): SuspiciousJump? = SuspiciousJumpRules.find(machine, points, from, to)

    private fun progress(
        kind: MachineKind,
        metric: ProgressMetric,
        points: List<SessionPoint> = emptyList(),
        measurePoints: Map<ProgressMetric, List<SessionPoint>> = emptyMap(),
    ) = MachineProgress(machine(kind), metric, points, measurePoints = measurePoints)

    // weightSeries

    @Test
    fun `weightSeries returns the points of a weight machine`() {
        val points = listOf(point(0, 30.0), point(5, 32.5))
        assertEquals(points, SuspiciousJumpRules.weightSeries(progress(MachineKind.WEIGHT_REPS, ProgressMetric.WEIGHT, points)))
    }

    @Test
    fun `weightSeries of a weight and time machine charting weight uses its points`() {
        val weights = listOf(point(0, 40.0), point(3, 42.5))
        val result = SuspiciousJumpRules.weightSeries(
            progress(MachineKind.WEIGHT_TIME, ProgressMetric.WEIGHT, weights, mapOf(ProgressMetric.WEIGHT to weights)),
        )
        assertEquals(weights, result)
    }

    @Test
    fun `weightSeries of a weight and time machine charting seconds uses the weight measure points`() {
        val weights = listOf(point(0, 40.0), point(3, 42.5))
        val seconds = listOf(point(0, 60.0), point(3, 90.0))
        val result = SuspiciousJumpRules.weightSeries(
            progress(
                MachineKind.WEIGHT_TIME,
                ProgressMetric.SECONDS,
                seconds,
                mapOf(ProgressMetric.WEIGHT to weights, ProgressMetric.SECONDS to seconds),
            ),
        )
        assertEquals(weights, result)
    }

    @Test
    fun `weightSeries of a time machine is empty`() {
        val seconds = listOf(point(0, 0.0), point(3, 0.0))
        assertTrue(SuspiciousJumpRules.weightSeries(progress(MachineKind.TIME, ProgressMetric.SECONDS, seconds)).isEmpty())
    }

    @Test
    fun `weightSeries of a cardio machine is empty`() {
        val speed = listOf(point(0, 0.0), point(3, 0.0))
        assertTrue(SuspiciousJumpRules.weightSeries(progress(MachineKind.CARDIO, ProgressMetric.SPEED, speed)).isEmpty())
    }

    // find: the threshold

    @Test
    fun `a rise of exactly forty percent in five days is flagged`() {
        val jump = find(listOf(point(0, 30.0), point(5, 42.0)))
        assertEquals(30.0, jump!!.fromKg, 0.0)
        assertEquals(42.0, jump.toKg, 0.0)
        assertEquals(5, jump.days)
    }

    @Test
    fun `a rise just under forty percent is not flagged`() {
        assertNull(find(listOf(point(0, 30.0), point(5, 41.5))))
    }

    @Test
    fun `the percent of a flagged jump is its rise over the starting weight`() {
        val jump = find(listOf(point(0, 30.0), point(5, 42.0)))!!
        assertEquals(40.0, jump.percent, 1e-9)
    }

    // find: the day limit

    @Test
    fun `a jump fourteen days apart is flagged`() {
        val jump = find(listOf(point(0, 30.0), point(14, 45.0)))
        assertEquals(14, jump!!.days)
    }

    @Test
    fun `a jump fifteen days apart is not flagged`() {
        assertNull(find(listOf(point(0, 30.0), point(15, 45.0))))
    }

    // find: the minimum rise

    @Test
    fun `a rise of two and a half kilograms is not flagged even when the ratio is met`() {
        assertNull(find(listOf(point(0, 2.5), point(3, 5.0)), machine(step = 2.5)))
    }

    @Test
    fun `a rise of exactly twice the step is flagged`() {
        val jump = find(listOf(point(0, 20.0), point(3, 30.0)), machine(step = 5.0))
        assertEquals(20.0, jump!!.fromKg, 0.0)
        assertEquals(30.0, jump.toKg, 0.0)
    }

    @Test
    fun `a rise just under twice the step is not flagged`() {
        assertNull(find(listOf(point(0, 20.0), point(3, 29.0)), machine(step = 5.0)))
    }

    @Test
    fun `a rise of exactly five kilograms is flagged when twice the step is smaller`() {
        val jump = find(listOf(point(0, 10.0), point(3, 15.0)), machine(step = 1.0))
        assertEquals(15.0, jump!!.toKg, 0.0)
    }

    // find: the owner's case and direction

    @Test
    fun `the biceps curl going from thirty to forty five kilograms in five days is flagged`() {
        val m = machine()
        val jump = find(listOf(point(0, 30.0), point(5, 45.0)), m)!!
        assertEquals(m, jump.machine)
        assertEquals(30.0, jump.fromKg, 0.0)
        assertEquals(45.0, jump.toKg, 0.0)
        assertEquals(day0, jump.fromDate)
        assertEquals(day0.plusDays(5), jump.toDate)
        assertEquals(50.0, jump.percent, 1e-9)
    }

    @Test
    fun `a drop is never flagged`() {
        assertNull(find(listOf(point(0, 45.0), point(5, 30.0))))
    }

    @Test
    fun `a drop back after a jump leaves the jump flagged`() {
        val jump = find(listOf(point(0, 30.0), point(5, 45.0), point(6, 30.0)))!!
        assertEquals(30.0, jump.fromKg, 0.0)
        assertEquals(45.0, jump.toKg, 0.0)
        assertEquals(day0.plusDays(5), jump.toDate)
    }

    @Test
    fun `the latest unconfirmed jump is the one returned`() {
        // 30 -> 45 is followed by 25 and 40, neither at 90 % of 45, so it stays unconfirmed; 25 -> 40 is the latest jump.
        val jump = find(listOf(point(0, 30.0), point(3, 45.0), point(4, 25.0), point(10, 40.0)))!!
        assertEquals(25.0, jump.fromKg, 0.0)
        assertEquals(40.0, jump.toKg, 0.0)
        assertEquals(day0.plusDays(4), jump.fromDate)
        assertEquals(day0.plusDays(10), jump.toDate)
    }

    // find: the period

    @Test
    fun `a jump is in the period when its later session is inside from and to`() {
        val points = listOf(point(0, 30.0), point(5, 45.0))
        assertTrue(find(points, from = day0.plusDays(5), to = day0.plusDays(10)) != null)
        assertTrue(find(points, from = day0.plusDays(3), to = day0.plusDays(5)) != null)
        assertTrue(find(points, from = day0.plusDays(0), to = day0.plusDays(5)) != null)
    }

    @Test
    fun `a jump whose later session is before the period is not flagged`() {
        assertNull(find(listOf(point(0, 30.0), point(5, 45.0)), from = day0.plusDays(6), to = day0.plusDays(10)))
    }

    @Test
    fun `a jump whose later session is after the period is not flagged`() {
        assertNull(find(listOf(point(0, 30.0), point(5, 45.0)), from = day0.plusDays(0), to = day0.plusDays(4)))
    }

    // find: confirmation (coordinator amendment)

    @Test
    fun `a later session at ninety percent of the jumped weight clears the jump`() {
        assertNull(find(listOf(point(0, 30.0), point(5, 45.0), point(20, 40.5))))
    }

    @Test
    fun `a later session just under ninety percent of the jumped weight does not clear the jump`() {
        val jump = find(listOf(point(0, 30.0), point(5, 45.0), point(20, 40.4)))
        assertEquals(45.0, jump!!.toKg, 0.0)
    }

    @Test
    fun `a later session at the jumped weight clears the jump`() {
        assertNull(find(listOf(point(0, 30.0), point(5, 45.0), point(7, 45.0))))
    }

    @Test
    fun `an earlier session at the jumped weight does not clear the jump`() {
        val jump = find(listOf(point(-10, 45.0), point(0, 30.0), point(5, 45.0)))
        assertEquals(30.0, jump!!.fromKg, 0.0)
        assertEquals(45.0, jump.toKg, 0.0)
    }

    // find: unloaded points and cardio

    @Test
    fun `a jump that starts from an unloaded session is not flagged`() {
        assertNull(find(listOf(point(0, 0.0), point(5, 45.0))))
    }

    @Test
    fun `a cardio machine is never flagged`() {
        assertNull(find(listOf(point(0, 30.0), point(5, 45.0)), machine(kind = MachineKind.CARDIO)))
    }

    @Test
    fun `no points give no jump`() {
        assertNull(find(emptyList()))
    }
}

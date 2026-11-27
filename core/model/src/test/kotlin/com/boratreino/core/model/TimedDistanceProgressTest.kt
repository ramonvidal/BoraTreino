package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class TimedDistanceProgressTest {
    private val at = Instant.parse("2026-11-20T09:00:00Z")
    private val day1 = LocalDate.of(2026, 11, 2)
    private val day2 = LocalDate.of(2026, 11, 9)
    private val today = LocalDate.of(2026, 11, 20)
    private val dates = mapOf(1L to day1, 2L to day2)

    private fun machine(id: Long, kind: MachineKind) =
        Machine(id, "Machine $id", MuscleGroup.ARMS, kind, 2.5, null, null, false)

    private fun set(
        id: Long,
        sessionId: Long,
        position: Int,
        weightKg: Double?,
        reps: Int? = null,
        seconds: Int? = null,
        distanceM: Int? = null,
        machineId: Long = 5L,
        type: SetType = SetType.NORMAL,
    ) = WorkoutSet(
        id = id, sessionId = sessionId, machineId = machineId, position = position,
        weightKg = weightKg, reps = reps, seconds = seconds, calories = null, distanceM = distanceM,
        loggedAt = at, type = type,
    )

    // metrics per kind

    @Test
    fun `weight and time charts weight and seconds, weight and distance charts weight and metres`() {
        assertEquals(
            listOf(ProgressMetric.WEIGHT, ProgressMetric.SECONDS),
            ProgressRules.strengthMetrics(MachineKind.WEIGHT_TIME),
        )
        assertEquals(
            listOf(ProgressMetric.WEIGHT, ProgressMetric.METRES),
            ProgressRules.strengthMetrics(MachineKind.WEIGHT_DISTANCE),
        )
    }

    @Test
    fun `time only charts seconds, and the reps kinds and cardio chart no measure of their own`() {
        assertEquals(listOf(ProgressMetric.SECONDS), ProgressRules.strengthMetrics(MachineKind.TIME))
        assertTrue(ProgressRules.strengthMetrics(MachineKind.WEIGHT_REPS).isEmpty())
        assertTrue(ProgressRules.strengthMetrics(MachineKind.BODYWEIGHT_REPS).isEmpty())
        assertTrue(ProgressRules.strengthMetrics(MachineKind.CARDIO).isEmpty())
    }

    @Test
    fun `a weight and time machine with no load charts seconds by default`() {
        val sets = listOf(set(1, 1, 0, 0.0, seconds = 30), set(2, 1, 1, null, seconds = 45))

        assertEquals(ProgressMetric.SECONDS, ProgressRules.metricFor(machine(5, MachineKind.WEIGHT_TIME), sets))
    }

    @Test
    fun `a weight and time machine with a load charts weight by default`() {
        val sets = listOf(set(1, 1, 0, 0.0, seconds = 30), set(2, 1, 1, 40.0, seconds = 45))

        assertEquals(ProgressMetric.WEIGHT, ProgressRules.metricFor(machine(5, MachineKind.WEIGHT_TIME), sets))
    }

    @Test
    fun `a weight and distance machine with no load charts metres by default and with a load charts weight`() {
        val noLoad = listOf(set(1, 1, 0, 0.0, distanceM = 20))
        val loaded = listOf(set(1, 1, 0, 40.0, distanceM = 20))

        assertEquals(ProgressMetric.METRES, ProgressRules.metricFor(machine(5, MachineKind.WEIGHT_DISTANCE), noLoad))
        assertEquals(ProgressMetric.WEIGHT, ProgressRules.metricFor(machine(5, MachineKind.WEIGHT_DISTANCE), loaded))
    }

    @Test
    fun `a time only machine charts seconds`() {
        val sets = listOf(set(1, 1, 0, 0.0, seconds = 30))

        assertEquals(ProgressMetric.SECONDS, ProgressRules.metricFor(machine(5, MachineKind.TIME), sets))
    }

    @Test
    fun `a bodyweight machine with no load still charts reps`() {
        val sets = listOf(set(1, 1, 0, 0.0, reps = 12))

        assertEquals(ProgressMetric.REPS, ProgressRules.metricFor(machine(5, MachineKind.BODYWEIGHT_REPS), sets))
    }

    // points

    @Test
    fun `seconds points are the longest hold of each session`() {
        val sets = listOf(
            set(1, 1, 0, 40.0, seconds = 30),
            set(2, 1, 1, 40.0, seconds = 45),
            set(3, 2, 0, 20.0, seconds = 60),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.SECONDS)

        assertEquals(listOf(45.0, 60.0), points.map { it.value })
        assertEquals(listOf(day1, day2), points.map { it.date })
        assertEquals(45, points[0].topSet.seconds)
    }

    @Test
    fun `a seconds tie goes to the heavier set, then the later one`() {
        val heavierWins = listOf(set(1, 1, 0, 20.0, seconds = 30), set(2, 1, 1, 40.0, seconds = 30))
        val laterWins = listOf(set(3, 1, 0, 40.0, seconds = 30), set(4, 1, 1, 40.0, seconds = 30))

        assertEquals(40.0, ProgressRules.points(heavierWins, dates, ProgressMetric.SECONDS).single().topSet.weightKg!!, 0.0)
        assertEquals(1, ProgressRules.points(laterWins, dates, ProgressMetric.SECONDS).single().topSet.position)
    }

    @Test
    fun `metres points are the farthest carry of each session`() {
        val sets = listOf(
            set(1, 1, 0, 40.0, distanceM = 20),
            set(2, 1, 1, 40.0, distanceM = 25),
            set(3, 2, 0, 0.0, distanceM = 30),
        )

        val points = ProgressRules.points(sets, dates, ProgressMetric.METRES)

        assertEquals(listOf(25.0, 30.0), points.map { it.value })
    }

    @Test
    fun `the weight points of a weight and time machine take the weight and break ties by the longer hold`() {
        val sets = listOf(set(1, 1, 0, 40.0, seconds = 30), set(2, 1, 1, 40.0, seconds = 45))

        val point = ProgressRules.points(sets, dates, ProgressMetric.WEIGHT).single()

        assertEquals(40.0, point.value, 0.0)
        assertEquals(45, point.topSet.seconds)
    }

    @Test
    fun `warm-up timed sets are left out of the points`() {
        val sets = listOf(
            set(1, 1, 0, 40.0, seconds = 30),
            set(2, 1, 1, 40.0, seconds = 300, type = SetType.WARMUP),
        )

        assertEquals(30.0, ProgressRules.points(sets, dates, ProgressMetric.SECONDS).single().value, 0.0)
    }

    // machine progress

    @Test
    fun `a weight and time machine with a load charts weight and seconds, and has no one rep max`() {
        val machine = machine(5, MachineKind.WEIGHT_TIME)
        val sets = listOf(set(1, 1, 0, 40.0, seconds = 30), set(2, 2, 0, 42.5, seconds = 45))

        val progress = ProgressRules.machineProgress(machine, sets, dates, today, DayOfWeek.MONDAY)

        assertEquals(ProgressMetric.WEIGHT, progress.metric)
        assertEquals(setOf(ProgressMetric.WEIGHT, ProgressMetric.SECONDS), progress.measurePoints.keys)
        assertEquals(listOf(30.0, 45.0), progress.measurePoints.getValue(ProgressMetric.SECONDS).map { it.value })
        assertEquals(listOf(40.0, 42.5), progress.measurePoints.getValue(ProgressMetric.WEIGHT).map { it.value })
        assertTrue(progress.oneRmPoints.isEmpty())
    }

    @Test
    fun `a weight and distance machine charts weight and metres`() {
        val machine = machine(5, MachineKind.WEIGHT_DISTANCE)
        val sets = listOf(set(1, 1, 0, 40.0, distanceM = 20), set(2, 2, 0, 40.0, distanceM = 25))

        val progress = ProgressRules.machineProgress(machine, sets, dates, today, DayOfWeek.MONDAY)

        assertEquals(setOf(ProgressMetric.WEIGHT, ProgressMetric.METRES), progress.measurePoints.keys)
        assertEquals(listOf(20.0, 25.0), progress.measurePoints.getValue(ProgressMetric.METRES).map { it.value })
        assertTrue(progress.oneRmPoints.isEmpty())
    }

    @Test
    fun `a time only machine charts its seconds as the main points and has no second chart`() {
        val machine = machine(5, MachineKind.TIME)
        val sets = listOf(set(1, 1, 0, 0.0, seconds = 30), set(2, 2, 0, 0.0, seconds = 60))

        val progress = ProgressRules.machineProgress(machine, sets, dates, today, DayOfWeek.MONDAY)

        assertEquals(ProgressMetric.SECONDS, progress.metric)
        assertEquals(listOf(30.0, 60.0), progress.points.map { it.value })
        assertTrue(progress.measurePoints.isEmpty())
        assertTrue(progress.oneRmPoints.isEmpty())
    }

    @Test
    fun `a reps machine has no measure charts`() {
        val machine = machine(5, MachineKind.WEIGHT_REPS)
        val sets = listOf(set(1, 1, 0, 40.0, reps = 10))

        val progress = ProgressRules.machineProgress(machine, sets, dates, today, DayOfWeek.MONDAY)

        assertTrue(progress.measurePoints.isEmpty())
    }

    @Test
    fun `sets of another machine do not reach the progress of this one`() {
        val machine = machine(5, MachineKind.TIME)
        val sets = listOf(set(1, 1, 0, 0.0, seconds = 30), set(2, 1, 0, 0.0, seconds = 999, machineId = 6L))

        val progress = ProgressRules.machineProgress(machine, sets, dates, today, DayOfWeek.MONDAY)

        assertEquals(listOf(30.0), progress.points.map { it.value })
    }
}

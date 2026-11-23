package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRules
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** MachineProgress.cardioPoints from the repository: every cardio metric is filled for a cardio machine (phase 42). */
class DefaultProgressCardioPointsTest {
    // Monday, 2 November 2026, evening (UTC).
    private val monday: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var repo: ProgressRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        repo = DefaultProgressRepository(store, TestClock(monday))
    }

    private suspend fun bike(name: String = "Bike"): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false))

    private suspend fun chestPress(): Long =
        store.saveMachine(Machine(0, "Chest press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun session(startedAt: String): Long {
        val start = Instant.parse(startedAt)
        return store.insertSession(Session(0, start, start.plusSeconds(3600), null, null))
    }

    /** A bike set with the console values; [watts] etc. are null when not logged. */
    private suspend fun bikeSet(
        sessionId: Long,
        machineId: Long,
        seconds: Int? = 1200,
        distanceM: Int? = null,
        intensity: Int? = null,
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
        position: Int = 0,
    ) {
        val loggedAt = store.session(sessionId)!!.startedAt
        store.insertSet(
            WorkoutSet(
                id = 0,
                sessionId = sessionId,
                machineId = machineId,
                position = position,
                weightKg = null,
                reps = null,
                seconds = seconds,
                calories = 180,
                distanceM = distanceM,
                loggedAt = loggedAt,
                intensity = intensity,
                watts = watts,
                heartRate = heartRate,
                wattsPerKg = wattsPerKg,
                tss = tss,
                intensityFactor = intensityFactor,
            ),
        )
    }

    private fun values(points: List<SessionPoint>): List<Double> = points.map { it.value }

    private fun assertSingle(expected: Double, points: List<SessionPoint>) {
        assertEquals(1, points.size)
        assertEquals(expected, points.single().value, 1e-9)
    }

    // Every cardio metric

    @Test
    fun `a cardio machine has an entry for every cardio metric`() = runTest {
        val machine = bike()
        val s = session("2026-10-27T10:00:00Z")
        bikeSet(s, machine, distanceM = 5000, intensity = 12, watts = 160, heartRate = 142, wattsPerKg = 2.4, tss = 48, intensityFactor = 0.79)

        val progress = requireNotNull(repo.observeMachineProgress(machine).first())

        for (metric in ProgressRules.CARDIO_METRICS) {
            assertTrue("missing $metric", progress.cardioPoints.containsKey(metric))
        }
        assertEquals(ProgressRules.CARDIO_METRICS.toSet(), progress.cardioPoints.keys)
    }

    @Test
    fun `each cardio metric carries the value the console logged`() = runTest {
        val machine = bike()
        val s = session("2026-10-27T10:00:00Z")
        bikeSet(s, machine, distanceM = 5000, intensity = 12, watts = 160, heartRate = 142, wattsPerKg = 2.4, tss = 48, intensityFactor = 0.79)

        val points = requireNotNull(repo.observeMachineProgress(machine).first()).cardioPoints

        assertSingle(15.0, points.getValue(ProgressMetric.SPEED))
        assertSingle(160.0, points.getValue(ProgressMetric.POWER))
        assertSingle(0.79, points.getValue(ProgressMetric.INTENSITY_FACTOR))
        assertSingle(2.4, points.getValue(ProgressMetric.WATTS_PER_KG))
        assertSingle(48.0, points.getValue(ProgressMetric.TSS))
        assertSingle(142.0, points.getValue(ProgressMetric.HEART_RATE))
        assertSingle(12.0, points.getValue(ProgressMetric.INTENSITY))
        assertSingle(5.0, points.getValue(ProgressMetric.DISTANCE))
        assertSingle(20.0, points.getValue(ProgressMetric.MINUTES))
    }

    @Test
    fun `a metric with no data for the machine maps to an empty list`() = runTest {
        val machine = bike()
        val s = session("2026-10-27T10:00:00Z")
        bikeSet(s, machine, seconds = 1200)

        val points = requireNotNull(repo.observeMachineProgress(machine).first()).cardioPoints

        for (metric in listOf(
            ProgressMetric.SPEED,
            ProgressMetric.POWER,
            ProgressMetric.INTENSITY_FACTOR,
            ProgressMetric.WATTS_PER_KG,
            ProgressMetric.TSS,
            ProgressMetric.HEART_RATE,
            ProgressMetric.INTENSITY,
            ProgressMetric.DISTANCE,
        )) {
            assertTrue("$metric should be empty", points.getValue(metric).isEmpty())
        }
        assertEquals(listOf(20.0), values(points.getValue(ProgressMetric.MINUTES)))
    }

    @Test
    fun `the default points are the cardio points of the metric chosen for the machine`() = runTest {
        val machine = bike()
        val s = session("2026-10-27T10:00:00Z")
        bikeSet(s, machine, watts = 160, intensityFactor = 0.79)

        val progress = requireNotNull(repo.observeMachineProgress(machine).first())

        assertEquals(ProgressMetric.POWER, progress.metric)
        assertEquals(progress.points, progress.cardioPoints.getValue(ProgressMetric.POWER))
    }

    @Test
    fun `each metric has one point per session that has its data, oldest first`() = runTest {
        val machine = bike()
        val october = session("2026-10-20T10:00:00Z")
        val november = session("2026-10-27T10:00:00Z")
        val noPower = session("2026-10-13T10:00:00Z")
        bikeSet(october, machine, watts = 150)
        bikeSet(november, machine, watts = 170)
        bikeSet(noPower, machine, seconds = 600)

        val power = requireNotNull(repo.observeMachineProgress(machine).first()).cardioPoints.getValue(ProgressMetric.POWER)

        assertEquals(listOf(october, november), power.map { it.sessionId })
        assertEquals(listOf(150.0, 170.0), values(power))
    }

    // Other machines

    @Test
    fun `a weight machine has no cardio points`() = runTest {
        val press = chestPress()
        val s = session("2026-10-27T10:00:00Z")
        store.insertSet(WorkoutSet(0, s, press, 0, 40.0, 10, null, null, null, store.session(s)!!.startedAt))

        val progress = requireNotNull(repo.observeMachineProgress(press).first())

        assertTrue(progress.cardioPoints.isEmpty())
    }
}

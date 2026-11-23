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

/** MachineProgress.cardioPoints from the repository: every cardio metric is filled for a cardio machine. */
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

    /** A cardio set of 20 minutes and 180 kcal; [distanceM] and [intensity] are null when not logged. */
    private suspend fun bikeSet(
        sessionId: Long,
        machineId: Long,
        seconds: Int? = 1200,
        distanceM: Int? = null,
        intensity: Double? = null,
    ) {
        val loggedAt = store.session(sessionId)!!.startedAt
        store.insertSet(
            WorkoutSet(
                id = 0,
                sessionId = sessionId,
                machineId = machineId,
                position = 0,
                weightKg = null,
                reps = null,
                seconds = seconds,
                calories = 180,
                distanceM = distanceM,
                loggedAt = loggedAt,
                intensity = intensity,
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
    fun `a cardio machine has an entry for every cardio metric and no others`() = runTest {
        val machine = bike()
        val s = session("2026-10-27T10:00:00Z")
        bikeSet(s, machine, distanceM = 5000, intensity = 0.79)

        val progress = requireNotNull(repo.observeMachineProgress(machine).first())

        assertEquals(ProgressRules.CARDIO_METRICS.toSet(), progress.cardioPoints.keys)
        assertEquals(
            setOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.DISTANCE, ProgressMetric.MINUTES),
            progress.cardioPoints.keys,
        )
    }

    @Test
    fun `each cardio metric carries the value the console logged`() = runTest {
        val machine = bike()
        val s = session("2026-10-27T10:00:00Z")
        bikeSet(s, machine, distanceM = 5000, intensity = 0.79)

        val points = requireNotNull(repo.observeMachineProgress(machine).first()).cardioPoints

        assertSingle(15.0, points.getValue(ProgressMetric.SPEED))
        assertSingle(0.79, points.getValue(ProgressMetric.INTENSITY))
        assertSingle(5.0, points.getValue(ProgressMetric.DISTANCE))
        assertSingle(20.0, points.getValue(ProgressMetric.MINUTES))
    }

    @Test
    fun `a metric with no data for the machine maps to an empty list`() = runTest {
        val machine = bike()
        val s = session("2026-10-27T10:00:00Z")
        bikeSet(s, machine, seconds = 1200)

        val points = requireNotNull(repo.observeMachineProgress(machine).first()).cardioPoints

        for (metric in listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.DISTANCE)) {
            assertTrue("$metric should be empty", points.getValue(metric).isEmpty())
        }
        assertEquals(listOf(20.0), values(points.getValue(ProgressMetric.MINUTES)))
    }

    @Test
    fun `the default points are the cardio points of the metric chosen for the machine`() = runTest {
        val machine = bike()
        val s = session("2026-10-27T10:00:00Z")
        bikeSet(s, machine, intensity = 0.79)

        val progress = requireNotNull(repo.observeMachineProgress(machine).first())

        assertEquals(ProgressMetric.INTENSITY, progress.metric)
        assertEquals(progress.points, progress.cardioPoints.getValue(ProgressMetric.INTENSITY))
    }

    @Test
    fun `each metric has one point per session that has its data, oldest first`() = runTest {
        val machine = bike()
        val october = session("2026-10-20T10:00:00Z")
        val november = session("2026-10-27T10:00:00Z")
        val noIntensity = session("2026-10-13T10:00:00Z")
        bikeSet(october, machine, intensity = 0.12)
        bikeSet(november, machine, intensity = 0.17)
        bikeSet(noIntensity, machine, seconds = 600)

        val intensity = requireNotNull(repo.observeMachineProgress(machine).first()).cardioPoints
            .getValue(ProgressMetric.INTENSITY)

        assertEquals(listOf(october, november), intensity.map { it.sessionId })
        assertEquals(0.12, intensity[0].value, 1e-9)
        assertEquals(0.17, intensity[1].value, 1e-9)
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

package com.boratreino.core.data

import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Session
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.TrendVerdict
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The statistics added in phase 46 (trends, waist / hip), as the repository delivers them. Today is 5 November 2026. */
class DefaultStatsTrendsTest {
    private val thursday: Instant = Instant.parse("2026-11-05T12:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var repo: StatsRepository

    private object EmptyLibrary : ExerciseLibraryRepository {
        override suspend fun all(): List<LibraryExercise> = emptyList()

        override suspend fun byId(id: String): LibraryExercise? = null
    }

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        repo = DefaultStatsRepository(store, TestClock(thursday), EmptyLibrary)
    }

    private suspend fun machine(name: String): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    /** One session on [date] (10:00 UTC) with a single one-rep set of [weightKg], so the 1RM equals the weight. */
    private suspend fun lift(machineId: Long, date: LocalDate, weightKg: Double) {
        val startedAt = date.atTime(10, 0).toInstant(ZoneOffset.UTC)
        val sessionId = store.insertSession(Session(0, startedAt, startedAt.plusSeconds(3600), null, null))
        store.insertSet(WorkoutSet(0, sessionId, machineId, 0, weightKg, 1, null, null, null, startedAt))
    }

    @Test
    fun `the machine trends reach the report`() = runTest {
        val bench = machine("Bench press")
        // Weeks 0..4 ending today: 80, 82, 84, 86, 88 kg, a perfect 2 kg per week line.
        listOf(8, 15, 22, 29).zip(listOf(80.0, 82.0, 84.0, 86.0)).forEach { (day, kg) ->
            lift(bench, LocalDate.of(2026, 10, day), kg)
        }
        lift(bench, LocalDate.of(2026, 11, 5), 88.0)

        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        val trend = report.machineTrends.single()
        assertEquals("Bench press", trend.name)
        assertEquals(TrendVerdict.RISING, trend.result.verdict)
        assertEquals(2.0, trend.result.fit!!.slopePerWeek, 1e-9)
    }

    @Test
    fun `the waist to hip ratio reaches the report`() = runTest {
        store.addMeasurement(BodyMeasurement(date = LocalDate.of(2026, 11, 4), type = MeasurementType.WAIST, valueCm = 90.0))
        store.addMeasurement(BodyMeasurement(date = LocalDate.of(2026, 11, 4), type = MeasurementType.HIPS, valueCm = 100.0))
        store.addMeasurement(BodyMeasurement(date = LocalDate.of(2026, 11, 2), type = MeasurementType.WAIST, valueCm = 92.0))

        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertEquals(1, report.waistHip.size)
        assertEquals(LocalDate.of(2026, 11, 4), report.waistHip[0].first)
        assertEquals(0.9, report.waistHip[0].second, 1e-9)
    }

    @Test
    fun `an empty store has no trends, cardio insight or waist to hip`() = runTest {
        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertTrue(report.machineTrends.isEmpty())
        assertTrue(report.waistHip.isEmpty())
        assertNull(report.cardioInsight)
    }
}

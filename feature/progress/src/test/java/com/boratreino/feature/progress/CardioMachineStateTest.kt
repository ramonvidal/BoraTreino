package com.boratreino.feature.progress

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The cardio exercise's detail state: the measure the chart shows, the measures offered, and the records. */
class CardioMachineStateTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val bike = Machine(30L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)
    private val bench = Machine(1L, "Bench", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, archived = false)

    private fun point(value: Double, sessionId: Long, date: LocalDate = LocalDate.of(2026, 10, 20)) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = value,
        topSet = WorkoutSet(sessionId, sessionId, bike.id, 0, null, null, 600, null, null, Instant.parse("2026-10-20T10:00:00Z")),
        setCount = 1,
    )

    /** A bike with speed (best 12.0), intensity (best 14.0) and time, and no distance. */
    private val bikeProgress = MachineProgress(
        machine = bike,
        metric = ProgressMetric.MINUTES,
        points = listOf(point(30.0, 1L), point(45.0, 2L, LocalDate.of(2026, 10, 27))),
        cardioPoints = mapOf(
            ProgressMetric.SPEED to listOf(point(10.0, 1L), point(12.0, 2L, LocalDate.of(2026, 10, 27))),
            ProgressMetric.INTENSITY to listOf(point(14.0, 2L, LocalDate.of(2026, 10, 27))),
            ProgressMetric.DISTANCE to emptyList(),
            ProgressMetric.MINUTES to listOf(point(30.0, 1L), point(45.0, 2L, LocalDate.of(2026, 10, 27))),
        ),
    )

    private fun state(progress: MachineProgress = bikeProgress, cardioMetric: String? = null) =
        buildMachineState(
            progress = progress,
            range = ProgressRange.ALL,
            today = today,
            selected = null,
            showAll = false,
            cardioMetric = cardioMetric,
        )

    @Test
    fun `speed is the chart's measure by default when the bike has speed`() {
        val s = state()

        assertEquals(ProgressMetric.SPEED, s.metric)
        assertEquals(listOf(10.0, 12.0), s.points.map { it.value })
    }

    @Test
    fun `the measures offered are the ones with data, in order`() {
        assertEquals(listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.MINUTES), state().cardioMetrics)
    }

    @Test
    fun `a saved measure with data is the one the chart shows`() {
        val s = state(cardioMetric = "INTENSITY")

        assertEquals(ProgressMetric.INTENSITY, s.metric)
        assertEquals(listOf(14.0), s.points.map { it.value })
    }

    @Test
    fun `an unknown or missing saved measure falls back to the default`() {
        assertEquals(ProgressMetric.SPEED, state(cardioMetric = "NOT_A_METRIC").metric)
        assertEquals(ProgressMetric.SPEED, state(cardioMetric = null).metric)
    }

    @Test
    fun `a saved measure without data falls back to the default`() {
        assertEquals(ProgressMetric.SPEED, state(cardioMetric = "DISTANCE").metric)
    }

    @Test
    fun `the records are the best of each measure offered`() {
        val records = state().cardioRecords

        assertEquals(listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.MINUTES), records.map { it.metric })
        assertEquals(12.0, records.first { it.metric == ProgressMetric.SPEED }.point.value, 1e-9)
        assertEquals(14.0, records.first { it.metric == ProgressMetric.INTENSITY }.point.value, 1e-9)
    }

    @Test
    fun `a weight machine has no cardio measures or records`() {
        val weight = MachineProgress(bench, ProgressMetric.WEIGHT, listOf(point(80.0, 3L)))

        val s = state(weight)

        assertEquals(ProgressMetric.WEIGHT, s.metric)
        assertTrue(s.cardioMetrics.isEmpty())
        assertTrue(s.cardioRecords.isEmpty())
    }
}

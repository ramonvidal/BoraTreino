package com.boratreino.feature.progress

import com.boratreino.core.model.CardioBlock
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Which cardio measures a machine has, the default one, the points behind each, and the 7-day block totals. */
class CardioMetricsTest {
    private val bike = Machine(30L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)
    private val day = LocalDate.of(2026, 10, 20)

    private fun point(value: Double, sessionId: Long = 1L) = SessionPoint(
        sessionId = sessionId,
        date = day,
        value = value,
        topSet = WorkoutSet(1L, sessionId, bike.id, 0, null, null, 600, null, null, Instant.parse("2026-10-20T10:00:00Z")),
        setCount = 1,
    )

    private fun progress(
        metric: ProgressMetric = ProgressMetric.MINUTES,
        points: List<SessionPoint> = emptyList(),
        cardioPoints: Map<ProgressMetric, List<SessionPoint>> = emptyMap(),
    ) = MachineProgress(bike, metric, points, cardioPoints = cardioPoints)

    private fun block(minutes: Int, distanceM: Int, sessions: Int, intensity: Double?, speed: Double?) = CardioBlock(
        weekStart = day,
        minutes = minutes,
        distanceM = distanceM,
        sessions = sessions,
        intensityAvg = intensity,
        speedKmh = speed,
    )

    // The metrics a cardio exercise offers

    @Test
    fun `the cardio measures are speed, intensity, distance and time in that order`() {
        assertEquals(
            listOf(
                ProgressMetric.SPEED,
                ProgressMetric.INTENSITY,
                ProgressMetric.DISTANCE,
                ProgressMetric.MINUTES,
            ),
            CARDIO_METRICS,
        )
    }

    @Test
    fun `only the measures with data are available, and distance needs a value above zero`() {
        val p = progress(
            cardioPoints = mapOf(
                ProgressMetric.SPEED to listOf(point(10.0)),
                ProgressMetric.INTENSITY to emptyList(),
                ProgressMetric.DISTANCE to listOf(point(0.0)),
                ProgressMetric.MINUTES to listOf(point(30.0)),
            ),
        )

        assertEquals(listOf(ProgressMetric.SPEED, ProgressMetric.MINUTES), availableCardioMetrics(p))
    }

    @Test
    fun `a machine with no cardio points has no measures`() {
        assertTrue(availableCardioMetrics(progress()).isEmpty())
    }

    // pointsFor

    @Test
    fun `points for a measure are its own cardio points`() {
        val speed = listOf(point(10.0), point(12.0, sessionId = 2L))
        val p = progress(cardioPoints = mapOf(ProgressMetric.SPEED to speed))

        assertEquals(speed, p.pointsFor(ProgressMetric.SPEED))
    }

    @Test
    fun `points for the machine's own measure fall back to its main points`() {
        val minutes = listOf(point(30.0))
        val p = progress(metric = ProgressMetric.MINUTES, points = minutes)

        assertEquals(minutes, p.pointsFor(ProgressMetric.MINUTES))
        assertTrue(p.pointsFor(ProgressMetric.SPEED).isEmpty())
    }

    // The default measure

    @Test
    fun `speed is the default when there is speed`() {
        val available = listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.MINUTES)

        assertEquals(ProgressMetric.SPEED, defaultCardioMetricOf(available))
    }

    @Test
    fun `intensity is the default when there is no speed`() {
        assertEquals(
            ProgressMetric.INTENSITY,
            defaultCardioMetricOf(listOf(ProgressMetric.INTENSITY, ProgressMetric.MINUTES)),
        )
    }

    @Test
    fun `time is the default when there is neither speed nor intensity`() {
        assertEquals(ProgressMetric.MINUTES, defaultCardioMetricOf(listOf(ProgressMetric.MINUTES)))
        assertEquals(ProgressMetric.MINUTES, defaultCardioMetricOf(emptyList()))
    }

    // cardioTotals

    @Test
    fun `totals add the blocks, and the averages weigh each block by its minutes`() {
        val totals = cardioTotals(
            listOf(
                block(minutes = 30, distanceM = 5000, sessions = 2, intensity = 16.0, speed = 10.0),
                block(minutes = 15, distanceM = 0, sessions = 1, intensity = null, speed = null),
                block(minutes = 45, distanceM = 10_000, sessions = 3, intensity = 10.0, speed = 12.0),
            ),
        )

        assertEquals(90, totals.minutes)
        assertEquals(15_000, totals.distanceM)
        assertEquals(6, totals.sessions)
        // Only the blocks with an intensity count: (16 x 30 + 10 x 45) / 75.
        assertEquals(12.4, totals.intensityAvg!!, 1e-9)
        // Only the blocks with a speed count: (10 x 30 + 12 x 45) / 75.
        assertEquals(11.2, totals.speedKmh!!, 1e-9)
    }

    @Test
    fun `totals of blocks with no intensity or speed have no averages`() {
        val totals = cardioTotals(listOf(block(minutes = 15, distanceM = 0, sessions = 1, intensity = null, speed = null)))

        assertEquals(15, totals.minutes)
        assertNull(totals.intensityAvg)
        assertNull(totals.speedKmh)
    }

    @Test
    fun `totals of no blocks are empty`() {
        val totals = cardioTotals(emptyList())

        assertEquals(0, totals.minutes)
        assertEquals(0, totals.distanceM)
        assertEquals(0, totals.sessions)
        assertNull(totals.intensityAvg)
        assertNull(totals.speedKmh)
    }

    @Test
    fun `a block is drawn as a bar of its minutes, distance and sessions`() {
        val week = CardioBlock(LocalDate.of(2026, 10, 19), 45, 3200, 2, 12.0, 10.0).asBar()

        assertEquals(LocalDate.of(2026, 10, 19), week.weekStart)
        assertEquals(45, week.minutes)
        assertEquals(3200, week.distanceM)
        assertEquals(2, week.sessions)
    }
}

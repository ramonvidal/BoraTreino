package com.boratreino.feature.progress

import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.TrendVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trend and forecast the machine detail state carries: when they exist, what they follow, and which metric they name. */
class MachineStateTrendTest {
    private val today = ProgressSamples.today

    /** One point a week from 7 Sep 2026, values in the order given. */
    private fun weekly(
        vararg values: Double,
        metric: ProgressMetric = ProgressMetric.WEIGHT,
        firstSessionId: Long = 10,
    ): List<SessionPoint> = ProgressSamples.points(
        firstSessionId,
        values.mapIndexed { index, value -> ProgressSamples.date(9, 7).plusWeeks(index.toLong()) to value },
        metric,
    )

    private fun state(progress: MachineProgress?) =
        buildMachineState(progress, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)

    private val weightMachine = ProgressSamples.machine(1, "Leg press", MuscleGroup.LEGS)

    @Test
    fun `a rising weight machine carries its trend and a four-week forecast from the last point`() {
        val progress = MachineProgress(weightMachine, ProgressMetric.WEIGHT, weekly(100.0, 102.0, 104.0, 106.0, 108.0))

        val state = state(progress)

        val trend = checkNotNull(state.trend)
        assertEquals(TrendVerdict.RISING, trend.verdict)
        assertEquals(2.0, trend.fit!!.slopePerWeek, 1e-6)
        val forecast = checkNotNull(state.forecast)
        assertEquals(4.0, forecast.weeksAhead, 1e-9)
        assertEquals(116.0, forecast.value, 1e-6)
        assertTrue(forecast.low <= forecast.value)
        assertTrue(forecast.value <= forecast.high)
    }

    @Test
    fun `while the trend needs more data the forecast is null`() {
        val progress = MachineProgress(weightMachine, ProgressMetric.WEIGHT, weekly(100.0, 102.0, 104.0))

        val state = state(progress)

        val trend = checkNotNull(state.trend)
        assertEquals(TrendVerdict.NOT_ENOUGH_DATA, trend.verdict)
        assertEquals(3, trend.points)
        assertNull(state.forecast)
    }

    @Test
    fun `a flat machine is a plateau and still has a forecast`() {
        val progress = MachineProgress(weightMachine, ProgressMetric.WEIGHT, weekly(100.0, 100.0, 100.0, 100.0, 100.0))

        val state = state(progress)

        assertEquals(TrendVerdict.PLATEAU, state.trend!!.verdict)
        assertEquals(100.0, checkNotNull(state.forecast).value, 1e-6)
    }

    @Test
    fun `a falling machine has a falling trend and a forecast below the last point`() {
        val progress = MachineProgress(weightMachine, ProgressMetric.WEIGHT, weekly(110.0, 108.0, 106.0, 104.0, 102.0))

        val state = state(progress)

        assertEquals(TrendVerdict.FALLING, state.trend!!.verdict)
        assertEquals(-2.0, state.trend!!.fit!!.slopePerWeek, 1e-6)
        assertEquals(94.0, checkNotNull(state.forecast).value, 1e-6)
    }

    @Test
    fun `a machine without any point has no trend and no forecast`() {
        val state = state(MachineProgress(weightMachine, ProgressMetric.WEIGHT, emptyList()))

        assertNull(state.trend)
        assertNull(state.forecast)
    }

    @Test
    fun `a missing machine has no trend`() {
        assertNull(state(null).trend)
    }

    @Test
    fun `a weight machine without one-rep-max points trends its top weight`() {
        val progress = MachineProgress(weightMachine, ProgressMetric.WEIGHT, weekly(100.0, 102.0, 104.0, 106.0, 108.0))

        assertEquals(ProgressMetric.WEIGHT, state(progress).trendMetric)
    }

    @Test
    fun `a reps machine trends its reps`() {
        val progress = MachineProgress(
            ProgressSamples.machine(2, "Pull up", MuscleGroup.BACK),
            ProgressMetric.REPS,
            weekly(8.0, 9.0, 10.0, 11.0, 12.0, metric = ProgressMetric.REPS),
        )

        assertEquals(ProgressMetric.REPS, state(progress).trendMetric)
    }

    @Test
    fun `a machine with one-rep-max points trends the one-rep max and follows its series`() {
        val progress = MachineProgress(
            weightMachine,
            ProgressMetric.WEIGHT,
            points = weekly(100.0, 100.0, 100.0, 100.0, 100.0),
            oneRmPoints = weekly(100.0, 102.0, 104.0, 106.0, 108.0, firstSessionId = 50),
        )

        val state = state(progress)

        assertEquals(ProgressMetric.ONE_RM, state.trendMetric)
        assertEquals(2.0, state.trend!!.fit!!.slopePerWeek, 1e-6)
    }

    @Test
    fun `a cardio machine trends its speed`() {
        val treadmill = ProgressSamples.machine(6, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)
        val speed = weekly(8.0, 8.5, 9.0, 9.5, 10.0, metric = ProgressMetric.SPEED)
        val progress = MachineProgress(
            treadmill,
            ProgressMetric.SPEED,
            points = speed,
            cardioPoints = mapOf(ProgressMetric.SPEED to speed),
        )

        val state = state(progress)

        assertEquals(ProgressMetric.SPEED, state.trendMetric)
        assertEquals(0.5, state.trend!!.fit!!.slopePerWeek, 1e-6)
        assertEquals(12.0, checkNotNull(state.forecast).value, 1e-6)
    }
}

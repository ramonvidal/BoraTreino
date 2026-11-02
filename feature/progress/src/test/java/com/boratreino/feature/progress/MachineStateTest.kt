package com.boratreino.feature.progress

import com.boratreino.core.model.ChartRules
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.boratreino.feature.progress.ProgressSamples.date
import com.boratreino.feature.progress.ProgressSamples.machine
import com.boratreino.feature.progress.ProgressSamples.points
import com.boratreino.core.model.MuscleGroup

class MachineStateTest {
    private val today = ProgressSamples.today
    private val legPress = machine(1, "Leg press", MuscleGroup.LEGS)

    private fun history(vararg rows: Pair<java.time.LocalDate, Double>) =
        MachineProgress(legPress, ProgressMetric.WEIGHT, points(10, rows.toList()))

    private val mixed = history(
        date(11, 10, 2025) to 80.0,
        date(8, 1) to 90.0,       // one day before the three-month window
        date(8, 2) to 95.0,       // first day of the window
        date(9, 3) to 100.0,
        date(11, 2) to 125.0,
    )

    @Test
    fun `a machine that does not exist is not found`() {
        val state = buildMachineState(null, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertTrue(state.loaded)
        assertFalse(state.found)
    }

    @Test
    fun `a found machine carries its name, metric, range, day and show-all flag`() {
        val state = buildMachineState(mixed, ProgressRange.ONE_YEAR, today, selected = null, showAll = true)
        assertTrue(state.loaded)
        assertTrue(state.found)
        assertEquals("Leg press", state.name)
        assertEquals(ProgressMetric.WEIGHT, state.metric)
        assertEquals(ProgressRange.ONE_YEAR, state.range)
        assertEquals(today, state.today)
        assertTrue(state.showAll)
    }

    @Test
    fun `points are the ones inside the range, ascending`() {
        val state = buildMachineState(mixed, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertEquals(listOf(95.0, 100.0, 125.0), state.points.map { it.value })
        assertEquals(listOf(date(8, 2), date(9, 3), date(11, 2)), state.points.map { it.date })
    }

    @Test
    fun `the one-year range reaches back a year from today`() {
        val state = buildMachineState(mixed, ProgressRange.ONE_YEAR, today, selected = null, showAll = false)
        assertEquals(listOf(80.0, 90.0, 95.0, 100.0, 125.0), state.points.map { it.value })
    }

    @Test
    fun `all keeps every point`() {
        val state = buildMachineState(mixed, ProgressRange.ALL, today, selected = null, showAll = false)
        assertEquals(5, state.points.size)
    }

    @Test
    fun `best is the all-time best even when it is outside the range`() {
        val progress = history(date(11, 10, 2025) to 130.0, date(10, 1) to 100.0)
        val state = buildMachineState(progress, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertEquals(130.0, state.best!!.value, 1e-9)
        assertEquals(date(11, 10, 2025), state.best!!.date)
    }

    @Test
    fun `best is null with no points`() {
        val state = buildMachineState(history(), ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertNull(state.best)
        assertNull(state.change)
        assertTrue(state.points.isEmpty())
        assertTrue(state.loaded)
        assertTrue(state.found)
    }

    @Test
    fun `change is the last minus the first point of the range`() {
        val inRange = buildMachineState(mixed, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertEquals(30.0, inRange.change!!, 1e-9)
        val all = buildMachineState(mixed, ProgressRange.ALL, today, selected = null, showAll = false)
        assertEquals(45.0, all.change!!, 1e-9)
    }

    @Test
    fun `change is zero when the first and last values are equal`() {
        val flat = history(date(9, 3) to 100.0, date(11, 2) to 100.0)
        val state = buildMachineState(flat, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertEquals(0.0, state.change!!, 1e-9)
    }

    @Test
    fun `change is null with one session in the range`() {
        val sparse = history(date(6, 10) to 40.0, date(9, 15) to 42.5)
        val state = buildMachineState(sparse, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertEquals(1, state.points.size)
        assertNull(state.change)
    }

    @Test
    fun `the scale is the nice scale of the values in the range`() {
        val state = buildMachineState(mixed, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertEquals(ChartRules.niceScale(95.0, 125.0), state.scale)
        assertTrue(state.scale.min <= 95.0)
        assertTrue(state.scale.max >= 125.0)
    }

    @Test
    fun `the axis covers three months back to today`() {
        val state = buildMachineState(mixed, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertEquals(date(8, 2), state.axisStart)
        assertEquals(today, state.axisEnd)
    }

    @Test
    fun `the axis covers a year back to today`() {
        val state = buildMachineState(mixed, ProgressRange.ONE_YEAR, today, selected = null, showAll = false)
        assertEquals(date(11, 2, 2025), state.axisStart)
        assertEquals(today, state.axisEnd)
    }

    @Test
    fun `the axis for all starts at the first point`() {
        val state = buildMachineState(mixed, ProgressRange.ALL, today, selected = null, showAll = false)
        assertEquals(date(11, 10, 2025), state.axisStart)
        assertEquals(today, state.axisEnd)
    }

    @Test
    fun `the selected index is kept`() {
        val state = buildMachineState(mixed, ProgressRange.THREE_MONTHS, today, selected = 1, showAll = false)
        assertEquals(1, state.selectedIndex)
    }

    @Test
    fun `no selection gives no selected index`() {
        val state = buildMachineState(mixed, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)
        assertNull(state.selectedIndex)
    }

    @Test
    fun `the sessions list shows fifteen rows before show all`() {
        assertEquals(15, SESSIONS_SHOWN)
    }
}

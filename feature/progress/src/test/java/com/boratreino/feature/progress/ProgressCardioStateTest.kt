package com.boratreino.feature.progress

import com.boratreino.core.model.CardioWeek
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.model.ProgressRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cardio parts of the Progress states: the machine's weeks and the overview's Cardio this week. */
class ProgressCardioStateTest {
    private val today = ProgressSamples.today
    private val thisWeek = ProgressCardioSamples.twelveWeeks.last()

    private fun machineState(range: ProgressRange = ProgressRange.THREE_MONTHS) = buildMachineState(
        ProgressCardioSamples.treadmillHistory, range, today, selected = null, showAll = false,
    )

    private fun overview(cardio: CardioWeek?) = ProgressOverview(
        machines = ProgressSamples.overview.machines,
        trainingsThisMonth = 3,
        recordsThisMonth = 0,
        cardioThisWeek = cardio,
    )

    // Machine progress

    @Test
    fun `a cardio machine's state carries the metric, name and its twelve weeks`() {
        val state = machineState()

        assertEquals(ProgressMetric.MINUTES, state.metric)
        assertEquals("Treadmill", state.name)
        assertEquals(12, state.weeks.size)
        assertEquals(ProgressCardioSamples.twelveWeeks, state.weeks)
        assertEquals(thisWeek, state.weeks.last())
    }

    @Test
    fun `the weeks are the same in every range`() {
        for (range in ProgressRange.entries) {
            assertEquals(ProgressCardioSamples.twelveWeeks, machineState(range).weeks)
        }
    }

    @Test
    fun `the weeks stay oldest first and end with this week`() {
        val weeks = machineState().weeks

        assertEquals(weeks.map { it.weekStart }.sorted(), weeks.map { it.weekStart })
        assertEquals(ProgressCardioSamples.weekStart(0), weeks.last().weekStart)
    }

    @Test
    fun `the minutes chart uses the session minutes as values`() {
        val state = machineState()

        assertEquals(listOf(20.0, 25.0, 30.0, 30.0, 35.0, 40.0, 40.0, 45.0), state.points.map { it.value })
        assertEquals(45.0, state.best!!.value, 1e-9)
        assertEquals(25.0, state.change!!, 1e-9)
    }

    @Test
    fun `a weight machine has no weeks`() {
        val state = buildMachineState(ProgressSamples.legPressHistory, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)

        assertTrue(state.weeks.isEmpty())
        assertEquals(ProgressMetric.WEIGHT, state.metric)
    }

    @Test
    fun `a missing machine has no weeks`() {
        val state = buildMachineState(null, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false)

        assertTrue(state.weeks.isEmpty())
    }

    @Test
    fun `a cardio machine with no sessions in range keeps its weeks`() {
        val state = buildMachineState(
            ProgressCardioSamples.treadmillHistory.copy(points = emptyList(), weeks = ProgressCardioSamples.emptyWeeks),
            ProgressRange.THREE_MONTHS, today, selected = null, showAll = false,
        )

        assertTrue(state.points.isEmpty())
        assertNull(state.best)
        assertEquals(12, state.weeks.size)
    }

    // Overview

    @Test
    fun `this week's cardio reaches the overview state`() {
        val state = buildProgressState(overview(thisWeek), today)

        assertEquals(thisWeek, state.cardioThisWeek)
    }

    @Test
    fun `no cardio week means no card`() {
        assertNull(buildProgressState(overview(null), today).cardioThisWeek)
    }

    @Test
    fun `a week with no minutes and no calories shows no card`() {
        val quiet = CardioWeek(ProgressCardioSamples.weekStart(0), minutes = 0, calories = 0, distanceM = 0, sessions = 0)

        assertNull(buildProgressState(overview(quiet), today).cardioThisWeek)
    }

    @Test
    fun `a week with only a distance shows no card`() {
        val distanceOnly = CardioWeek(ProgressCardioSamples.weekStart(0), minutes = 0, calories = 0, distanceM = 5000, sessions = 1)

        assertNull(buildProgressState(overview(distanceOnly), today).cardioThisWeek)
    }

    @Test
    fun `minutes alone or calories alone show the card`() {
        val minutes = CardioWeek(ProgressCardioSamples.weekStart(0), minutes = 20, calories = 0, distanceM = 0, sessions = 1)
        val calories = CardioWeek(ProgressCardioSamples.weekStart(0), minutes = 0, calories = 150, distanceM = 0, sessions = 1)

        assertNotNull(buildProgressState(overview(minutes), today).cardioThisWeek)
        assertNotNull(buildProgressState(overview(calories), today).cardioThisWeek)
    }

    @Test
    fun `the cardio week does not change the other overview numbers`() {
        val with = buildProgressState(overview(thisWeek), today)
        val without = buildProgressState(overview(null), today)

        assertEquals(without.machines, with.machines)
        assertEquals(without.improved, with.improved)
        assertEquals(without.trainingsThisMonth, with.trainingsThisMonth)
    }

    @Test
    fun `a cardio machine appears in the machine list with the minutes metric`() {
        val state = buildProgressState(ProgressCardioSamples.overview, today)

        val row = state.machines.first { it.machine.name == "Treadmill" }
        assertEquals(ProgressMetric.MINUTES, row.metric)
        assertEquals(45.0, row.best, 1e-9)
        assertEquals(today, row.lastTrained)
    }
}

package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Body and Habit of the Overview tab (C6 contract, section 1.6). Dates are in November 2026. */
class OverviewRulesTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun d(day: Int): LocalDate = LocalDate.of(2026, 11, day)

    private fun m(type: MeasurementType, day: Int, cm: Double) = BodyMeasurement(0, d(day), type, cm)

    private fun report(
        cardio: List<CardioBlock> = emptyList(),
        times: SessionTimes = SessionTimes(0, null, null),
    ): StatsReport = StatsReport(
        period = StatsPeriod.FOUR_WEEKS,
        from = today.minusDays(27),
        to = today,
        weeks = emptyList(),
        muscleWeeks = emptyList(),
        recency = emptyList(),
        consistency = Consistency(4, 0, 3, 0.0, null, 0),
        strength = emptyList(),
        overallStrengthPercent = null,
        records = emptyList(),
        plateaus = emptyList(),
        repRanges = emptyList(),
        times = times,
        cardioBlocks = cardio,
    )

    private fun block(day: Int, minutes: Int, distanceM: Int, sessions: Int) =
        CardioBlock(d(day), minutes, distanceM, sessions, null, null)

    // body

    @Test
    fun `body shows the latest value of each type in measurement order`() {
        val snapshot = OverviewRules.body(
            listOf(
                m(MeasurementType.HIPS, 3, 100.0),
                m(MeasurementType.WAIST, 1, 90.0),
                m(MeasurementType.WAIST, 5, 88.0),
            ),
        )
        assertEquals(listOf(MeasurementType.WAIST, MeasurementType.HIPS), snapshot.tiles.map { it.type })
        assertEquals(88.0, snapshot.tiles.first().valueCm, 0.0)
        assertEquals(100.0, snapshot.tiles.last().valueCm, 0.0)
    }

    @Test
    fun `body shows at most four tiles, the first four types with data`() {
        val snapshot = OverviewRules.body(
            listOf(
                m(MeasurementType.THIGH, 2, 60.0),
                m(MeasurementType.WAIST, 1, 90.0),
                m(MeasurementType.NECK, 2, 38.0),
                m(MeasurementType.ARM, 2, 35.0),
                m(MeasurementType.HIPS, 2, 100.0),
                m(MeasurementType.CHEST, 2, 105.0),
            ),
        )
        assertEquals(OverviewRules.MAX_TILES, snapshot.tiles.size)
        assertEquals(4, OverviewRules.MAX_TILES)
        assertEquals(
            listOf(MeasurementType.WAIST, MeasurementType.HIPS, MeasurementType.CHEST, MeasurementType.ARM),
            snapshot.tiles.map { it.type },
        )
    }

    @Test
    fun `body latest date is the newest date among the shown tiles only`() {
        val snapshot = OverviewRules.body(
            listOf(
                m(MeasurementType.WAIST, 1, 90.0),
                m(MeasurementType.HIPS, 3, 100.0),
                m(MeasurementType.CHEST, 4, 105.0),
                m(MeasurementType.ARM, 2, 35.0),
                m(MeasurementType.THIGH, 6, 60.0),
                m(MeasurementType.NECK, 9, 38.0), // newer, but not shown
            ),
        )
        assertEquals(d(4), snapshot.latestDate)
    }

    @Test
    fun `body has a trend when a shown type has two measurements`() {
        val snapshot = OverviewRules.body(
            listOf(m(MeasurementType.WAIST, 1, 90.0), m(MeasurementType.WAIST, 5, 88.0)),
        )
        assertTrue(snapshot.hasTrend)
    }

    @Test
    fun `body has no trend when each shown type has one measurement`() {
        val snapshot = OverviewRules.body(
            listOf(m(MeasurementType.WAIST, 1, 90.0), m(MeasurementType.HIPS, 5, 100.0)),
        )
        assertFalse(snapshot.hasTrend)
    }

    @Test
    fun `body has no trend from a type that is not shown`() {
        val measurements = listOf(
            m(MeasurementType.WAIST, 1, 90.0),
            m(MeasurementType.HIPS, 1, 100.0),
            m(MeasurementType.CHEST, 1, 105.0),
            m(MeasurementType.ARM, 1, 35.0),
            m(MeasurementType.NECK, 1, 38.0),
            m(MeasurementType.NECK, 5, 37.5), // two measurements, but NECK is the fifth type with data
        )
        assertFalse(OverviewRules.body(measurements).hasTrend)
    }

    @Test
    fun `body without measurements is empty`() {
        val snapshot = OverviewRules.body(emptyList())
        assertTrue(snapshot.tiles.isEmpty())
        assertNull(snapshot.latestDate)
        assertFalse(snapshot.hasTrend)
    }

    // habit

    @Test
    fun `habit is null without cardio and without a session length`() {
        assertNull(OverviewRules.habit(report(cardio = emptyList(), times = SessionTimes(3, null, 2.5))))
    }

    @Test
    fun `habit is shown with a session length and no cardio`() {
        val habit = OverviewRules.habit(report(times = SessionTimes(3, 45, 2.5)))!!
        assertEquals(0, habit.cardioSessions)
        assertEquals(45, habit.averageSessionMinutes)
        assertEquals(2.5, habit.minutesBetweenSets!!, 0.0)
    }

    @Test
    fun `habit is shown with cardio and no session length`() {
        val habit = OverviewRules.habit(report(cardio = listOf(block(3, 20, 3000, 1))))
        assertEquals(1, habit!!.cardioSessions)
        assertNull(habit.averageSessionMinutes)
    }

    @Test
    fun `habit sums the cardio blocks and takes the session times from the report`() {
        val r = report(
            cardio = listOf(block(3, 20, 3000, 2), block(9, 30, 4000, 1)),
            times = SessionTimes(4, 45, 2.5),
        )
        val habit = OverviewRules.habit(r)!!
        assertEquals(r.from, habit.since)
        assertEquals(3, habit.cardioSessions)
        assertEquals(50, habit.cardioMinutes)
        assertEquals(7000, habit.cardioDistanceM)
        assertEquals(45, habit.averageSessionMinutes)
        assertEquals(2.5, habit.minutesBetweenSets!!, 0.0)
    }
}

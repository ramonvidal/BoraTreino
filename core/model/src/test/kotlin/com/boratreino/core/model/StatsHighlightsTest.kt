package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Highlights the Progress tab leads with. Built from report fixtures; dates are 2026. */
class StatsHighlightsTest {
    private val today = LocalDate.of(2026, 11, 5)

    private fun machine(id: Long, group: MuscleGroup = MuscleGroup.CHEST) =
        Machine(id, "Machine $id", group, MachineKind.WEIGHT_REPS, 2.5, null, null, false)

    private fun muscleWeek(group: MuscleGroup, sets: Int) =
        MuscleWeek(today.minusWeeks(1), group, sets, sets * 100.0)

    /** Every group in enum order; the given groups get [daysSince], the others were never trained. */
    private fun recencyOf(vararg daysSince: Pair<MuscleGroup, Int>): List<MuscleRecency> {
        val byGroup = daysSince.toMap()
        return MuscleGroup.entries.map { group ->
            val days = byGroup[group]
            MuscleRecency(group, days?.let { today.minusDays(it.toLong()) }, days)
        }
    }

    private fun report(
        weekCount: Int = 4,
        muscleWeeks: List<MuscleWeek> = emptyList(),
        recency: List<MuscleRecency> = emptyList(),
        weeksMetGoal: Int = 0,
        strength: List<StrengthTrend> = emptyList(),
    ): StatsReport = StatsReport(
        period = StatsPeriod.FOUR_WEEKS,
        from = today.minusWeeks(weekCount.toLong() - 1),
        to = today,
        weeks = (0 until weekCount).map { i ->
            WeekStats(today.minusWeeks(i.toLong()), sessions = 0, trainingDays = 0, workingSets = 0, volumeKg = 0.0)
        },
        muscleWeeks = muscleWeeks,
        recency = recency,
        consistency = Consistency(
            weeksCounted = weekCount,
            weeksMetGoal = weeksMetGoal,
            goal = 3,
            sessionsPerWeek = 0.0,
            bestWeekStart = null,
            bestWeekTrainingDays = 0,
        ),
        strength = strength,
        overallStrengthPercent = null,
        records = emptyList(),
        plateaus = emptyList(),
        repRanges = emptyList(),
        times = SessionTimes(0, null, null),
    )

    private fun trend(id: Long, changePercent: Double) = StrengthTrend(
        machine = machine(id),
        before = 100.0,
        after = 100.0 + changePercent,
        changeKg = changePercent,
        changePercent = changePercent,
    )

    /** A report that qualifies for all four highlights. */
    private fun fullReport(): StatsReport = report(
        muscleWeeks = listOf(muscleWeek(MuscleGroup.LEGS, 48)),
        recency = recencyOf(MuscleGroup.BACK to 9),
        weeksMetGoal = 5,
        strength = listOf(trend(1, 8.0)),
    )

    // Order and the max

    @Test
    fun `highlights come in the order strength gain, muscle load, muscle behind, goal streak`() {
        val kinds = StatsHighlights.of(fullReport(), max = 10).map { it.kind }

        assertEquals(
            listOf(
                HighlightKind.STRENGTH_GAIN,
                HighlightKind.MUSCLE_LOAD,
                HighlightKind.MUSCLE_BEHIND,
                HighlightKind.GOAL_STREAK,
            ),
            kinds,
        )
    }

    @Test
    fun `the default max keeps the first three highlights`() {
        val kinds = StatsHighlights.of(fullReport()).map { it.kind }

        assertEquals(
            listOf(HighlightKind.STRENGTH_GAIN, HighlightKind.MUSCLE_LOAD, HighlightKind.MUSCLE_BEHIND),
            kinds,
        )
    }

    @Test
    fun `max cuts the list after the first entries`() {
        val kinds = StatsHighlights.of(fullReport(), max = 1).map { it.kind }

        assertEquals(listOf(HighlightKind.STRENGTH_GAIN), kinds)
    }

    @Test
    fun `max of zero or below gives no highlights`() {
        assertTrue(StatsHighlights.of(fullReport(), max = 0).isEmpty())
        assertTrue(StatsHighlights.of(fullReport(), max = -2).isEmpty())
    }

    @Test
    fun `only the highlights that exist are returned`() {
        val kinds = StatsHighlights.of(report(weeksMetGoal = 2), max = 10).map { it.kind }

        assertEquals(listOf(HighlightKind.GOAL_STREAK), kinds)
    }

    @Test
    fun `an empty report gives no highlights and does not throw`() {
        assertTrue(StatsHighlights.of(report(weekCount = 0)).isEmpty())
        assertTrue(StatsHighlights.of(report(weekCount = 4)).isEmpty())
    }

    // Strength gain

    @Test
    fun `strength gain is the first trend in the report with a rise`() {
        val highlight = StatsHighlights.of(
            report(strength = listOf(trend(1, -4.0), trend(2, 8.0), trend(3, 3.0))),
        ).single()

        assertEquals(HighlightKind.STRENGTH_GAIN, highlight.kind)
        assertEquals(2L, highlight.machine?.id)
        assertEquals(8.0, highlight.value, 1e-9)
        assertEquals(StatsPage.STRENGTH, highlight.target)
    }

    @Test
    fun `no strength gain when no trend rose`() {
        val kinds = StatsHighlights.of(
            report(strength = listOf(trend(1, 0.0), trend(2, -3.0))),
        ).map { it.kind }

        assertTrue(HighlightKind.STRENGTH_GAIN !in kinds)
    }

    // Muscle load

    @Test
    fun `muscle load picks the group with the most average sets a week`() {
        val highlight = StatsHighlights.of(
            report(
                weekCount = 4,
                muscleWeeks = listOf(muscleWeek(MuscleGroup.CHEST, 8), muscleWeek(MuscleGroup.LEGS, 20)),
            ),
        ).single()

        assertEquals(HighlightKind.MUSCLE_LOAD, highlight.kind)
        assertEquals(MuscleGroup.LEGS, highlight.muscleGroup)
        assertEquals(5.0, highlight.value, 1e-9)
        assertEquals(StatsPage.BODY, highlight.target)
    }

    @Test
    fun `muscle load ties go to the group earlier in enum order`() {
        val highlight = StatsHighlights.of(
            report(
                weekCount = 4,
                muscleWeeks = listOf(muscleWeek(MuscleGroup.BACK, 12), muscleWeek(MuscleGroup.CHEST, 12)),
            ),
        ).single()

        assertEquals(MuscleGroup.CHEST, highlight.muscleGroup)
    }

    @Test
    fun `no muscle load highlight without any sets`() {
        val kinds = StatsHighlights.of(report(weekCount = 4)).map { it.kind }

        assertTrue(HighlightKind.MUSCLE_LOAD !in kinds)
    }

    // Muscle behind

    @Test
    fun `muscle behind names the group trained longest ago when it is a week or more`() {
        val highlight = StatsHighlights.of(
            report(recency = recencyOf(MuscleGroup.BACK to 9, MuscleGroup.ARMS to 7)),
        ).single()

        assertEquals(HighlightKind.MUSCLE_BEHIND, highlight.kind)
        assertEquals(MuscleGroup.BACK, highlight.muscleGroup)
        assertEquals(9, highlight.days)
        assertEquals(StatsPage.BODY, highlight.target)
    }

    @Test
    fun `muscle behind starts at seven days`() {
        val highlight = StatsHighlights.of(report(recency = recencyOf(MuscleGroup.LEGS to 7))).single()

        assertEquals(MuscleGroup.LEGS, highlight.muscleGroup)
        assertEquals(7, highlight.days)
    }

    @Test
    fun `muscle behind is not shown under seven days`() {
        val kinds = StatsHighlights.of(report(recency = recencyOf(MuscleGroup.LEGS to 6))).map { it.kind }

        assertTrue(kinds.isEmpty())
    }

    @Test
    fun `groups never trained are not mentioned as behind`() {
        val highlight = StatsHighlights.of(
            report(recency = recencyOf(MuscleGroup.BACK to 9)),
        ).single()

        assertEquals(MuscleGroup.BACK, highlight.muscleGroup)
    }

    @Test
    fun `no muscle behind when no group was ever trained`() {
        val kinds = StatsHighlights.of(report(recency = recencyOf())).map { it.kind }

        assertTrue(kinds.isEmpty())
    }

    @Test
    fun `cardio is never named as behind`() {
        val highlight = StatsHighlights.of(
            report(recency = recencyOf(MuscleGroup.CARDIO to 30, MuscleGroup.BACK to 9)),
        ).single()

        assertEquals(MuscleGroup.BACK, highlight.muscleGroup)
    }

    // Goal streak

    @Test
    fun `goal streak counts the weeks that met the goal`() {
        val highlight = StatsHighlights.of(report(weeksMetGoal = 5)).single()

        assertEquals(HighlightKind.GOAL_STREAK, highlight.kind)
        assertEquals(5.0, highlight.value, 1e-9)
        assertEquals(StatsPage.HABIT, highlight.target)
        assertNull(highlight.muscleGroup)
        assertNull(highlight.machine)
    }

    @Test
    fun `no goal streak when no week met the goal`() {
        assertTrue(StatsHighlights.of(report(weeksMetGoal = 0)).isEmpty())
    }
}

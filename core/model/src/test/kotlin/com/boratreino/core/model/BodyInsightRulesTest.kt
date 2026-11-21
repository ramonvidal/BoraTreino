package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Body insight summaries: relative strength, body weight index, body map shades. Dates are 2026. */
class BodyInsightRulesTest {
    private fun d(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private fun machine(id: Long, kind: MachineKind = MachineKind.WEIGHT_REPS) =
        Machine(id, "Machine $id", MuscleGroup.CHEST, kind, 2.5, null, null, false)

    private fun estimate(id: Long, kg1rm: Double, kind: MachineKind = MachineKind.WEIGHT_REPS) =
        MachineEstimate(machine(id, kind), kg1rm)

    private fun muscleWeek(week: LocalDate, group: MuscleGroup, sets: Int) =
        MuscleWeek(week, group, sets, sets * 100.0)

    /** Thirteen 7-day blocks from Thursday 13 Aug 2026 to Wednesday 11 Nov, as a rolling report holds them. */
    private val weeks: List<WeekStats> = (0 until 13).map { i ->
        WeekStats(d(8, 13).plusWeeks(i.toLong()), sessions = 0, trainingDays = 0, workingSets = 0, volumeKg = 0.0)
    }

    private fun weekOf(i: Int): LocalDate = d(8, 13).plusWeeks(i.toLong())

    // Relative strength

    @Test
    fun `relative strength is the estimate over the body weight, best first`() {
        val ranking = BodyInsightRules.relativeStrength(
            listOf(estimate(1, 100.0), estimate(2, 150.0), estimate(3, 120.0)),
            bodyWeightKg = 80.0,
        )

        assertEquals(listOf(2L, 3L, 1L), ranking.map { it.first.machine.id })
        assertEquals(1.875, ranking[0].second, 1e-9)
        assertEquals(1.5, ranking[1].second, 1e-9)
        assertEquals(1.25, ranking[2].second, 1e-9)
    }

    @Test
    fun `relative strength keeps eight machines by default and the limit can be changed`() {
        val estimates = (1..10).map { estimate(it.toLong(), 80.0 + it) }

        val eight = BodyInsightRules.relativeStrength(estimates, 80.0)
        val three = BodyInsightRules.relativeStrength(estimates, 80.0, limit = 3)

        assertEquals(8, eight.size)
        assertEquals(10L, eight.first().first.machine.id)
        assertEquals(3L, eight.last().first.machine.id)
        assertEquals(listOf(10L, 9L, 8L), three.map { it.first.machine.id })
    }

    @Test
    fun `only weight and reps machines are ranked`() {
        val ranking = BodyInsightRules.relativeStrength(
            listOf(
                estimate(1, 300.0, MachineKind.BODYWEIGHT_REPS),
                estimate(2, 200.0, MachineKind.CARDIO),
                estimate(3, 90.0),
            ),
            bodyWeightKg = 80.0,
        )

        assertEquals(listOf(3L), ranking.map { it.first.machine.id })
    }

    @Test
    fun `relative strength is empty without a usable body weight`() {
        val estimates = listOf(estimate(1, 100.0))

        assertTrue(BodyInsightRules.relativeStrength(estimates, 0.0).isEmpty())
        assertTrue(BodyInsightRules.relativeStrength(estimates, -70.0).isEmpty())
        assertTrue(BodyInsightRules.relativeStrength(emptyList(), 80.0).isEmpty())
    }

    // Body weight index

    @Test
    fun `body weight is carried forward from the last entry on or before each week's end`() {
        val weights = listOf(WeightEntry(d(10, 1), 80.0), WeightEntry(d(10, 15), 78.0))
        val weekStarts = listOf(d(10, 5), d(10, 12), d(10, 19), d(10, 26))

        val index = BodyInsightRules.weightIndex(weights, weekStarts)

        assertEquals(weekStarts, index.map { it.weekStart })
        assertEquals(100.0, index[0].percent, 0.0)
        assertEquals(97.5, index[1].percent, 1e-9)   // 78 / 80, the entry of 15 Oct
        assertEquals(97.5, index[2].percent, 1e-9)   // carried forward
        assertEquals(97.5, index[3].percent, 1e-9)   // carried forward
    }

    @Test
    fun `weeks before the first entry are skipped and the index starts at 100 at the first point`() {
        val weights = listOf(WeightEntry(d(10, 15), 78.0), WeightEntry(d(10, 22), 76.0))
        val weekStarts = listOf(d(10, 5), d(10, 12), d(10, 19), d(10, 26))

        val index = BodyInsightRules.weightIndex(weights, weekStarts)

        assertEquals(listOf(d(10, 12), d(10, 19), d(10, 26)), index.map { it.weekStart })
        assertEquals(100.0, index[0].percent, 0.0)
        assertEquals(76.0 / 78.0 * 100.0, index[1].percent, 1e-9)
        assertEquals(76.0 / 78.0 * 100.0, index[2].percent, 1e-9)
    }

    @Test
    fun `weights in any order give the same index`() {
        val weekStarts = listOf(d(10, 5), d(10, 12), d(10, 19), d(10, 26))
        val ordered = listOf(WeightEntry(d(10, 1), 80.0), WeightEntry(d(10, 15), 78.0))

        assertEquals(
            BodyInsightRules.weightIndex(ordered, weekStarts),
            BodyInsightRules.weightIndex(ordered.reversed(), weekStarts),
        )
    }

    @Test
    fun `the index needs two distinct entry dates in or before the range`() {
        val weekStarts = listOf(d(10, 5), d(10, 12), d(10, 19), d(10, 26))

        assertTrue(BodyInsightRules.weightIndex(emptyList(), weekStarts).isEmpty())
        assertTrue(BodyInsightRules.weightIndex(listOf(WeightEntry(d(10, 1), 80.0)), weekStarts).isEmpty())
        // Two entries on the same date are one date.
        assertTrue(
            BodyInsightRules.weightIndex(
                listOf(WeightEntry(d(10, 1), 80.0), WeightEntry(d(10, 1), 81.0)),
                weekStarts,
            ).isEmpty(),
        )
        // The second entry comes after the range.
        assertTrue(
            BodyInsightRules.weightIndex(
                listOf(WeightEntry(d(10, 1), 80.0), WeightEntry(d(11, 20), 70.0)),
                weekStarts,
            ).isEmpty(),
        )
    }

    @Test
    fun `two entries before the range are enough and give a flat index`() {
        val weights = listOf(WeightEntry(d(9, 28), 80.0), WeightEntry(d(10, 2), 84.0))
        val weekStarts = listOf(d(10, 5), d(10, 12), d(10, 19), d(10, 26))

        val index = BodyInsightRules.weightIndex(weights, weekStarts)

        assertEquals(4, index.size)
        index.forEach { assertEquals(100.0, it.percent, 1e-9) }
    }

    @Test
    fun `no week starts give no index`() {
        assertTrue(BodyInsightRules.weightIndex(listOf(WeightEntry(d(10, 1), 80.0), WeightEntry(d(10, 2), 81.0)), emptyList()).isEmpty())
    }

    // Body map

    @Test
    fun `the body map sums the last four weeks of the report, even when the recent weeks have no sets`() {
        val muscleWeeks = listOf(
            muscleWeek(weekOf(6), MuscleGroup.CHEST, 10),  // outside the last four weeks
            muscleWeek(weekOf(9), MuscleGroup.CHEST, 5),   // the first of the last four
        )

        val sets = BodyInsightRules.bodyMap(muscleWeeks, weeks)

        assertEquals(5, sets.getValue(MuscleGroup.CHEST))
        assertEquals(0, sets.getValue(MuscleGroup.LEGS))
    }

    @Test
    fun `the current week of the report counts and the week before the last four does not`() {
        val muscleWeeks = listOf(
            muscleWeek(weekOf(8), MuscleGroup.ARMS, 7),
            muscleWeek(weekOf(12), MuscleGroup.ARMS, 3),
        )

        assertEquals(3, BodyInsightRules.bodyMap(muscleWeeks, weeks).getValue(MuscleGroup.ARMS))
    }

    @Test
    fun `the last-weeks argument of the report overload counts that many weeks`() {
        val muscleWeeks = listOf(
            muscleWeek(weekOf(9), MuscleGroup.BACK, 4),
            muscleWeek(weekOf(11), MuscleGroup.BACK, 2),
            muscleWeek(weekOf(12), MuscleGroup.BACK, 1),
        )

        assertEquals(3, BodyInsightRules.bodyMap(muscleWeeks, weeks, lastWeeks = 2).getValue(MuscleGroup.BACK))
    }

    @Test
    fun `the overload without weeks counts the last distinct weeks present in the muscle weeks`() {
        val muscleWeeks = listOf(
            muscleWeek(weekOf(2), MuscleGroup.CHEST, 1),
            muscleWeek(weekOf(5), MuscleGroup.CHEST, 2),
            muscleWeek(weekOf(9), MuscleGroup.CHEST, 3),
        )

        assertEquals(5, BodyInsightRules.bodyMap(muscleWeeks, lastWeeks = 2).getValue(MuscleGroup.CHEST))
        assertEquals(6, BodyInsightRules.bodyMap(muscleWeeks, lastWeeks = 3).getValue(MuscleGroup.CHEST))
    }

    @Test
    fun `the body map has all seven groups, zero when none is trained`() {
        val none = BodyInsightRules.bodyMap(emptyList(), weeks)

        assertEquals(MuscleGroup.entries.toSet(), none.keys)
        assertTrue(none.values.all { it == 0 })
    }

    // Shades of average sets a week

    @Test
    fun `shade is 0 for no sets, 1 under 5 a week, 2 from 5 to under 10 and 3 from 10 a week`() {
        assertEquals(0, BodyInsightRules.shade(0.0))
        assertEquals(1, BodyInsightRules.shade(0.5))
        assertEquals(1, BodyInsightRules.shade(4.99))
        assertEquals(2, BodyInsightRules.shade(5.0))
        assertEquals(2, BodyInsightRules.shade(9.99))
        assertEquals(3, BodyInsightRules.shade(10.0))
        assertEquals(3, BodyInsightRules.shade(57.0))
    }

    // Muscle load: average effective sets a week over every week of the report

    /** A report with [weekCount] 7-day blocks from 13 Aug 2026 (the last ends 11 Nov) and the given muscle weeks; the rest is empty. */
    private fun reportOf(
        weekCount: Int,
        muscleWeeks: List<MuscleWeek> = emptyList(),
        indirectWeeks: List<IndirectWeek> = emptyList(),
        detailWeeks: List<MuscleDetailWeek> = emptyList(),
    ): StatsReport = StatsReport(
        period = StatsPeriod.THREE_MONTHS,
        from = d(8, 13),
        to = d(11, 11),
        weeks = (0 until weekCount).map { i ->
            WeekStats(d(8, 13).plusWeeks(i.toLong()), sessions = 0, trainingDays = 0, workingSets = 0, volumeKg = 0.0)
        },
        muscleWeeks = muscleWeeks,
        recency = emptyList(),
        consistency = Consistency(0, 0, 3, 0.0, null, 0),
        strength = emptyList(),
        overallStrengthPercent = null,
        records = emptyList(),
        plateaus = emptyList(),
        repRanges = emptyList(),
        times = SessionTimes(0, null, null),
        indirectWeeks = indirectWeeks,
        detailWeeks = detailWeeks,
    )

    private fun indirectWeek(week: LocalDate, group: MuscleGroup, sets: Int) = IndirectWeek(week, group, sets)

    @Test
    fun `muscle load averages direct sets over every week of the report, empty weeks included`() {
        val report = reportOf(
            weekCount = 13,
            muscleWeeks = listOf(
                muscleWeek(weekOf(0), MuscleGroup.CHEST, 26),
                muscleWeek(weekOf(5), MuscleGroup.CHEST, 13),
            ),
        )

        assertEquals(3.0, BodyInsightRules.muscleLoad(report).getValue(MuscleGroup.CHEST), 1e-9)   // 39 over 13 weeks
        assertEquals(3.0, BodyInsightRules.directLoad(report).getValue(MuscleGroup.CHEST), 1e-9)
    }

    @Test
    fun `the same sets give a smaller average over a longer report`() {
        val lastWeek = listOf(muscleWeek(weekOf(12), MuscleGroup.LEGS, 12))

        val fourWeeks = BodyInsightRules.muscleLoad(reportOf(4, muscleWeeks = lastWeek))
        val threeMonths = BodyInsightRules.muscleLoad(reportOf(13, muscleWeeks = lastWeek))

        assertEquals(3.0, fourWeeks.getValue(MuscleGroup.LEGS), 1e-9)
        assertEquals(12.0 / 13.0, threeMonths.getValue(MuscleGroup.LEGS), 1e-9)
    }

    @Test
    fun `indirect sets count as half a set in the muscle load but not in the direct load`() {
        val report = reportOf(
            weekCount = 2,
            muscleWeeks = listOf(muscleWeek(weekOf(1), MuscleGroup.ARMS, 4)),
            indirectWeeks = listOf(indirectWeek(weekOf(0), MuscleGroup.ARMS, 10)),
        )

        // (4 direct + 0.5 x 10 indirect) / 2 weeks
        assertEquals(4.5, BodyInsightRules.muscleLoad(report).getValue(MuscleGroup.ARMS), 1e-9)
        assertEquals(2.0, BodyInsightRules.directLoad(report).getValue(MuscleGroup.ARMS), 1e-9)
        assertEquals(0.5, StatsRules.INDIRECT_WEIGHT, 0.0)
    }

    @Test
    fun `a group worked only indirectly has a load but no direct load`() {
        val report = reportOf(
            weekCount = 1,
            indirectWeeks = listOf(indirectWeek(weekOf(0), MuscleGroup.SHOULDERS, 3)),
        )

        assertEquals(1.5, BodyInsightRules.muscleLoad(report).getValue(MuscleGroup.SHOULDERS), 1e-9)
        assertEquals(0.0, BodyInsightRules.directLoad(report).getValue(MuscleGroup.SHOULDERS), 0.0)
    }

    @Test
    fun `muscle load has all seven groups and is 0 for an empty report`() {
        val empty = reportOf(weekCount = 0, muscleWeeks = listOf(muscleWeek(weekOf(0), MuscleGroup.CHEST, 9)))

        assertEquals(MuscleGroup.entries.toSet(), BodyInsightRules.muscleLoad(empty).keys)
        assertEquals(MuscleGroup.entries.toSet(), BodyInsightRules.directLoad(empty).keys)
        assertTrue(BodyInsightRules.muscleLoad(empty).values.all { it == 0.0 })
        assertTrue(BodyInsightRules.directLoad(empty).values.all { it == 0.0 })
    }

    @Test
    fun `muscle load is 0 for cardio`() {
        val report = reportOf(
            weekCount = 4,
            muscleWeeks = listOf(muscleWeek(weekOf(0), MuscleGroup.CARDIO, 6)),
            indirectWeeks = listOf(indirectWeek(weekOf(0), MuscleGroup.CARDIO, 6)),
        )

        assertEquals(0.0, BodyInsightRules.muscleLoad(report).getValue(MuscleGroup.CARDIO), 0.0)
        assertEquals(0.0, BodyInsightRules.directLoad(report).getValue(MuscleGroup.CARDIO), 0.0)
    }

    // Muscle detail load: average effective sets per block for each specific muscle

    private fun detailWeek(week: LocalDate, muscle: Muscle, direct: Double, indirect: Double = 0.0) =
        MuscleDetailWeek(week, muscle, direct, indirect)

    @Test
    fun `muscle detail load averages direct sets over every block, indirect ones at half weight`() {
        val report = reportOf(
            weekCount = 4,
            detailWeeks = listOf(
                detailWeek(weekOf(1), Muscle.BICEPS, direct = 4.0),
                detailWeek(weekOf(2), Muscle.BICEPS, direct = 0.0, indirect = 6.0),
            ),
        )

        // (4 direct + 0.5 x 6 indirect) / 4 blocks
        assertEquals(1.75, BodyInsightRules.muscleDetailLoad(report).getValue(Muscle.BICEPS), 1e-9)
        // 4 direct / 4 blocks
        assertEquals(1.0, BodyInsightRules.directDetailLoad(report).getValue(Muscle.BICEPS), 1e-9)
    }

    @Test
    fun `a muscle worked only indirectly has a detail load but no direct detail load`() {
        val report = reportOf(
            weekCount = 1,
            detailWeeks = listOf(detailWeek(weekOf(0), Muscle.ABS, direct = 0.0, indirect = 3.0)),
        )

        assertEquals(1.5, BodyInsightRules.muscleDetailLoad(report).getValue(Muscle.ABS), 1e-9)
        assertEquals(0.0, BodyInsightRules.directDetailLoad(report).getValue(Muscle.ABS), 0.0)
    }

    @Test
    fun `muscle detail load has all fourteen muscles`() {
        val report = reportOf(
            weekCount = 2,
            detailWeeks = listOf(detailWeek(weekOf(0), Muscle.CHEST, direct = 2.0)),
        )

        assertEquals(Muscle.entries.toSet(), BodyInsightRules.muscleDetailLoad(report).keys)
        assertEquals(Muscle.entries.toSet(), BodyInsightRules.directDetailLoad(report).keys)
        assertEquals(14, BodyInsightRules.muscleDetailLoad(report).size)
        assertEquals(1.0, BodyInsightRules.muscleDetailLoad(report).getValue(Muscle.CHEST), 1e-9)
        assertEquals(0.0, BodyInsightRules.muscleDetailLoad(report).getValue(Muscle.HIPS), 0.0)
    }

    @Test
    fun `muscle detail load is 0 for every muscle when the report has no weeks`() {
        val empty = reportOf(
            weekCount = 0,
            detailWeeks = listOf(detailWeek(weekOf(0), Muscle.CHEST, direct = 9.0)),
        )

        assertEquals(Muscle.entries.toSet(), BodyInsightRules.muscleDetailLoad(empty).keys)
        assertTrue(BodyInsightRules.muscleDetailLoad(empty).values.all { it == 0.0 })
        assertTrue(BodyInsightRules.directDetailLoad(empty).values.all { it == 0.0 })
    }

    @Test
    fun `shade for a muscle is 0 for no sets, 1 under 3 a week, 2 from 3 to under 6 and 3 from 6 a week`() {
        assertEquals(0, BodyInsightRules.shadeMuscle(0.0))
        assertEquals(0, BodyInsightRules.shadeMuscle(-1.0))
        assertEquals(1, BodyInsightRules.shadeMuscle(0.1))
        assertEquals(1, BodyInsightRules.shadeMuscle(2.99))
        assertEquals(2, BodyInsightRules.shadeMuscle(3.0))
        assertEquals(2, BodyInsightRules.shadeMuscle(5.99))
        assertEquals(3, BodyInsightRules.shadeMuscle(6.0))
        assertEquals(3, BodyInsightRules.shadeMuscle(12.0))
    }

    @Test
    fun `the muscle shade thresholds are three and six sets a week`() {
        assertEquals(3.0, BodyInsightRules.MUSCLE_SHADE_2_FROM, 0.0)
        assertEquals(6.0, BodyInsightRules.MUSCLE_SHADE_3_FROM, 0.0)
    }
}

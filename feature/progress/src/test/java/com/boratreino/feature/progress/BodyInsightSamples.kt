package com.boratreino.feature.progress

import com.boratreino.core.model.BalanceStats
import com.boratreino.core.model.IndexPoint
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineEstimate
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.Muscle
import com.boratreino.core.model.MuscleDetailWeek
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.MuscleWeek
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.WeightEntry
import java.time.LocalDate

/** Fixtures for the body cards, on top of [StatsSamples.full]: 13 seven-day blocks from 4 Aug 2026, the last ending 2 Nov. */
internal object BodyInsightSamples {
    private val full = StatsSamples.full

    fun weekStart(index: Int): LocalDate = full.weeks[index].weekStart

    private fun sets(index: Int, group: MuscleGroup, count: Int) =
        MuscleWeek(weekStart(index), group, count, count * 100.0)

    /**
     * Averages over the 13 weeks: legs 10 a week (10.0, the solid shade); chest 8 a week (8.0); back 12 in week 6
     * (0.9); shoulders 3 in week 9 (0.2); arms 1 in week 11 (0.1); core none (0.0).
     */
    val muscleWeeks: List<MuscleWeek> = buildList {
        (0..12).forEach { add(sets(it, MuscleGroup.LEGS, 10)) }
        (0..12).forEach { add(sets(it, MuscleGroup.CHEST, 8)) }
        add(sets(6, MuscleGroup.BACK, 12))
        add(sets(9, MuscleGroup.SHOULDERS, 3))
        add(sets(11, MuscleGroup.ARMS, 1))
    }

    /**
     * The same sets per specific muscle: each leg group muscle 2.0 a week (10 split over five), chest 8.0, back 4 a week
     * in week 6 split over three (0.3), shoulders 3 in week 9 (0.2), each arm muscle 1 in week 11 split over three (0.0).
     */
    private val muscleDetail = StatsSamples.detailOf(muscleWeeks)

    /**
     * The full report with [muscleWeeks]: the body map reads chest 8.0, quadriceps, hamstrings, glutes, calves and hips
     * 2.0 each, upper back, traps and lower back 0.3 each, shoulders 0.2, biceps, triceps and forearms 0.0, abs 0.0.
     */
    val bodyMapReport: StatsReport = full.copy(muscleWeeks = muscleWeeks, detailWeeks = muscleDetail)

    /** The body map figures' description for [bodyMapReport], muscles in display order. */
    const val BODY_MAP_FIGURES =
        "Chest: 8.0 sets a week (8.0 direct), Shoulders: 0.2 sets a week (0.2 direct), " +
            "Biceps: 0.0 sets a week (0.0 direct), Triceps: 0.0 sets a week (0.0 direct), " +
            "Forearms: 0.0 sets a week (0.0 direct), Abs: 0.0 sets a week (0.0 direct), " +
            "Quadriceps: 2.0 sets a week (2.0 direct), Hamstrings: 2.0 sets a week (2.0 direct), " +
            "Glutes: 2.0 sets a week (2.0 direct), Calves: 2.0 sets a week (2.0 direct), " +
            "Hips: 2.0 sets a week (2.0 direct), Upper back: 0.3 sets a week (0.3 direct), " +
            "Traps and neck: 0.3 sets a week (0.3 direct), Lower back: 0.3 sets a week (0.3 direct)"

    /** Averages per block that reach every shade of the map: 6 or more, 3 to 5, under 3, and none (abs). */
    private val shadeSets: Map<Muscle, Double> = mapOf(
        Muscle.CHEST to 8.0,
        Muscle.BICEPS to 6.0,
        Muscle.SHOULDERS to 4.0,
        Muscle.TRICEPS to 3.0,
        Muscle.QUADRICEPS to 1.0,
    )

    /** Thirteen blocks of the same sets per muscle, so each muscle's average is the number above. Abs has none. */
    val shadeReport: StatsReport = full.copy(
        muscleWeeks = emptyList(),
        detailWeeks = (0 until 13).flatMap { i ->
            shadeSets.map { (muscle, sets) -> MuscleDetailWeek(weekStart(i), muscle, sets, 0.0) }
        }.sortedWith(compareBy({ it.weekStart }, { it.muscle })),
    )

    private fun machine(id: Long, name: String, group: MuscleGroup = MuscleGroup.LEGS, kind: MachineKind = MachineKind.WEIGHT_REPS) =
        Machine(id, name, group, kind, 2.5, null, null, false)

    /** Best first. At 80 kg: leg press 1.4, bench press 1.2, lat pulldown 0.8. The pull-up is bodyweight: never ranked. */
    val strengthNow: List<MachineEstimate> = listOf(
        MachineEstimate(machine(1, "Leg press"), 112.0),
        MachineEstimate(machine(2, "Bench press", MuscleGroup.CHEST), 96.0),
        MachineEstimate(machine(3, "Lat pulldown", MuscleGroup.BACK), 64.0),
        MachineEstimate(machine(4, "Pull-up", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS), 200.0),
    )

    /** Ten machines at 1.1 to 2.0 times 80 kg, "Machine 1" to "Machine 10", in that order. */
    val tenMachines: List<MachineEstimate> =
        (1..10).map { MachineEstimate(machine(100L + it, "Machine $it"), 80.0 + it * 8.0) }

    private val strengthPercents = listOf(100.0, 100.0, 101.0, 102.0, 102.0, 103.0, 104.0, 104.0, 105.0, 106.0, 107.0, 107.0, 108.0)

    /** Strength rises from 100 to 108 over the 13 weeks. */
    val strengthIndex: List<IndexPoint> = full.weeks.zip(strengthPercents) { week, percent -> IndexPoint(week.weekStart, percent) }

    /** Newest first, as stored: 80 kg on 10 Aug, 76 kg on 2 Nov (-4.0 kg, -5.0 % against the first week). */
    val weights: List<WeightEntry> = listOf(
        WeightEntry(LocalDate.of(2026, 11, 2), 76.0),
        WeightEntry(LocalDate.of(2026, 8, 10), 80.0),
    )

    val balance = BalanceStats(
        pushSets = 15,
        pullSets = 10,
        upperSets = 24,
        lowerSets = 20,
        quadSets = 6,
        hamstringSets = 3,
        unclassifiedSets = 3,
    )

    /** The Stats page state with a body weight and weight entries. */
    fun state(report: StatsReport = full, bodyWeightKg: Double? = null, weights: List<WeightEntry> = emptyList()) =
        StatsSamples.state(report).copy(bodyWeightKg = bodyWeightKg, weights = weights)
}

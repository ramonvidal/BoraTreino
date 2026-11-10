package com.boratreino.core.model

import java.time.LocalDate

/** Summaries that relate training to the body: relative strength, body-weight index, body map shades. Pure functions. */
object BodyInsightRules {
    /** Average weekly effective sets at which a muscle group's shade steps up (under 5, 5 up to 10, 10+). */
    private const val SHADE_2_FROM = 5.0
    private const val SHADE_3_FROM = 10.0

    /** Weighted machines only; ratio = estimate / body weight, best first. Empty when [bodyWeightKg] <= 0. */
    fun relativeStrength(
        estimates: List<MachineEstimate>,
        bodyWeightKg: Double,
        limit: Int = 8,
    ): List<Pair<MachineEstimate, Double>> {
        if (bodyWeightKg <= 0.0) return emptyList()
        return estimates
            .filter { it.machine.kind == MachineKind.WEIGHT_REPS }
            .map { it to it.estimate1rm / bodyWeightKg }
            .sortedByDescending { it.second }
            .take(limit.coerceAtLeast(0))
    }

    /**
     * Body weight per week (last entry on or before the end of the week, carried forward), indexed to 100.0 at the first
     * produced point. Empty unless at least two distinct entry dates fall in or before the range.
     */
    fun weightIndex(weights: List<WeightEntry>, weekStarts: List<LocalDate>): List<IndexPoint> {
        if (weekStarts.isEmpty()) return emptyList()
        val sorted = weights.sortedBy { it.date }
        val lastEnd = weekStarts.maxOrNull()!!.plusDays(6)
        if (sorted.filter { !it.date.isAfter(lastEnd) }.map { it.date }.distinct().size < 2) return emptyList()
        val values = weekStarts.sorted().mapNotNull { start ->
            val end = start.plusDays(6)
            sorted.lastOrNull { !it.date.isAfter(end) }?.let { start to it.kg }
        }
        val baseline = values.firstOrNull()?.second ?: return emptyList()
        if (baseline <= 0.0) return emptyList()
        return values.mapIndexed { i, (week, kg) -> IndexPoint(week, if (i == 0) 100.0 else kg / baseline * 100.0) }
    }

    /** Working sets per group over the last [lastWeeks] weeks of [weeks]; all groups present, 0 when none. */
    fun bodyMap(muscleWeeks: List<MuscleWeek>, weeks: List<WeekStats>, lastWeeks: Int = 4): Map<MuscleGroup, Int> =
        bodyMap(muscleWeeks, weeks.map { it.weekStart }.sorted().takeLast(lastWeeks.coerceAtLeast(0)).toSet())

    /** Same, over the last [lastWeeks] distinct week starts present in [muscleWeeks]. */
    fun bodyMap(muscleWeeks: List<MuscleWeek>, lastWeeks: Int = 4): Map<MuscleGroup, Int> =
        bodyMap(muscleWeeks, muscleWeeks.map { it.weekStart }.distinct().sorted().takeLast(lastWeeks.coerceAtLeast(0)).toSet())

    private fun bodyMap(muscleWeeks: List<MuscleWeek>, starts: Set<LocalDate>): Map<MuscleGroup, Int> {
        val sets = muscleWeeks.filter { it.weekStart in starts }
            .groupBy { it.muscleGroup }
            .mapValues { (_, weeks) -> weeks.sumOf { it.workingSets } }
        return MuscleGroup.entries.associateWith { sets[it] ?: 0 }
    }

    /**
     * Average effective sets per week over all weeks of [report]: direct + [StatsRules.INDIRECT_WEIGHT] x indirect.
     * All groups present; 0.0 when the report has no weeks. Cardio stays 0.0.
     */
    fun muscleLoad(report: StatsReport): Map<MuscleGroup, Double> = load(report, StatsRules.INDIRECT_WEIGHT)

    /** Same as [muscleLoad] but direct sets only. */
    fun directLoad(report: StatsReport): Map<MuscleGroup, Double> = load(report, 0.0)

    private fun load(report: StatsReport, indirectWeight: Double): Map<MuscleGroup, Double> {
        val weekCount = report.weeks.size
        val direct = report.muscleWeeks.groupBy { it.muscleGroup }.mapValues { (_, w) -> w.sumOf { it.workingSets } }
        val indirect = report.indirectWeeks.groupBy { it.muscleGroup }.mapValues { (_, w) -> w.sumOf { it.sets } }
        return MuscleGroup.entries.associateWith { group ->
            if (weekCount == 0 || group == MuscleGroup.CARDIO) {
                0.0
            } else {
                ((direct[group] ?: 0) + indirectWeight * (indirect[group] ?: 0)) / weekCount
            }
        }
    }

    /** 0 for none, 1 under 5 sets a week, 2 for 5 up to 10, 3 for 10 or more. */
    fun shade(setsPerWeek: Double): Int = when {
        setsPerWeek <= 0.0 -> 0
        setsPerWeek < SHADE_2_FROM -> 1
        setsPerWeek < SHADE_3_FROM -> 2
        else -> 3
    }
}

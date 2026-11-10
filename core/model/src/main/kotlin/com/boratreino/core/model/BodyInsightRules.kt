package com.boratreino.core.model

import java.time.LocalDate

/** Summaries that relate training to the body: relative strength, body-weight index, body map shades. Pure functions. */
object BodyInsightRules {
    /** Working-set counts at which a muscle group's shade steps up (1..9, 10..19, 20+). */
    private const val SHADE_2_FROM = 10
    private const val SHADE_3_FROM = 20

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

    /** 0 for none, 1 for 1..9 sets, 2 for 10..19, 3 for 20 or more. */
    fun shade(sets: Int): Int = when {
        sets <= 0 -> 0
        sets < SHADE_2_FROM -> 1
        sets < SHADE_3_FROM -> 2
        else -> 3
    }
}

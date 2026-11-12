package com.boratreino.core.model

/** What a [Highlight] says. */
enum class HighlightKind { MUSCLE_LOAD, MUSCLE_BEHIND, STRENGTH_GAIN, GOAL_STREAK }

/** The Stats sub-page that holds the cards behind a highlight. */
enum class StatsPage { BODY, STRENGTH, HABIT }

/** One sentence the Progress tab leads with. [target] is the Stats page that holds the card behind it. */
data class Highlight(
    val kind: HighlightKind,
    val muscleGroup: MuscleGroup? = null,
    val machine: Machine? = null,
    /** MUSCLE_LOAD: average effective sets a week; STRENGTH_GAIN: percent change; GOAL_STREAK: weeks met. */
    val value: Double = 0.0,
    /** MUSCLE_BEHIND: days since the group was trained. */
    val days: Int = 0,
    val target: StatsPage,
)

/** Picks the few facts worth leading the Stats tab with. Pure and deterministic; never throws. */
object StatsHighlights {
    /** A group counts as "behind" once it has not been trained for this many days. */
    const val BEHIND_FROM_DAYS = 7

    /**
     * Up to [max] highlights, in this order (each one only when it exists):
     * STRENGTH_GAIN, MUSCLE_LOAD, MUSCLE_BEHIND, GOAL_STREAK.
     * Empty when nothing qualifies (for instance an empty period) or [max] <= 0.
     */
    fun of(report: StatsReport, max: Int = 3): List<Highlight> =
        listOfNotNull(strengthGain(report), muscleLoad(report), muscleBehind(report), goalStreak(report))
            .take(max.coerceAtLeast(0))

    /**
     * STRENGTH_GAIN: the first entry of [StatsReport.strength] (best change first) whose changePercent is above 0.
     * value = that percent; machine is set; target STRENGTH.
     */
    private fun strengthGain(report: StatsReport): Highlight? =
        report.strength.firstOrNull { it.changePercent > 0.0 }?.let {
            Highlight(
                kind = HighlightKind.STRENGTH_GAIN,
                machine = it.machine,
                value = it.changePercent,
                target = StatsPage.STRENGTH,
            )
        }

    /**
     * MUSCLE_LOAD: the non-cardio group with the highest [BodyInsightRules.muscleLoad] (must be above 0;
     * ties go to the earlier group in enum order). value = average effective sets a week; target BODY.
     */
    private fun muscleLoad(report: StatsReport): Highlight? =
        BodyInsightRules.muscleLoad(report).entries
            .filter { it.key != MuscleGroup.CARDIO && it.value > 0.0 }
            .maxByOrNull { it.value }
            ?.let {
                Highlight(
                    kind = HighlightKind.MUSCLE_LOAD,
                    muscleGroup = it.key,
                    value = it.value,
                    target = StatsPage.BODY,
                )
            }

    /**
     * MUSCLE_BEHIND: among non-cardio groups trained before (daysSince not null), the one with the largest
     * [MuscleRecency.daysSince], when that is at least [BEHIND_FROM_DAYS]. Never-trained groups are not mentioned.
     * Ties go to the earlier group in enum order. days = daysSince; target BODY.
     */
    private fun muscleBehind(report: StatsReport): Highlight? =
        report.recency
            .filter { it.muscleGroup != MuscleGroup.CARDIO && it.daysSince != null }
            .maxByOrNull { it.daysSince ?: 0 }
            ?.takeIf { (it.daysSince ?: 0) >= BEHIND_FROM_DAYS }
            ?.let {
                Highlight(
                    kind = HighlightKind.MUSCLE_BEHIND,
                    muscleGroup = it.muscleGroup,
                    days = it.daysSince ?: 0,
                    target = StatsPage.BODY,
                )
            }

    /**
     * GOAL_STREAK: when [Consistency.weeksMetGoal] is above 0. value = weeksMetGoal; target HABIT.
     */
    private fun goalStreak(report: StatsReport): Highlight? =
        report.consistency.weeksMetGoal.takeIf { it > 0 }?.let {
            Highlight(kind = HighlightKind.GOAL_STREAK, value = it.toDouble(), target = StatsPage.HABIT)
        }
}

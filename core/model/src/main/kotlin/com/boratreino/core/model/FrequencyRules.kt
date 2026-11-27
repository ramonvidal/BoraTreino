package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/** Distinct training days in the calendar week starting [weekStart]. */
data class WeekCount(val weekStart: LocalDate, val days: Int, val isCurrent: Boolean)

data class FrequencySummary(
    val thisWeek: Int,
    /** Clamped to [WeekGoalRules.MIN_GOAL]..[WeekGoalRules.MAX_GOAL]. */
    val goal: Int,
    /** The last [FrequencyRules.SHOWN_WEEKS] calendar weeks including the current one, oldest first. */
    val weeks: List<WeekCount>,
    val streak: Int,
    /** Passed through; null hides "next: ...". */
    val next: ProjectedWorkout?,
)

object FrequencyRules {
    const val SHOWN_WEEKS = 4

    /** The [count] calendar weeks ending with the current one, oldest first; isCurrent only on the last. */
    fun weekCounts(
        trainingDates: Set<LocalDate>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        count: Int,
    ): List<WeekCount> {
        val current = WeekGoalRules.weekStart(today, firstDayOfWeek)
        return (count - 1 downTo 0).map { back ->
            val start = current.minusWeeks(back.toLong())
            WeekCount(start, WeekGoalRules.daysTrained(trainingDates, start, firstDayOfWeek), isCurrent = back == 0)
        }
    }

    fun summary(
        trainingDates: Set<LocalDate>,
        goal: Int,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        next: ProjectedWorkout?,
    ): FrequencySummary {
        val g = goal.coerceIn(WeekGoalRules.MIN_GOAL, WeekGoalRules.MAX_GOAL)
        return FrequencySummary(
            thisWeek = WeekGoalRules.daysTrained(trainingDates, today, firstDayOfWeek),
            goal = g,
            weeks = weekCounts(trainingDates, today, firstDayOfWeek, SHOWN_WEEKS),
            streak = WeekGoalRules.streak(trainingDates, g, today, firstDayOfWeek),
            next = next,
        )
    }
}

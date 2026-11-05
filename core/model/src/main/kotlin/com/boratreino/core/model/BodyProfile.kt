package com.boratreino.core.model

import java.time.LocalDate

enum class Sex { MALE, FEMALE }

/** What the owner typed in Settings; only used to estimate the day's energy need. */
data class BodyProfile(
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Int,
    val weightKg: Double,
    /** -30..+30; use [coerced] before relying on it. */
    val goalAdjustmentPercent: Int = 0,
    /** When set it replaces the formula. */
    val manualBmrKcal: Int? = null,
) {
    /** The same profile with the goal clamped to [MIN_GOAL_PERCENT]..[MAX_GOAL_PERCENT]. */
    fun coerced(): BodyProfile =
        copy(goalAdjustmentPercent = goalAdjustmentPercent.coerceIn(MIN_GOAL_PERCENT, MAX_GOAL_PERCENT))

    companion object {
        const val MIN_GOAL_PERCENT = -30
        const val MAX_GOAL_PERCENT = 30
    }
}

data class WeightEntry(val date: LocalDate, val kg: Double)

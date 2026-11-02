package com.boratreino.core.settings

import kotlinx.coroutines.flow.Flow

/** This phone's training choices. */
interface TrainingSettings {
    /** Days to train per week; default WeekGoalRules.DEFAULT_GOAL, always within 1..7. */
    val weeklyGoal: Flow<Int>

    /** Clamped to 1..7. */
    suspend fun setWeeklyGoal(goal: Int)
}

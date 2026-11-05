package com.boratreino.core.settings

import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import kotlinx.coroutines.flow.Flow

/** This phone's training choices. */
interface TrainingSettings {
    /** Days to train per week; default WeekGoalRules.DEFAULT_GOAL, always within 1..7. */
    val weeklyGoal: Flow<Int>

    /** Clamped to 1..7. */
    suspend fun setWeeklyGoal(goal: Int)

    /** Training reminders; off by default. */
    val reminders: Flow<ReminderSettings>

    suspend fun setReminders(settings: ReminderSettings)

    /** Rest timer; on by default. */
    val restTimer: Flow<RestTimerSettings>

    /** The default duration is clamped to RestTimerRules.MIN_SECONDS..MAX_SECONDS. */
    suspend fun setRestTimer(settings: RestTimerSettings)

    /** Offer warm-up sets on the machine screen; on by default. */
    val warmupCalculator: Flow<Boolean>

    suspend fun setWarmupCalculator(enabled: Boolean)
}

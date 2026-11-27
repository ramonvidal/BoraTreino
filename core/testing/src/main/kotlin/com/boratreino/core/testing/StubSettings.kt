package com.boratreino.core.testing

import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.settings.BodySettings
import com.boratreino.core.settings.HealthSettings
import com.boratreino.core.settings.TrainingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * [TrainingSettings] for tests, fixed at the given values (the app's defaults otherwise, with a weekly goal of
 * [weeklyGoal]). The setters change nothing: override them (and the flows) for a test that checks a write.
 */
open class StubTrainingSettings(
    weeklyGoal: Int = 4,
    reminders: ReminderSettings = ReminderSettings(),
    restTimer: RestTimerSettings = RestTimerSettings(),
    warmupCalculator: Boolean = true,
) : TrainingSettings {
    override val weeklyGoal: Flow<Int> = MutableStateFlow(weeklyGoal)
    override suspend fun setWeeklyGoal(goal: Int) = Unit
    override val reminders: Flow<ReminderSettings> = MutableStateFlow(reminders)
    override suspend fun setReminders(settings: ReminderSettings) = Unit
    override val restTimer: Flow<RestTimerSettings> = MutableStateFlow(restTimer)
    override suspend fun setRestTimer(settings: RestTimerSettings) = Unit
    override val warmupCalculator: Flow<Boolean> = MutableStateFlow(warmupCalculator)
    override suspend fun setWarmupCalculator(enabled: Boolean) = Unit
}

/** [HealthSettings] for tests, fixed at the given values (off, nothing synced by default); the setters change nothing. */
open class StubHealthSettings(
    enabled: Boolean = false,
    syncedSessionIds: Set<Long> = emptySet(),
) : HealthSettings {
    override val enabled: Flow<Boolean> = MutableStateFlow(enabled)
    override suspend fun setEnabled(enabled: Boolean) = Unit
    override val syncedSessionIds: Flow<Set<Long>> = MutableStateFlow(syncedSessionIds)
    override suspend fun setSyncedSessionIds(ids: Set<Long>) = Unit
}

/**
 * [BodySettings] for tests, fixed at the given values (no profile, no weights, energy estimate shown by default);
 * the setters change nothing.
 */
open class StubBodySettings(
    profile: BodyProfile? = null,
    weights: List<WeightEntry> = emptyList(),
    showEnergyEstimate: Boolean = true,
) : BodySettings {
    override val profile: Flow<BodyProfile?> = MutableStateFlow(profile)
    override suspend fun setProfile(profile: BodyProfile) = Unit
    override val weights: Flow<List<WeightEntry>> = MutableStateFlow(weights)
    override suspend fun addWeight(date: LocalDate, kg: Double) = Unit
    override val showEnergyEstimate: Flow<Boolean> = MutableStateFlow(showEnergyEstimate)
    override suspend fun setShowEnergyEstimate(show: Boolean) = Unit
}

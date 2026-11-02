package com.boratreino.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.boratreino.core.model.WeekGoalRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

internal class DataStoreTrainingSettings(
    private val dataStore: DataStore<Preferences>,
) : TrainingSettings {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val weeklyGoal: Flow<Int> = preferences
        .map { prefs -> clamp(prefs[WEEKLY_GOAL] ?: WeekGoalRules.DEFAULT_GOAL) }
        .distinctUntilChanged()

    override suspend fun setWeeklyGoal(goal: Int) {
        dataStore.edit { prefs -> prefs[WEEKLY_GOAL] = clamp(goal) }
    }

    private fun clamp(goal: Int) = goal.coerceIn(WeekGoalRules.MIN_GOAL, WeekGoalRules.MAX_GOAL)

    private companion object {
        val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
    }
}

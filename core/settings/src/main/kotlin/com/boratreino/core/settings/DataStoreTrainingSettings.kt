package com.boratreino.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.WeekGoalRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

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

    override val reminders: Flow<ReminderSettings> = preferences
        .map { prefs ->
            val defaults = ReminderSettings()
            ReminderSettings(
                enabled = prefs[REMINDERS_ENABLED] ?: defaults.enabled,
                // A stored empty string means "no weekdays"; a missing key means the default days.
                weekdays = prefs[REMINDER_WEEKDAYS]?.let(::parseWeekdays) ?: defaults.weekdays,
                time = prefs[REMINDER_MINUTE_OF_DAY]
                    ?.let { LocalTime.of(it.coerceIn(0, MINUTES_PER_DAY - 1) / 60, it.coerceIn(0, MINUTES_PER_DAY - 1) % 60) }
                    ?: defaults.time,
            )
        }
        .distinctUntilChanged()

    override suspend fun setReminders(settings: ReminderSettings) {
        dataStore.edit { prefs ->
            prefs[REMINDERS_ENABLED] = settings.enabled
            prefs[REMINDER_WEEKDAYS] = settings.weekdays.sorted().joinToString(",") { it.name }
            prefs[REMINDER_MINUTE_OF_DAY] = settings.time.hour * 60 + settings.time.minute
        }
    }

    override val restTimer: Flow<RestTimerSettings> = preferences
        .map { prefs ->
            val defaults = RestTimerSettings()
            RestTimerSettings(
                enabled = prefs[REST_ENABLED] ?: defaults.enabled,
                defaultSeconds = clampRest(prefs[REST_DEFAULT_SECONDS] ?: defaults.defaultSeconds),
                beep = prefs[REST_BEEP] ?: defaults.beep,
            )
        }
        .distinctUntilChanged()

    override suspend fun setRestTimer(settings: RestTimerSettings) {
        dataStore.edit { prefs ->
            prefs[REST_ENABLED] = settings.enabled
            prefs[REST_DEFAULT_SECONDS] = clampRest(settings.defaultSeconds)
            prefs[REST_BEEP] = settings.beep
        }
    }

    private fun clampRest(seconds: Int) = seconds.coerceIn(RestTimerRules.MIN_SECONDS, RestTimerRules.MAX_SECONDS)

    private fun parseWeekdays(text: String): Set<DayOfWeek> =
        text.split(',').mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }.toSet()

    private fun clamp(goal: Int) = goal.coerceIn(WeekGoalRules.MIN_GOAL, WeekGoalRules.MAX_GOAL)

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_WEEKDAYS = stringPreferencesKey("reminder_weekdays")
        val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val REST_ENABLED = booleanPreferencesKey("rest_timer_enabled")
        val REST_BEEP = booleanPreferencesKey("rest_timer_beep")
        val REST_DEFAULT_SECONDS = intPreferencesKey("rest_timer_default_seconds")
    }
}

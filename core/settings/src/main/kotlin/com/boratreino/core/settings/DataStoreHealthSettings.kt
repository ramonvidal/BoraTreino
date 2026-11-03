package com.boratreino.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

internal class DataStoreHealthSettings(
    private val dataStore: DataStore<Preferences>,
) : HealthSettings {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val enabled: Flow<Boolean> = preferences
        .map { prefs -> prefs[HEALTH_ENABLED] ?: false }
        .distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[HEALTH_ENABLED] = enabled }
    }

    override val syncedSessionIds: Flow<Set<Long>> = preferences
        .map { prefs -> prefs[HEALTH_SYNCED_IDS]?.let(::parseIds).orEmpty() }
        .distinctUntilChanged()

    override suspend fun setSyncedSessionIds(ids: Set<Long>) {
        dataStore.edit { prefs -> prefs[HEALTH_SYNCED_IDS] = ids.sorted().joinToString(",") }
    }

    private fun parseIds(text: String): Set<Long> =
        text.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    private companion object {
        val HEALTH_ENABLED = booleanPreferencesKey("health_connect_enabled")
        val HEALTH_SYNCED_IDS = stringPreferencesKey("health_connect_synced_session_ids")
    }
}

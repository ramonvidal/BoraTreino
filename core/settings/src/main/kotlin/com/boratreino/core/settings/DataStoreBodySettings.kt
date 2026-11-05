package com.boratreino.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.Sex
import com.boratreino.core.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

internal class DataStoreBodySettings(
    private val dataStore: DataStore<Preferences>,
) : BodySettings {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val profile: Flow<BodyProfile?> = preferences
        .map { prefs -> readProfile(prefs) }
        .distinctUntilChanged()

    override suspend fun setProfile(profile: BodyProfile) {
        val p = profile.coerced()
        dataStore.edit { prefs ->
            prefs[BODY_SEX] = p.sex.name
            prefs[BODY_BIRTH_YEAR] = p.birthYear
            prefs[BODY_HEIGHT_CM] = p.heightCm
            prefs[BODY_WEIGHT_KG] = p.weightKg
            prefs[BODY_GOAL_PERCENT] = p.goalAdjustmentPercent
            val manual = p.manualBmrKcal
            if (manual != null) prefs[BODY_MANUAL_BMR] = manual else prefs.remove(BODY_MANUAL_BMR)
        }
    }

    override val weights: Flow<List<WeightEntry>> = preferences
        .map { prefs -> prefs[BODY_WEIGHTS]?.let(::parseWeights).orEmpty() }
        .distinctUntilChanged()

    override suspend fun addWeight(date: LocalDate, kg: Double) {
        dataStore.edit { prefs ->
            val updated = (prefs[BODY_WEIGHTS]?.let(::parseWeights).orEmpty().filterNot { it.date == date } +
                WeightEntry(date, kg))
                .sortedByDescending { it.date }
                .take(MAX_WEIGHTS)
            prefs[BODY_WEIGHTS] = updated.joinToString(";") { "${it.date}:${it.kg}" }
            if (readProfile(prefs) != null) prefs[BODY_WEIGHT_KG] = kg
        }
    }

    override val showEnergyEstimate: Flow<Boolean> = preferences
        .map { prefs -> prefs[BODY_SHOW_ENERGY] ?: true }
        .distinctUntilChanged()

    override suspend fun setShowEnergyEstimate(show: Boolean) {
        dataStore.edit { prefs -> prefs[BODY_SHOW_ENERGY] = show }
    }

    private fun readProfile(prefs: Preferences): BodyProfile? {
        val sex = prefs[BODY_SEX]?.let { name -> Sex.values().firstOrNull { it.name == name } } ?: return null
        return BodyProfile(
            sex = sex,
            birthYear = prefs[BODY_BIRTH_YEAR] ?: return null,
            heightCm = prefs[BODY_HEIGHT_CM] ?: return null,
            weightKg = prefs[BODY_WEIGHT_KG] ?: return null,
            goalAdjustmentPercent = prefs[BODY_GOAL_PERCENT] ?: 0,
            manualBmrKcal = prefs[BODY_MANUAL_BMR],
        ).coerced()
    }

    private fun parseWeights(text: String): List<WeightEntry> =
        text.split(';').mapNotNull { part ->
            val pieces = part.split(':')
            val date = pieces.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val kg = pieces.getOrNull(1)?.toDoubleOrNull()
            if (date != null && kg != null) WeightEntry(date, kg) else null
        }.sortedByDescending { it.date }.take(MAX_WEIGHTS)

    private companion object {
        const val MAX_WEIGHTS = 200
        val BODY_SEX = stringPreferencesKey("body_sex")
        val BODY_BIRTH_YEAR = intPreferencesKey("body_birth_year")
        val BODY_HEIGHT_CM = intPreferencesKey("body_height_cm")
        val BODY_WEIGHT_KG = doublePreferencesKey("body_weight_kg")
        val BODY_GOAL_PERCENT = intPreferencesKey("body_goal_percent")
        val BODY_MANUAL_BMR = intPreferencesKey("body_manual_bmr_kcal")
        val BODY_WEIGHTS = stringPreferencesKey("body_weights")
        val BODY_SHOW_ENERGY = booleanPreferencesKey("body_show_energy_estimate")
    }
}

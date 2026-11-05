package com.boratreino.feature.settings

import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.settings.BodySettings
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Body settings on plain state flows that also record what was written. */
internal class FakeBodySettings : BodySettings {
    val profileFlow = MutableStateFlow<BodyProfile?>(null)
    val weightsFlow = MutableStateFlow<List<WeightEntry>>(emptyList())
    val showFlow = MutableStateFlow(true)

    /** Every profile passed to [setProfile], in order. */
    val savedProfiles = mutableListOf<BodyProfile>()

    /** Every call of [addWeight], in order. */
    val addedWeights = mutableListOf<Pair<LocalDate, Double>>()

    override val profile: Flow<BodyProfile?> = profileFlow
    override val weights: Flow<List<WeightEntry>> = weightsFlow
    override val showEnergyEstimate: Flow<Boolean> = showFlow

    override suspend fun setProfile(profile: BodyProfile) {
        savedProfiles += profile
        profileFlow.value = profile
    }

    override suspend fun addWeight(date: LocalDate, kg: Double) {
        addedWeights += date to kg
        weightsFlow.value = (weightsFlow.value.filterNot { it.date == date } + WeightEntry(date, kg)).sortedByDescending { it.date }
        profileFlow.value = profileFlow.value?.copy(weightKg = kg)
    }

    override suspend fun setShowEnergyEstimate(show: Boolean) {
        showFlow.value = show
    }
}

package com.boratreino.core.settings

import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The owner's body data. Stays on this phone and is not part of the backup. */
interface BodySettings {
    /** Null until the owner has filled it in. */
    val profile: Flow<BodyProfile?>

    suspend fun setProfile(profile: BodyProfile)

    /** Newest first, at most 200. */
    val weights: Flow<List<WeightEntry>>

    /** Replaces an entry of the same date; also updates the profile's weight when one exists. */
    suspend fun addWeight(date: LocalDate, kg: Double)

    /** Show the daily energy estimate; on by default. */
    val showEnergyEstimate: Flow<Boolean>

    suspend fun setShowEnergyEstimate(show: Boolean)
}

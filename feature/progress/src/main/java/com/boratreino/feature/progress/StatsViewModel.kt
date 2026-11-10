package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.StatsRepository
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.settings.BodySettings
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val PERIOD_KEY = "statsPeriod"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    stats: StatsRepository,
    settings: TrainingSettings,
    body: BodySettings,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // Kept as the enum's name in the saved state so the choice survives rotation and process death.
    private val period: StateFlow<StatsPeriod> = savedState.getStateFlow(PERIOD_KEY, StatsPeriod.THREE_MONTHS.name)
        .map { name -> StatsPeriod.entries.firstOrNull { it.name == name } ?: StatsPeriod.THREE_MONTHS }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsPeriod.THREE_MONTHS)

    /** Latest weight entry's kg, else the profile's weight, plus the entries as stored. */
    private val bodyWeight: Flow<Pair<Double?, List<WeightEntry>>> =
        combine(body.profile, body.weights) { profile, weights ->
            (weights.firstOrNull()?.kg ?: profile?.weightKg) to weights
        }

    internal val state: StateFlow<StatsUiState> = combine(period, settings.weeklyGoal, bodyWeight) { period, goal, bodyPair ->
        Triple(period, goal, bodyPair)
    }
        .flatMapLatest { (period, goal, bodyPair) ->
            stats.observeStats(period, goal).map { report ->
                StatsUiState(
                    loaded = true,
                    period = period,
                    report = report,
                    weeklyGoal = goal,
                    bodyWeightKg = bodyPair.first,
                    weights = bodyPair.second,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    internal fun setPeriod(period: StatsPeriod) {
        savedState[PERIOD_KEY] = period.name
    }
}

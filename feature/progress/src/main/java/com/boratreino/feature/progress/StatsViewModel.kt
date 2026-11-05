package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.StatsRepository
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // Kept as the enum's name in the saved state so the choice survives rotation and process death.
    private val period: StateFlow<StatsPeriod> = savedState.getStateFlow(PERIOD_KEY, StatsPeriod.THREE_MONTHS.name)
        .map { name -> StatsPeriod.entries.firstOrNull { it.name == name } ?: StatsPeriod.THREE_MONTHS }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsPeriod.THREE_MONTHS)

    internal val state: StateFlow<StatsUiState> = combine(period, settings.weeklyGoal) { period, goal -> period to goal }
        .flatMapLatest { (period, goal) ->
            stats.observeStats(period, goal).map { report ->
                StatsUiState(loaded = true, period = period, report = report, weeklyGoal = goal)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    internal fun setPeriod(period: StatsPeriod) {
        savedState[PERIOD_KEY] = period.name
    }
}

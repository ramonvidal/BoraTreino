package com.boratreino.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.ProgressRepository
import com.boratreino.core.model.CardioWeek
import com.boratreino.core.model.Improvement
import com.boratreino.core.model.Machine
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.model.ProgressRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One machine in the Machines list. [trend] holds the last up-to-12 session values, oldest first. */
internal data class MachineRow(
    val machine: Machine,
    val metric: ProgressMetric,
    val best: Double,
    val lastTrained: LocalDate,
    val trend: List<Double>,
)

/** One machine in "Most improved". */
internal data class ImprovedRow(
    val machine: Machine,
    val metric: ProgressMetric,
    val improvement: Improvement,
    val trend: List<Double>,
)

/** Everything the Progress front page shows. [loaded] false draws a blank body. */
internal data class ProgressUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.of(2000, 1, 1),
    val trainingsThisMonth: Int = 0,
    val recordsThisMonth: Int = 0,
    val improved: List<ImprovedRow> = emptyList(),
    val machines: List<MachineRow> = emptyList(),
    /** This week's cardio totals; null when there is nothing to show. */
    val cardioThisWeek: CardioWeek? = null,
)

internal const val MOST_IMPROVED_COUNT = 5

/** Machines that improved in the last 30 days: biggest percent first, then larger delta, then name; top five. */
internal fun buildProgressState(overview: ProgressOverview, today: LocalDate): ProgressUiState {
    val improved = overview.machines
        .mapNotNull { progress ->
            ProgressRules.improvement(progress.points, today)?.let { ImprovedRow(progress.machine, progress.metric, it, trendOf(progress.points.map { p -> p.value })) }
        }
        .sortedWith(
            compareByDescending<ImprovedRow> { it.improvement.percent }
                .thenByDescending { it.improvement.delta }
                .thenBy { it.machine.name },
        )
        .take(MOST_IMPROVED_COUNT)
    val machines = overview.machines
        .filter { it.points.isNotEmpty() }
        .map { progress ->
            MachineRow(
                machine = progress.machine,
                metric = progress.metric,
                best = progress.points.maxOf { it.value },
                lastTrained = progress.points.last().date,
                trend = trendOf(progress.points.map { it.value }),
            )
        }
    return ProgressUiState(
        loaded = true,
        today = today,
        trainingsThisMonth = overview.trainingsThisMonth,
        recordsThisMonth = overview.recordsThisMonth,
        improved = improved,
        machines = machines,
        cardioThisWeek = overview.cardioThisWeek?.takeIf { it.minutes > 0 || it.calories > 0 },
    )
}

private fun trendOf(values: List<Double>): List<Double> = recentValues(values)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    repository: ProgressRepository,
    private val clock: Clock,
) : ViewModel() {

    internal val state: StateFlow<ProgressUiState> = repository.observeOverview()
        .map { buildProgressState(it, LocalDate.now(clock)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}

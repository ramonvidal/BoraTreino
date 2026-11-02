package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.ProgressRepository
import com.boratreino.core.model.ChartRules
import com.boratreino.core.model.ChartScale
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.model.ProgressRules
import com.boratreino.core.model.SessionPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Everything the machine detail shows. [points] are the ones inside [range], ascending; [best] is
 * the all-time best; [change] is over [range] (null with fewer than 2 points). [axisStart]..[axisEnd]
 * is the time window the chart covers. [found] false means the machine does not exist.
 */
internal data class MachineProgressUiState(
    val loaded: Boolean = false,
    val found: Boolean = true,
    val name: String = "",
    val metric: ProgressMetric = ProgressMetric.WEIGHT,
    val range: ProgressRange = ProgressRange.THREE_MONTHS,
    val today: LocalDate = LocalDate.of(2000, 1, 1),
    val best: SessionPoint? = null,
    val change: Double? = null,
    val points: List<SessionPoint> = emptyList(),
    val scale: ChartScale = ChartScale(0.0, 1.0, listOf(0.0, 1.0)),
    val axisStart: LocalDate = LocalDate.of(2000, 1, 1),
    val axisEnd: LocalDate = LocalDate.of(2000, 1, 1),
    val selectedIndex: Int? = null,
    val showAll: Boolean = false,
)

/** The detail state for [progress] (null = machine missing) in [range] as of [today]. */
internal fun buildMachineState(
    progress: MachineProgress?,
    range: ProgressRange,
    today: LocalDate,
    selected: Int?,
    showAll: Boolean,
): MachineProgressUiState {
    if (progress == null) return MachineProgressUiState(loaded = true, found = false, range = range, today = today)
    val inRange = ProgressRules.inRange(progress.points, range, today)
    val (start, end) = axisWindow(range, today, inRange.firstOrNull()?.date)
    val scale = if (inRange.isEmpty()) {
        MachineProgressUiState().scale
    } else {
        ChartRules.niceScale(inRange.minOf { it.value }, inRange.maxOf { it.value })
    }
    return MachineProgressUiState(
        loaded = true,
        found = true,
        name = progress.machine.name,
        metric = progress.metric,
        range = range,
        today = today,
        best = ProgressRules.best(progress.points),
        change = ProgressRules.change(inRange),
        points = inRange,
        scale = scale,
        axisStart = start,
        axisEnd = end,
        selectedIndex = selected?.takeIf { it in inRange.indices },
        showAll = showAll,
    )
}

@HiltViewModel
class MachineProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ProgressRepository,
    private val clock: Clock,
) : ViewModel() {

    private val machineId: Long = checkNotNull(savedStateHandle.get<Long>(MACHINE_ID_ARG)) { "machineId missing" }

    private val range = MutableStateFlow(ProgressRange.THREE_MONTHS)
    private val selected = MutableStateFlow<Int?>(null)
    private val showAll = MutableStateFlow(false)

    internal val state: StateFlow<MachineProgressUiState> = combine(
        repository.observeMachineProgress(machineId),
        range,
        selected,
        showAll,
    ) { progress, range, selected, showAll ->
        buildMachineState(progress, range, LocalDate.now(clock), selected, showAll)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachineProgressUiState())

    internal fun selectRange(newRange: ProgressRange) {
        selected.value = null
        range.value = newRange
    }

    /** The chart's tooltip point; null clears it. */
    internal fun selectPoint(index: Int?) {
        selected.value = index
    }

    internal fun showAllSessions() {
        showAll.value = true
    }
}

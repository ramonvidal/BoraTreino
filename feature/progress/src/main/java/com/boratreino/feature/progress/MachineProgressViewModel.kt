package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.ProgressRepository
import com.boratreino.core.model.ChartRules
import com.boratreino.core.model.CardioWeek
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
import kotlinx.coroutines.flow.Flow
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
    /** The last 12 weeks, oldest first; only cardio machines have them. */
    val weeks: List<CardioWeek> = emptyList(),
    /** The machine has at least one set the estimated 1RM applies to. */
    val oneRmAvailable: Boolean = false,
    /** The chart shows the estimated 1RM (only true when [oneRmAvailable]); [metric] is then ONE_RM. */
    val showOneRm: Boolean = false,
    /** The best estimated 1RM, all-time like [best]; null without eligible sets. */
    val oneRmBest: SessionPoint? = null,
    /** Cardio metrics that have data, in chip order; empty for other machines. Chips show only with two or more. */
    val cardioMetrics: List<ProgressMetric> = emptyList(),
    /** The all-time best session per cardio metric in [cardioMetrics] (speed, power, IF, TSS, intensity, distance, time), same order. */
    val cardioRecords: List<CardioRecord> = emptyList(),
)

/** The best session of one cardio [metric], all-time. */
internal data class CardioRecord(val metric: ProgressMetric, val point: SessionPoint)

/** The detail state for [progress] (null = machine missing) in [range] as of [today]. */
internal fun buildMachineState(
    progress: MachineProgress?,
    range: ProgressRange,
    today: LocalDate,
    selected: Int?,
    showAll: Boolean,
    showOneRm: Boolean = false,
    /** The saved chip choice as an enum name (cardio machines only); unknown, missing or without data falls back to the default. */
    cardioMetric: String? = null,
): MachineProgressUiState {
    if (progress == null) return MachineProgressUiState(loaded = true, found = false, range = range, today = today)
    val oneRmAvailable = progress.oneRmPoints.isNotEmpty()
    val useOneRm = showOneRm && oneRmAvailable
    val cardio = progress.machine.isCardio()
    val cardioMetrics = if (cardio) availableCardioMetrics(progress) else emptyList()
    val chosenCardio = if (cardio) {
        cardioMetrics.firstOrNull { it.name == cardioMetric } ?: defaultCardioMetricOf(cardioMetrics)
    } else {
        null
    }
    val shownMetric = chosenCardio ?: progress.metric
    val allPoints = when {
        useOneRm -> progress.oneRmPoints
        chosenCardio != null -> progress.pointsFor(chosenCardio)
        else -> progress.points
    }
    val inRange = ProgressRules.inRange(allPoints, range, today)
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
        metric = if (useOneRm) ProgressMetric.ONE_RM else shownMetric,
        range = range,
        today = today,
        best = ProgressRules.best(allPoints),
        change = ProgressRules.change(inRange),
        points = inRange,
        scale = scale,
        axisStart = start,
        axisEnd = end,
        selectedIndex = selected?.takeIf { it in inRange.indices },
        showAll = showAll,
        weeks = progress.weeks,
        oneRmAvailable = oneRmAvailable,
        showOneRm = useOneRm,
        oneRmBest = ProgressRules.best(progress.oneRmPoints),
        cardioMetrics = cardioMetrics,
        cardioRecords = cardioMetrics.filter { it in RECORD_METRICS }.mapNotNull { metric ->
            ProgressRules.best(progress.pointsFor(metric))?.let { CardioRecord(metric, it) }
        },
    )
}

@HiltViewModel
class MachineProgressViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repository: ProgressRepository,
    private val clock: Clock,
) : ViewModel() {

    private val machineId: Long = checkNotNull(savedStateHandle.get<Long>(MACHINE_ID_ARG)) { "machineId missing" }

    private val range = MutableStateFlow(ProgressRange.THREE_MONTHS)
    private val selected = MutableStateFlow<Int?>(null)
    private val showAll = MutableStateFlow(false)

    // Kept in the saved state so the choice survives rotation and process death; it is per machine
    // because this view model (and its handle) belongs to one machine's route.
    private val showOneRm: StateFlow<Boolean> = savedStateHandle.getStateFlow(SHOW_ONE_RM_KEY, false)

    // The cardio metric chip, saved as the enum name; null until the user picks one.
    private val cardioMetric: StateFlow<String?> = savedStateHandle.getStateFlow<String?>(CARDIO_METRIC_KEY, null)

    // The two saved choices travel together so the main combine stays within five flows.
    private val choices: Flow<Pair<Boolean, String?>> = combine(showOneRm, cardioMetric) { oneRm, metric -> oneRm to metric }

    internal val state: StateFlow<MachineProgressUiState> = combine(
        repository.observeMachineProgress(machineId),
        range,
        selected,
        showAll,
        choices,
    ) { progress, range, selected, showAll, (showOneRm, cardioMetric) ->
        buildMachineState(progress, range, LocalDate.now(clock), selected, showAll, showOneRm, cardioMetric)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachineProgressUiState())

    internal fun selectRange(newRange: ProgressRange) {
        selected.value = null
        range.value = newRange
    }

    /** The chart's tooltip point; null clears it. */
    internal fun selectPoint(index: Int?) {
        selected.value = index
    }

    /** Switches the chart between top weight (false) and estimated 1RM (true). */
    internal fun setShowOneRm(show: Boolean) {
        selected.value = null
        savedStateHandle[SHOW_ONE_RM_KEY] = show
    }

    /** Picks the cardio chart's metric chip; remembered while the screen is open (and across rotation). */
    internal fun setCardioMetric(metric: ProgressMetric) {
        selected.value = null
        savedStateHandle[CARDIO_METRIC_KEY] = metric.name
    }

    internal fun showAllSessions() {
        showAll.value = true
    }
}

private const val SHOW_ONE_RM_KEY = "showOneRm"
/** The cardio metrics that have an all-time best worth a line: heart rate and watts/kg have no "best". */
private val RECORD_METRICS = setOf(
    ProgressMetric.SPEED,
    ProgressMetric.POWER,
    ProgressMetric.INTENSITY_FACTOR,
    ProgressMetric.TSS,
    ProgressMetric.INTENSITY,
    ProgressMetric.DISTANCE,
    ProgressMetric.MINUTES,
)

private const val CARDIO_METRIC_KEY = "cardioMetric"

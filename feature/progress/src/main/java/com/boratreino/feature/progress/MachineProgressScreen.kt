package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.CardioWeek
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.EmptyState
import com.boratreino.core.ui.cardioText
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.R as CoreUiR
import java.time.LocalDate

/** Sessions listed before "Show all". */
internal const val SESSIONS_SHOWN = 15

/** What the machine detail can ask for. */
internal data class MachineProgressActions(
    val onBack: () -> Unit = {},
    val onSelectRange: (ProgressRange) -> Unit = {},
    val onSelectPoint: (index: Int?) -> Unit = {},
    val onShowAll: () -> Unit = {},
    val onShowOneRm: (Boolean) -> Unit = {},
)

@Composable
internal fun MachineProgressScreen(
    onBack: () -> Unit,
    viewModel: MachineProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MachineProgressContent(
        state = state,
        actions = MachineProgressActions(
            onBack = onBack,
            onSelectRange = viewModel::selectRange,
            onSelectPoint = viewModel::selectPoint,
            onShowAll = viewModel::showAllSessions,
            onShowOneRm = viewModel::setShowOneRm,
        ),
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun MachineProgressContent(
    state: MachineProgressUiState = MachineProgressUiState(),
    actions: MachineProgressActions = MachineProgressActions(),
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(CoreUiR.drawable.ic_back), stringResource(CoreUiR.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !state.loaded -> Unit
            !state.found -> EmptyState(
                title = stringResource(R.string.progress_machine_not_found),
                modifier = Modifier.padding(innerPadding),
            )
            else -> DetailList(state, actions, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun DetailList(state: MachineProgressUiState, actions: MachineProgressActions, modifier: Modifier) {
    val newestFirst = state.points.asReversed()
    val visible = if (state.showAll) newestFirst else newestFirst.take(SESSIONS_SHOWN)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "tiles") { Tiles(state) }
        item(key = "ranges") { RangeChips(state.range, actions.onSelectRange) }
        if (state.oneRmAvailable) {
            item(key = "view") { ViewChips(state.showOneRm, actions.onShowOneRm) }
        }
        item(key = "chart") { ChartCard(state, actions.onSelectPoint) }
        if (state.oneRmAvailable && state.oneRmBest != null) {
            item(key = "one-rm-best") { OneRmBestCard(state.oneRmBest, state.today) }
        }
        if (state.metric == ProgressMetric.MINUTES && state.weeks.isNotEmpty()) {
            item(key = "weeks") { WeeksCard(state.weeks, state.today) }
        }
        if (state.points.isNotEmpty()) {
            item(key = "sessions-header") {
                Text(
                    stringResource(R.string.progress_sessions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            items(visible, key = { it.sessionId }) { point ->
                SessionRow(state, point)
            }
            if (!state.showAll && newestFirst.size > SESSIONS_SHOWN) {
                item(key = "show-all") {
                    TextButton(onClick = actions.onShowAll, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.progress_show_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun Tiles(state: MachineProgressUiState) {
    val best = state.best
    val change = state.change
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            label = stringResource(
                if (state.metric == ProgressMetric.ONE_RM) R.string.progress_tile_best_one_rm else R.string.progress_tile_best,
            ),
            value = if (best == null) stringResource(R.string.progress_dash) else valueText(state.metric, best.value),
            hint = best?.let { formatFullDate(it.date) },
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.progress_tile_change),
            value = when {
                change == null -> stringResource(R.string.progress_dash)
                change == 0.0 -> stringResource(R.string.progress_no_change)
                else -> deltaText(state.metric, change)
            },
            hint = if (change == null) stringResource(R.string.progress_change_needs_two) else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, hint: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RangeChips(selected: ProgressRange, onSelect: (ProgressRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (range in ProgressRange.entries) {
            BoraChip(
                label = rangeLabel(range),
                selected = range == selected,
                onClick = { onSelect(range) },
            )
        }
    }
}

/** Top weight | Est. 1RM: which value the chart follows. */
@Composable
private fun ViewChips(showOneRm: Boolean, onShowOneRm: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (oneRm in listOf(false, true)) {
            BoraChip(
                label = stringResource(if (oneRm) R.string.progress_view_one_rm else R.string.progress_view_top_weight),
                selected = showOneRm == oneRm,
                onClick = { onShowOneRm(oneRm) },
            )
        }
    }
}

/** "Best estimated 1RM": the value, its date and the set it came from. */
@Composable
private fun OneRmBestCard(best: SessionPoint, today: LocalDate) {
    StatTile(
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.progress_one_rm_card_title),
        value = valueText(ProgressMetric.ONE_RM, best.value),
        hint = stringResource(R.string.progress_detail_pair, formatShortDate(best.date, today), setOnlyText(best)),
    )
}

@Composable
private fun rangeLabel(range: ProgressRange): String = stringResource(
    when (range) {
        ProgressRange.THREE_MONTHS -> R.string.progress_range_3m
        ProgressRange.ONE_YEAR -> R.string.progress_range_1y
        ProgressRange.ALL -> R.string.progress_range_all
    },
)

@Composable
private fun ChartCard(state: MachineProgressUiState, onSelect: (Int?) -> Unit) {
    val points = state.points
    val title = when (state.metric) {
        ProgressMetric.WEIGHT -> stringResource(R.string.progress_chart_title_weight)
        ProgressMetric.REPS -> stringResource(R.string.progress_chart_title_reps)
        ProgressMetric.MINUTES -> stringResource(R.string.progress_chart_title_minutes)
        ProgressMetric.ONE_RM -> stringResource(R.string.progress_chart_title_one_rm)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.progress_chart_not_enough),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val selected = state.selectedIndex?.let { points.getOrNull(it) }
                val tooltip = selected?.let {
                    stringResource(
                        R.string.progress_detail,
                        formatFullDate(it.date),
                        topText(state.metric, it),
                        setsText(it),
                    )
                }
                ProgressChart(
                    points = points,
                    scale = state.scale,
                    axisStart = state.axisStart,
                    axisEnd = state.axisEnd,
                    selectedIndex = state.selectedIndex,
                    onSelect = onSelect,
                    tooltip = tooltip,
                    description = chartDescription(state),
                    valueFormat = valueFormatter(state.metric),
                )
                if (state.metric == ProgressMetric.ONE_RM) {
                    Text(
                        stringResource(R.string.progress_one_rm_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Plain number formatting for chart labels: estimates keep one decimal, other values up to two. */
private fun valueFormatter(metric: ProgressMetric): (Double) -> String =
    if (metric == ProgressMetric.ONE_RM) ::formatEstimate1 else ::formatKg1

private fun formatEstimate1(value: Double): String = formatEstimate(value)

private fun formatKg1(value: Double): String = formatKg(value)

/** "Top weight from 100 kg on 3 Sep to 125 kg on 2 Nov; best 125 kg". */
@Composable
private fun chartDescription(state: MachineProgressUiState): String {
    val points = state.points
    val first = points.first()
    val last = points.last()
    val best = points.maxOf { it.value }
    val metric = state.metric
    val number = valueFormatter(metric)
    return if (points.size < 2) {
        stringResource(
            when (metric) {
                ProgressMetric.WEIGHT -> R.string.progress_chart_description_one_weight
                ProgressMetric.REPS -> R.string.progress_chart_description_one_reps
                ProgressMetric.MINUTES -> R.string.progress_chart_description_one_minutes
                ProgressMetric.ONE_RM -> R.string.progress_chart_description_one_one_rm
            },
            number(last.value),
            formatShortDate(last.date, state.today),
        )
    } else {
        stringResource(
            when (metric) {
                ProgressMetric.WEIGHT -> R.string.progress_chart_description_weight
                ProgressMetric.REPS -> R.string.progress_chart_description_reps
                ProgressMetric.MINUTES -> R.string.progress_chart_description_minutes
                ProgressMetric.ONE_RM -> R.string.progress_chart_description_one_rm
            },
            number(first.value),
            formatShortDate(first.date, state.today),
            number(last.value),
            formatShortDate(last.date, state.today),
            number(best),
        )
    }
}

@Composable
private fun SessionRow(state: MachineProgressUiState, point: SessionPoint) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Text(
            stringResource(
                R.string.progress_detail,
                formatShortDate(point.date, state.today),
                topText(state.metric, point),
                setsText(point),
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** "Minutes per week": 12 bars, then a row per week that had cardio, newest first. */
@Composable
private fun WeeksCard(weeks: List<CardioWeek>, today: LocalDate) {
    val currentStart = weeks.last().weekStart
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.progress_weeks_title), style = MaterialTheme.typography.titleMedium)
            WeeklyBars(weeks, description = weeksDescription(weeks))
            for (week in weeks.asReversed()) {
                if (week.minutes == 0 && week.calories == 0 && week.distanceM == 0) continue
                val label = if (week.weekStart == currentStart) {
                    stringResource(R.string.progress_week_this)
                } else {
                    stringResource(R.string.progress_week_of, formatShortDate(week.weekStart, today))
                }
                Text(
                    stringResource(R.string.progress_detail_pair, label, cardioText(week.minutes, week.calories, week.distanceM)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun weeksDescription(weeks: List<CardioWeek>): String {
    val total = weeks.sumOf { it.minutes }
    val best = weeks.maxOf { it.minutes }
    return stringResource(R.string.progress_weeks_description, weeks.size, total, best)
}

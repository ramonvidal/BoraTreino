package com.boratreino.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.CardioWeek
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.ui.EmptyState
import com.boratreino.core.ui.cardioText
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.label
import com.boratreino.core.ui.R as CoreUiR

/** What the Progress front page can ask for. */
internal data class ProgressActions(
    val onOpenMachine: (machineId: Long) -> Unit = {},
)

/** The two pages of the Progress tab. */
internal enum class ProgressPage { MACHINES, STATS }

/** The Progress tab: a Machines | Stats switch above the chosen page. The choice survives rotation. */
@Composable
internal fun ProgressShell(
    onOpenMachine: (machineId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by rememberSaveable { mutableStateOf(ProgressPage.MACHINES.name) }
    val page = ProgressPage.entries.firstOrNull { it.name == chosen } ?: ProgressPage.MACHINES
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
            ProgressPage.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = page == option,
                    onClick = { chosen = option.name },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ProgressPage.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (option == ProgressPage.MACHINES) R.string.progress_page_machines else R.string.progress_page_stats,
                        ),
                    )
                }
            }
        }
        when (page) {
            ProgressPage.MACHINES -> ProgressScreen(onOpenMachine = onOpenMachine, modifier = Modifier.weight(1f))
            ProgressPage.STATS -> StatsScreen(onOpenMachine = onOpenMachine, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ProgressScreen(
    onOpenMachine: (machineId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressContent(state = state, actions = ProgressActions(onOpenMachine = onOpenMachine), modifier = modifier)
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun ProgressContent(
    state: ProgressUiState = ProgressUiState(),
    actions: ProgressActions = ProgressActions(),
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        when {
            !state.loaded -> Unit
            state.machines.isEmpty() -> EmptyState(
                title = stringResource(R.string.progress_empty),
                icon = CoreUiR.drawable.ic_progress,
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") { SummaryCard(state) }
                state.cardioThisWeek?.let { week ->
                    item(key = "cardio-week") { CardioWeekCard(week) }
                }
                if (state.improved.isNotEmpty()) {
                    item(key = "improved-header") { SectionTitle(stringResource(R.string.progress_most_improved)) }
                    item(key = "improved") { ImprovedCard(state.improved, actions.onOpenMachine) }
                }
                item(key = "machines-header") { SectionTitle(stringResource(R.string.progress_machines)) }
                items(state.machines, key = { it.machine.id }) { row ->
                    MachineRowCard(row, state, actions.onOpenMachine)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SoftCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) { content() }
}

/** "This month": three big numbers, each with a small label. */
@Composable
private fun SummaryCard(state: ProgressUiState) {
    SoftCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.progress_this_month), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigNumber(state.trainingsThisMonth, stringResource(R.string.progress_stat_trainings), Modifier.weight(1f))
                BigNumber(state.recordsThisMonth, stringResource(R.string.progress_stat_records), Modifier.weight(1f))
                BigNumber(state.setsThisMonth, stringResource(R.string.progress_stat_sets), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BigNumber(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Cardio this week: 45 min · 320 kcal". */
@Composable
private fun CardioWeekCard(week: CardioWeek) {
    SoftCard {
        Text(
            stringResource(R.string.progress_cardio_week, cardioText(week.minutes, week.calories, week.distanceM)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(16.dp),
        )
    }
}

@Composable
private fun ImprovedCard(rows: List<ImprovedRow>, onOpen: (Long) -> Unit) {
    SoftCard {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ImprovedRowItem(row, onOpen)
            }
        }
    }
}

@Composable
private fun ImprovedRowItem(row: ImprovedRow, onOpen: (Long) -> Unit) {
    val from = formatKg(row.improvement.from)
    val to = formatKg(row.improvement.to)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(row.machine.id) }
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.machine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painterResource(CoreUiR.drawable.ic_arrow_upward),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    deltaText(row.metric, row.improvement.delta),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = when (row.metric) {
                    ProgressMetric.WEIGHT, ProgressMetric.ONE_RM -> stringResource(R.string.progress_from_to_kg, from, to)
                    ProgressMetric.REPS -> stringResource(R.string.progress_from_to_reps, from, to)
                    ProgressMetric.MINUTES -> stringResource(R.string.progress_from_to_minutes, from, to)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TrendLine(row.trend, trendDescription(row.metric, row.trend))
    }
}

@Composable
private fun MachineRowCard(row: MachineRow, state: ProgressUiState, onOpen: (Long) -> Unit) {
    val best = when (row.metric) {
        ProgressMetric.WEIGHT, ProgressMetric.ONE_RM -> stringResource(R.string.progress_best_kg, formatKg(row.best))
        ProgressMetric.REPS -> row.best.toInt().let { pluralStringResource(R.plurals.progress_best_reps, it, it) }
        ProgressMetric.MINUTES -> stringResource(R.string.progress_best_minutes, row.best.toInt())
    }
    val last = stringResource(R.string.progress_last_trained, formatShortDate(row.lastTrained, state.today))
    Card(
        onClick = { onOpen(row.machine.id) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.machine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    row.machine.muscleGroup.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.progress_detail_pair, best, last),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TrendLine(row.trend, trendDescription(row.metric, row.trend))
        }
    }
}

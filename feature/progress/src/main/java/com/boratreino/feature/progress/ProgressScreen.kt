package com.boratreino.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.CardioWeek
import com.boratreino.core.model.Machine
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.EmptyState
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.cardioText
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.R as CoreUiR
import kotlin.math.roundToInt

/** What the Progress front page can ask for. */
internal data class ProgressActions(
    val onOpenMachine: (machineId: Long) -> Unit = {},
)

/** The two pages of the Progress tab. [MACHINES] is the Exercises page (the name is kept for the saved choice). */
internal enum class ProgressPage { MACHINES, STATS }

/** The Progress tab: a large title and an Exercises | Stats switch above the chosen page. The choice survives rotation. */
@Composable
internal fun ProgressShell(
    onOpenMachine: (machineId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by rememberSaveable { mutableStateOf(ProgressPage.MACHINES.name) }
    val page = ProgressPage.entries.firstOrNull { it.name == chosen } ?: ProgressPage.MACHINES
    // statusBarsPadding keeps the switch clear of the status bar and consumes that inset, so the pages' own Scaffolds do not add it again below the switch.
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
            Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressPage.entries.forEach { option ->
                    BoraChip(
                        label = stringResource(
                            if (option == ProgressPage.MACHINES) R.string.progress_page_machines else R.string.progress_page_stats,
                        ),
                        selected = page == option,
                        onClick = { chosen = option.name },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                item(key = "machines") { MachinesCard(state, actions.onOpenMachine) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

/** "This month": three big numbers, each with a small label. */
@Composable
private fun SummaryCard(state: ProgressUiState) {
    BoraCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.progress_this_month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Cardio this week: 45 min · 320 kcal". */
@Composable
private fun CardioWeekCard(week: CardioWeek) {
    BoraCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.progress_cardio_week, cardioText(week.minutes, week.calories, week.distanceM)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ImprovedCard(rows: List<ImprovedRow>, onOpen: (Long) -> Unit) {
    BoraCard(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val from = formatKg(row.improvement.from)
            val to = formatKg(row.improvement.to)
            val detail = when (row.metric) {
                ProgressMetric.WEIGHT, ProgressMetric.ONE_RM -> stringResource(R.string.progress_from_to_kg, from, to)
                ProgressMetric.REPS -> stringResource(R.string.progress_from_to_reps, from, to)
                ProgressMetric.MINUTES -> stringResource(R.string.progress_from_to_minutes, from, to)
            }
            ExerciseRow(
                machine = row.machine,
                detail = detail,
                metric = row.metric,
                trend = row.trend,
                changePercent = row.improvement.percent,
                onClick = { onOpen(row.machine.id) },
            )
        }
    }
}

/** The Exercises list: one card, one row per exercise. */
@Composable
private fun MachinesCard(state: ProgressUiState, onOpen: (Long) -> Unit) {
    BoraCard(Modifier.fillMaxWidth()) {
        state.machines.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val best = when (row.metric) {
                ProgressMetric.WEIGHT, ProgressMetric.ONE_RM -> stringResource(R.string.progress_best_kg, formatKg(row.best))
                ProgressMetric.REPS -> row.best.toInt().let { pluralStringResource(R.plurals.progress_best_reps, it, it) }
                ProgressMetric.MINUTES -> stringResource(R.string.progress_best_minutes, row.best.toInt())
            }
            val days = daysSince(row.lastTrained, state.today)
            val ago = if (days <= 0) stringResource(R.string.progress_today) else pluralStringResource(R.plurals.progress_days_ago, days, days)
            ExerciseRow(
                machine = row.machine,
                detail = stringResource(R.string.progress_detail_pair, best, ago),
                metric = row.metric,
                trend = row.trend,
                changePercent = trendChangePercent(row.trend),
                onClick = { onOpen(row.machine.id) },
            )
        }
    }
}

/** Thumb, bold name, muted detail, a sparkline and the latest change as a coloured number. */
@Composable
private fun ExerciseRow(
    machine: Machine,
    detail: String,
    metric: ProgressMetric,
    trend: List<Double>,
    changePercent: Double?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MachineThumb(name = machine.name, muscleGroup = machine.muscleGroup, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(
                machine.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ExerciseSparkline(trend, trendDescription(metric, trend))
        ChangeNumber(changePercent)
    }
}

/** "+8 %" in the secondary colour, "-3 %" in the error colour, "0" muted; blank when there is nothing to compare. */
@Composable
private fun ChangeNumber(percent: Double?) {
    val scheme = MaterialTheme.colorScheme
    if (percent == null) {
        Spacer(Modifier.width(48.dp))
        return
    }
    val rounded = percent.roundToInt()
    val color: Color = when {
        rounded > 0 -> scheme.secondary
        rounded < 0 -> scheme.error
        else -> scheme.onSurfaceVariant
    }
    Text(
        signedPercent(percent),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.widthIn(min = 48.dp),
        textAlign = TextAlign.End,
    )
}

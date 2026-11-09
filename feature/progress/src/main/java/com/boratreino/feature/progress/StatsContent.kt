package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.MuscleRecency
import com.boratreino.core.model.Record
import com.boratreino.core.model.RepRange
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.label

private const val RECENT_DAYS = 3
private const val WEEK_DAYS = 7
private const val MAX_RECORDS = 10

/** The Stats page: period chips, then the cards. Drawn from its state alone. */
@Composable
internal fun StatsContent(
    state: StatsUiState,
    actions: StatsActions,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") { PeriodChips(state.period, actions.onPeriod) }
        if (report == null) return@LazyColumn
        if (report.isEmptyPeriod()) {
            item(key = "empty") { StatsEmptyCard() }
            return@LazyColumn
        }
        item(key = "summary") { SummaryCard(report) }
        if (report.muscleWeeks.any { it.volumeKg > 0 }) {
            item(key = "volume") { WeeklyVolumeCard(report) }
        }
        if (report.muscleWeeks.isNotEmpty()) {
            item(key = "hard-sets") { HardSetsCard(report) }
        }
        item(key = "recency") { RecencyCard(report) }
        if (report.strength.isNotEmpty()) {
            item(key = "strength") { StrengthCard(report, actions.onOpenMachine) }
        }
        if (report.records.isNotEmpty()) {
            item(key = "records") { RecordsCard(report, actions.onOpenMachine) }
        }
        if (report.repRanges.any { it.sets > 0 }) {
            item(key = "rep-ranges") { RepRangesCard(report) }
        }
        if (report.times.averageMinutes != null || report.times.averageMinutesBetweenSets != null) {
            item(key = "times") { SessionLengthCard(report) }
        }
    }
}

@Composable
internal fun PeriodChips(selected: StatsPeriod, onPeriod: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatsPeriod.entries.forEach { period ->
            BoraChip(
                label = periodLabel(period),
                selected = period == selected,
                onClick = { onPeriod(period) },
            )
        }
    }
}

@Composable
private fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.FOUR_WEEKS -> R.string.stats_period_4w
        StatsPeriod.THREE_MONTHS -> R.string.progress_range_3m
        StatsPeriod.ONE_YEAR -> R.string.progress_range_1y
        StatsPeriod.ALL -> R.string.progress_range_all
    },
)

@Composable
private fun StatsCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun StatsEmptyCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Text(
            stringResource(R.string.stats_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
    }
}

@Composable
private fun BodyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = 28.dp))
}

@Composable
private fun NoteText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Sessions a week, consistency, best week and the overall strength change. */
@Composable
internal fun SummaryCard(report: StatsReport, modifier: Modifier = Modifier) {
    val consistency = report.consistency
    StatsCard(stringResource(R.string.stats_summary_title), modifier) {
        BodyLine(stringResource(R.string.stats_sessions_per_week, formatDecimal(consistency.sessionsPerWeek, 1)))
        if (consistency.weeksCounted > 0) {
            BodyLine(
                pluralStringResource(
                    R.plurals.stats_goal_weeks,
                    consistency.weeksCounted,
                    consistency.weeksMetGoal,
                    consistency.weeksCounted,
                ),
            )
        }
        consistency.bestWeekStart?.let { start ->
            BodyLine(
                pluralStringResource(
                    R.plurals.stats_best_week,
                    consistency.bestWeekTrainingDays,
                    consistency.bestWeekTrainingDays,
                    formatShortDate(start, report.to),
                ),
            )
        }
        report.overallStrengthPercent?.let { percent ->
            BodyLine(stringResource(R.string.stats_strength_overall, percentText(percent)))
        }
    }
}

@Composable
private fun percentText(percent: Double): String =
    stringResource(R.string.stats_percent, signedPercentNumber(percent))

/** Stacked weekly volume by muscle group, with its legend. */
@Composable
internal fun WeeklyVolumeCard(report: StatsReport, modifier: Modifier = Modifier) {
    StatsCard(stringResource(R.string.stats_volume_title), modifier) {
        StackedWeekBars(report.weeks, report.muscleWeeks)
        MuscleLegend(report.muscleWeeks)
    }
}

/** Average working sets a week per muscle group. */
@Composable
internal fun HardSetsCard(report: StatsReport, modifier: Modifier = Modifier) {
    val weekCount = report.weeks.size.coerceAtLeast(1)
    val rows = report.muscleWeeks
        .groupBy { it.muscleGroup }
        .map { (group, weeks) -> group to weeks.sumOf { it.workingSets }.toDouble() / weekCount }
        .filter { it.second > 0.0 }
        .sortedByDescending { it.second }
    val bars = rows.map { (group, perWeek) ->
        BarValue(group.label(), perWeek, stringResource(R.string.stats_sets_per_week, formatDecimal(perWeek, 1)))
    }
    StatsCard(stringResource(R.string.stats_hard_sets_title), modifier) {
        HorizontalBars(bars)
        NoteText(stringResource(R.string.stats_hard_sets_note))
    }
}

/** One row per muscle group with a dot for how long ago it was trained. */
@Composable
internal fun RecencyCard(report: StatsReport, modifier: Modifier = Modifier) {
    StatsCard(stringResource(R.string.stats_recency_title), modifier) {
        Column {
            report.recency.forEach { RecencyRow(it) }
        }
    }
}

@Composable
private fun RecencyRow(item: MuscleRecency) {
    val colors = MaterialTheme.colorScheme
    val days = item.daysSince
    val dot = when {
        days == null -> colors.outline
        days <= RECENT_DAYS -> colors.primary
        days <= WEEK_DAYS -> StatsPalette.amber()
        else -> colors.onSurfaceVariant.copy(alpha = 0.5f)
    }
    val text = when (days) {
        null -> stringResource(R.string.stats_never)
        0 -> stringResource(R.string.stats_today)
        else -> pluralStringResource(R.plurals.stats_days_ago, days, days)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(12.dp)) {
            if (days == null) {
                val stroke = 1.5.dp.toPx()
                drawCircle(dot, radius = (size.minDimension - stroke) / 2f, style = Stroke(stroke))
            } else {
                drawCircle(dot)
            }
        }
        Text(item.muscleGroup.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** Per machine: estimated 1RM before and after, the change, and the plateaus. */
@Composable
internal fun StrengthCard(report: StatsReport, onOpenMachine: (Long) -> Unit, modifier: Modifier = Modifier) {
    StatsCard(stringResource(R.string.stats_strength_title), modifier) {
        Column {
            report.strength.forEachIndexed { index, trend ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMachine(trend.machine.id) }
                        .heightIn(min = 56.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            trend.machine.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                R.string.stats_strength_from_to,
                                formatEstimate(trend.before),
                                formatEstimate(trend.after),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(percentText(trend.changePercent), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        report.plateaus.forEach { plateau ->
            NoteText(
                pluralStringResource(
                    R.plurals.stats_plateau,
                    plateau.sessionsWithoutImprovement,
                    plateau.machine.name,
                    plateau.sessionsWithoutImprovement,
                ),
            )
        }
    }
}

/** The last records, newest first. */
@Composable
internal fun RecordsCard(report: StatsReport, onOpenMachine: (Long) -> Unit, modifier: Modifier = Modifier) {
    StatsCard(stringResource(R.string.stats_records_title), modifier) {
        Column {
            report.records.take(MAX_RECORDS).forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val text = when (val record = event.record) {
                    is Record.HeaviestWeight -> stringResource(R.string.stats_record_heaviest, formatKg(record.weightKg))
                    is Record.MostReps -> stringResource(R.string.stats_record_most_reps, record.reps, formatKg(record.weightKg))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMachine(event.machine.id) }
                        .heightIn(min = 56.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            event.machine.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        formatShortDate(event.date, report.to),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One stacked bar of the share of sets in each rep range. */
@Composable
internal fun RepRangesCard(report: StatsReport, modifier: Modifier = Modifier) {
    val colors = StatsPalette.rangeColors()
    val shares = report.repRanges.map { share ->
        val index = share.range.ordinal
        ShareValue(
            label = repRangeLabel(share.range),
            percent = share.percent,
            percentLabel = stringResource(R.string.stats_percent_plain, formatDecimal(share.percent, 0)),
            color = colors[index],
        )
    }
    StatsCard(stringResource(R.string.stats_rep_ranges_title), modifier) {
        StackedShareBar(shares)
    }
}

@Composable
private fun repRangeLabel(range: RepRange): String = stringResource(
    when (range) {
        RepRange.R1_5 -> R.string.stats_reps_1_5
        RepRange.R6_8 -> R.string.stats_reps_6_8
        RepRange.R9_12 -> R.string.stats_reps_9_12
        RepRange.R13_PLUS -> R.string.stats_reps_13_plus
    },
)

/** Average session length and time between sets. */
@Composable
internal fun SessionLengthCard(report: StatsReport, modifier: Modifier = Modifier) {
    StatsCard(stringResource(R.string.stats_times_title), modifier) {
        report.times.averageMinutes?.let { BodyLine(stringResource(R.string.stats_average_session, it)) }
        report.times.averageMinutesBetweenSets?.let {
            BodyLine(stringResource(R.string.stats_between_sets, formatDecimal(it, 1)))
        }
    }
}

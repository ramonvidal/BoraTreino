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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyInsightRules
import com.boratreino.core.model.Highlight
import com.boratreino.core.model.HighlightKind
import com.boratreino.core.model.MuscleRecency
import com.boratreino.core.model.Record
import com.boratreino.core.model.RepRange
import com.boratreino.core.model.StatsHighlights
import com.boratreino.core.model.StatsPage
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.label
import kotlin.math.roundToInt
import com.boratreino.core.ui.R as CoreUiR

private const val RECENT_DAYS = 3
private const val WEEK_DAYS = 7
private const val MAX_RECORDS = 10

/**
 * The Stats page: pinned period chips and sub-page switch, then the highlights and the cards of
 * the chosen sub-page. Drawn from its state alone; [initialPage] only seeds the first sub-page
 * (screenshot tests), the choice then survives rotation.
 */
@Composable
internal fun StatsContent(
    state: StatsUiState,
    actions: StatsActions,
    modifier: Modifier = Modifier,
    initialPage: StatsPage? = null,
) {
    val report = state.report
    var page by rememberSaveable { mutableStateOf(initialPage ?: StatsPage.BODY) }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodChips(state.period, actions.onPeriod)
            StatsPageChips(page) { page = it }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (report == null) return@LazyColumn
            if (report.isEmptyPeriod()) {
                item(key = "empty") { StatsEmptyCard() }
                return@LazyColumn
            }
            val highlights = StatsHighlights.of(report)
            if (highlights.isNotEmpty()) {
                item(key = "highlights") { HighlightsStrip(highlights) { page = it } }
            }
            when (page) {
                StatsPage.BODY -> bodyItems(state, report)
                StatsPage.STRENGTH -> strengthItems(report, actions)
                StatsPage.HABIT -> habitItems(report)
            }
        }
    }
}

private fun LazyListScope.bodyItems(state: StatsUiState, report: StatsReport) {
    item(key = "week-muscles") { WeekMusclesCard(report) }
    if (BodyInsightRules.muscleLoad(report).values.any { it > 0 }) {
        item(key = "body-map") { BodyMapCard(report) }
    }
    if (hasBalance(report.balance)) {
        item(key = "balance") { BalanceCard(report.balance) }
    }
    if (hasRelativeStrength(state)) {
        item(key = "relative-strength") { RelativeStrengthCard(state) }
    }
    if (hasStrengthChart(report)) {
        item(key = "strength-vs-weight") { StrengthVsWeightCard(state) }
    }
    if (report.muscleWeeks.isNotEmpty()) {
        item(key = "hard-sets") { HardSetsCard(report) }
    }
}

private fun LazyListScope.strengthItems(report: StatsReport, actions: StatsActions) {
    if (report.strength.isNotEmpty()) {
        item(key = "strength") { StrengthCard(report, actions.onOpenMachine) }
    }
    if (report.records.isNotEmpty()) {
        item(key = "records") { RecordsCard(report, actions.onOpenMachine) }
    }
    if (report.repRanges.any { it.sets > 0 }) {
        item(key = "rep-ranges") { RepRangesCard(report) }
    }
}

private fun LazyListScope.habitItems(report: StatsReport) {
    item(key = "summary") { SummaryCard(report) }
    if (report.muscleWeeks.any { it.volumeKg > 0 }) {
        item(key = "volume") { WeeklyVolumeCard(report) }
    }
    item(key = "recency") { RecencyCard(report) }
    if (report.times.averageMinutes != null || report.times.averageMinutesBetweenSets != null) {
        item(key = "times") { SessionLengthCard(report) }
    }
}

/** The Body / Strength / Habit switch. */
@Composable
internal fun StatsPageChips(selected: StatsPage, onPage: (StatsPage) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatsPage.entries.forEach { page ->
            BoraChip(
                label = stringResource(
                    when (page) {
                        StatsPage.BODY -> R.string.stats_page_body
                        StatsPage.STRENGTH -> R.string.stats_page_strength
                        StatsPage.HABIT -> R.string.stats_page_habit
                    },
                ),
                selected = page == selected,
                onClick = { onPage(page) },
            )
        }
    }
}

/** Up to three cards in a row that scrolls sideways; tapping one opens the page that holds its card. */
@Composable
internal fun HighlightsStrip(highlights: List<Highlight>, onPage: (StatsPage) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        highlights.forEach { highlight -> HighlightCard(highlight, onPage) }
    }
}

@Composable
private fun HighlightCard(highlight: Highlight, onPage: (StatsPage) -> Unit) {
    val group = highlight.muscleGroup?.label().orEmpty()
    val sentence = when (highlight.kind) {
        HighlightKind.MUSCLE_LOAD -> stringResource(R.string.stats_highlight_load, group, formatOneDecimal(highlight.value))
        HighlightKind.MUSCLE_BEHIND -> pluralStringResource(R.plurals.stats_highlight_behind, highlight.days, group, highlight.days)
        HighlightKind.STRENGTH_GAIN -> stringResource(
            R.string.stats_highlight_gain,
            highlight.machine?.name.orEmpty(),
            stringResource(R.string.stats_percent, signedPercentNumber(highlight.value)),
        )
        HighlightKind.GOAL_STREAK -> {
            val weeks = highlight.value.roundToInt()
            pluralStringResource(R.plurals.stats_highlight_streak, weeks, weeks)
        }
    }
    val kindLabel = stringResource(
        when (highlight.kind) {
            HighlightKind.MUSCLE_LOAD -> R.string.stats_highlight_kind_load
            HighlightKind.MUSCLE_BEHIND -> R.string.stats_highlight_kind_behind
            HighlightKind.STRENGTH_GAIN -> R.string.stats_highlight_kind_gain
            HighlightKind.GOAL_STREAK -> R.string.stats_highlight_kind_streak
        },
    )
    BoraCard(modifier = Modifier.width(220.dp).heightIn(min = 104.dp), onClick = { onPage(highlight.target) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                kindLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(CoreUiR.drawable.ic_arrow_upward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 90f },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(sentence, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
internal fun StatsCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoraCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
internal fun StatsEmptyCard(modifier: Modifier = Modifier) {
    BoraCard(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.stats_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun BodyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = 28.dp))
}

@Composable
private fun NoteText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            style = MaterialTheme.typography.bodyMedium,
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
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyInsightRules
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.MuscleRecency
import com.boratreino.core.model.Record
import com.boratreino.core.model.RepRange
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.label
import java.time.LocalDate

private const val RECENT_DAYS = 3
private const val WEEK_DAYS = 7
private const val MAX_RECORDS = 10

/** One entry of the Analysis list. The key is the LazyColumn item key, so an anchor maps to an index by key. */
private sealed interface AnalysisItem {
    val key: String

    data object Empty : AnalysisItem {
        override val key = "empty"
    }

    data class CardItem(val card: AnalysisCard) : AnalysisItem {
        override val key: String = card.name
    }
}

/** Whether [card] has anything to show for [state] (the card's own condition from the old pages). */
private fun isPresent(card: AnalysisCard, state: StatsUiState, report: StatsReport): Boolean = when (card) {
    AnalysisCard.TREND -> report.machineTrends.isNotEmpty()
    AnalysisCard.WORKLOAD -> report.workload != null
    AnalysisCard.PATTERNS -> report.patterns != null
    AnalysisCard.HARD_SETS -> report.muscleWeeks.isNotEmpty()
    AnalysisCard.BALANCE -> hasBalance(report.balance)
    AnalysisCard.STRENGTH -> report.strength.isNotEmpty()
    AnalysisCard.RECORDS -> report.records.isNotEmpty()
    AnalysisCard.MUSCLE_VOLUME -> report.muscleVolumes.isNotEmpty()
    AnalysisCard.WEEK_MUSCLES -> true
    AnalysisCard.BODY_MAP, AnalysisCard.MUSCLES -> BodyInsightRules.muscleDetailLoad(report).values.any { it > 0 }
    AnalysisCard.REP_RANGES -> report.repRanges.any { it.sets > 0 }
    AnalysisCard.WEEKLY_VOLUME -> report.muscleWeeks.any { it.volumeKg > 0 }
    AnalysisCard.STRENGTH_VS_WEIGHT -> hasStrengthChart(report)
    AnalysisCard.RELATIVE_STRENGTH -> hasRelativeStrength(state)
    AnalysisCard.SUMMARY -> true
    AnalysisCard.CARDIO -> report.cardioBlocks.isNotEmpty()
    AnalysisCard.TIMES -> report.times.averageMinutes != null || report.times.averageMinutesBetweenSets != null
    AnalysisCard.RECENCY -> true
    AnalysisCard.MEASUREMENTS -> true
    AnalysisCard.PROPORTIONS -> report.waistHip.isNotEmpty()
}

/** Every card in display order (section by section, then the card order within each section). */
private val AllCardsInOrder: List<AnalysisCard> = listOf(
    AnalysisCard.TREND, AnalysisCard.WORKLOAD, AnalysisCard.PATTERNS, AnalysisCard.HARD_SETS, AnalysisCard.BALANCE,
    AnalysisCard.STRENGTH, AnalysisCard.RECORDS, AnalysisCard.REP_RANGES, AnalysisCard.STRENGTH_VS_WEIGHT,
    AnalysisCard.RELATIVE_STRENGTH,
    AnalysisCard.MUSCLE_VOLUME, AnalysisCard.WEEK_MUSCLES, AnalysisCard.BODY_MAP, AnalysisCard.MUSCLES,
    AnalysisCard.WEEKLY_VOLUME,
    AnalysisCard.SUMMARY, AnalysisCard.CARDIO, AnalysisCard.TIMES, AnalysisCard.RECENCY,
    AnalysisCard.MEASUREMENTS, AnalysisCard.PROPORTIONS,
)

/** The present cards in display order; the empty period shows only the measurements. Empty when there is no report. */
private fun presentCards(state: StatsUiState): List<AnalysisCard> {
    val report = state.report ?: return emptyList()
    if (report.isEmptyPeriod()) return listOf(AnalysisCard.MEASUREMENTS)
    return AllCardsInOrder.filter { isPresent(it, state, report) }
}

/** Pure: every present card for this state, in display order. */
internal fun analysisCards(state: StatsUiState): List<AnalysisCard> = presentCards(state)

/** Pure: the present cards of [section], in display order. */
internal fun analysisCards(state: StatsUiState, section: AnalysisSection): List<AnalysisCard> =
    presentCards(state).filter { sectionOf(it) == section }

/** Pure: the sections having a present card, in enum order. Empty with no report; the empty period has only BODY. */
internal fun analysisSections(state: StatsUiState): List<AnalysisSection> {
    val present = presentCards(state).map { sectionOf(it) }.toSet()
    return AnalysisSection.entries.filter { it in present }
}

/** The list items of the selected [section]: the empty-period card first when the period is empty. */
private fun analysisItems(state: StatsUiState, section: AnalysisSection?): List<AnalysisItem> {
    val report = state.report ?: return emptyList()
    val cards = section?.let { analysisCards(state, it) }.orEmpty().map { AnalysisItem.CardItem(it) }
    return if (report.isEmptyPeriod()) listOf(AnalysisItem.Empty) + cards else cards
}

@Composable
private fun sectionLabel(section: AnalysisSection): String = stringResource(
    when (section) {
        AnalysisSection.HIGHLIGHTS -> R.string.analysis_section_highlights
        AnalysisSection.STRENGTH -> R.string.stats_page_strength
        AnalysisSection.MUSCLES -> R.string.analysis_group_muscles
        AnalysisSection.HABIT -> R.string.stats_page_habit
        AnalysisSection.BODY -> R.string.stats_page_body
    },
)

/** The scrollable row of section chips at the top of Analysis. */
@Composable
private fun SectionChips(sections: List<AnalysisSection>, selected: AnalysisSection?, onSection: (AnalysisSection) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (section in sections) {
            BoraChip(label = sectionLabel(section), selected = section == selected, onClick = { onSection(section) })
        }
    }
}

/**
 * The Analysis level: the existing detail cards (the header lives in [ProgressShell]). Drawn from its state
 * alone. Scrolls to [anchor] once it is present, then calls [onAnchorShown].
 */
@Composable
internal fun AnalysisContent(
    state: StatsUiState,
    actions: StatsActions,
    listState: LazyListState,
    anchor: AnalysisCard?,
    onAnchorShown: () -> Unit,
    section: AnalysisSection,
    onSection: (AnalysisSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    var measureType by rememberSaveable { mutableStateOf<String?>(null) }
    var logSheetOpen by rememberSaveable { mutableStateOf(false) }
    val selectedMeasure = MeasurementType.entries.firstOrNull { it.name == measureType }
    val sections = analysisSections(state)
    val shown = if (section in sections) section else sections.firstOrNull()
    val entries = analysisItems(state, shown)
    // Index 0 is the chip row.
    val anchorIndex = if (anchor == null) -1 else entries.indexOfFirst { it.key == anchor.name }.let { if (it < 0) it else it + 1 }
    LaunchedEffect(anchor, anchorIndex, state.loaded) {
        if (anchor == null) return@LaunchedEffect
        if (anchorIndex >= 0) {
            listState.scrollToItem(anchorIndex)
            onAnchorShown()
        } else if (state.loaded && report != null) {
            onAnchorShown()
        }
    }
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report == null) return@LazyColumn
        if (sections.isNotEmpty()) {
            item(key = "sections") { SectionChips(sections, shown, onSection) }
        }
        items(entries, key = { it.key }) { item ->
            when (item) {
                AnalysisItem.Empty -> StatsEmptyCard()
                is AnalysisItem.CardItem -> when (item.card) {
                    AnalysisCard.TREND -> TrendSummaryCard(report.machineTrends, actions.onOpenMachine)
                    AnalysisCard.WORKLOAD -> report.workload?.let { WorkloadCard(it) }
                    AnalysisCard.PATTERNS -> report.patterns?.let { PatternsCard(it) }
                    AnalysisCard.HARD_SETS -> HardSetsCard(report)
                    AnalysisCard.BALANCE -> BalanceCard(report.balance)
                    AnalysisCard.STRENGTH -> StrengthCard(report, actions.onOpenMachine)
                    AnalysisCard.RECORDS -> RecordsCard(report, actions.onOpenMachine)
                    AnalysisCard.MUSCLE_VOLUME -> MuscleVolumeCard(report.muscleVolumes)
                    AnalysisCard.WEEK_MUSCLES -> WeekMusclesCard(report)
                    AnalysisCard.BODY_MAP -> BodyMapCard(report)
                    AnalysisCard.MUSCLES -> MusclesCard(report)
                    AnalysisCard.REP_RANGES -> RepRangesCard(report)
                    AnalysisCard.WEEKLY_VOLUME -> WeeklyVolumeCard(report)
                    AnalysisCard.STRENGTH_VS_WEIGHT -> StrengthVsWeightCard(state)
                    AnalysisCard.RELATIVE_STRENGTH -> RelativeStrengthCard(state)
                    AnalysisCard.SUMMARY -> SummaryCard(report)
                    AnalysisCard.CARDIO -> CardioCard(report)
                    AnalysisCard.TIMES -> SessionLengthCard(report)
                    AnalysisCard.RECENCY -> RecencyCard(report)
                    AnalysisCard.MEASUREMENTS -> MeasurementsCard(
                        measurements = state.measurements,
                        from = report.from,
                        today = report.to,
                        selectedType = selectedMeasure,
                        onType = { measureType = it.name },
                        onLog = { logSheetOpen = true },
                        onDelete = { actions.onDeleteMeasurement(it.id) },
                        onUndo = actions.onUndoDeleteMeasurement,
                    )
                    AnalysisCard.PROPORTIONS -> ProportionsCard(report.waistHip, report.to)
                }
            }
        }
    }
    if (logSheetOpen) {
        LogMeasurementSheet(
            measurements = state.measurements,
            initialType = selectedMeasure ?: defaultMeasurementType(state.measurements),
            onSave = { type, valueCm ->
                actions.onAddMeasurement(LocalDate.now(), type, valueCm)
                measureType = type.name
                logSheetOpen = false
            },
            onDismiss = { logSheetOpen = false },
        )
    }
}

@Composable
internal fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.ONE_WEEK -> R.string.stats_period_1w
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
                    is Record.LongestTime ->
                        if (record.weightKg > 0.0) {
                            stringResource(R.string.stats_record_longest_time, record.seconds, formatKg(record.weightKg))
                        } else {
                            stringResource(R.string.stats_record_longest_time_no_load, record.seconds)
                        }
                    is Record.LongestDistance ->
                        if (record.weightKg > 0.0) {
                            stringResource(R.string.stats_record_longest_distance, record.distanceM, formatKg(record.weightKg))
                        } else {
                            stringResource(R.string.stats_record_longest_distance_no_load, record.distanceM)
                        }
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

package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.Finding
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The Overview tab: "What matters now", then Frequency, Strength, Body, Habit and the Analysis
 * row. Drawn from its state alone; it hosts its own [LogMeasurementSheet].
 */
@Composable
internal fun OverviewContent(
    state: StatsUiState,
    actions: StatsActions,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    var logSheetOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val findings = state.findings
    // Items before "frequency": the title (or the "none" line) and one per finding.
    val frequencyIndex = 1 + findings.size
    val nowTitle = stringResource(R.string.overview_now_title)
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report == null) return@LazyColumn
        if (report.isEmptyPeriod()) {
            item(key = "empty") { StatsEmptyCard() }
            state.frequency?.let { frequency ->
                item(key = "frequency") { FrequencySection(frequency, actions.onOpenCalendar) }
            }
            item(key = "body") {
                BodySection(
                    state.body,
                    report.to,
                    onLog = { logSheetOpen = true },
                    onOpen = { actions.onOpenAnalysis(AnalysisCard.MEASUREMENTS) },
                )
            }
            item(key = "analysis-row") { AnalysisRow(onClick = { actions.onOpenAnalysis(null) }) }
            return@LazyColumn
        }
        item(key = "now-title") { SectionLabel(nowTitle) }
        if (findings.isEmpty()) {
            item(key = "now-none") {
                Text(
                    stringResource(R.string.overview_now_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        } else {
            findings.take(MAX_SHOWN_FINDINGS).forEachIndexed { index, finding ->
                item(key = "finding-$index") {
                    FindingCard(
                        finding,
                        onClick = {
                            when (val target = findingTarget(finding)) {
                                FindingTarget.Frequency -> scope.launch { listState.animateScrollToItem(frequencyIndex) }
                                is FindingTarget.Analysis -> actions.onOpenAnalysis(target.card)
                                is FindingTarget.Machine -> actions.onOpenMachine(target.id)
                            }
                        },
                    )
                }
            }
        }
        state.frequency?.let { frequency ->
            item(key = "frequency") { FrequencySection(frequency, actions.onOpenCalendar) }
        }
        item(key = "strength") {
            StrengthSection(state.strengthSummary, state.period, report.to, actions.onOpenMachine)
        }
        item(key = "body") {
            BodySection(
                state.body,
                report.to,
                onLog = { logSheetOpen = true },
                onOpen = { actions.onOpenAnalysis(AnalysisCard.MEASUREMENTS) },
            )
        }
        state.habit?.let { habit ->
            item(key = "habit") { HabitSection(habit, report.to) }
        }
        item(key = "analysis-row") { AnalysisRow(onClick = { actions.onOpenAnalysis(null) }) }
    }
    if (logSheetOpen) {
        LogMeasurementSheet(
            measurements = state.measurements,
            initialType = defaultMeasurementType(state.measurements),
            onSave = { type, valueCm ->
                actions.onAddMeasurement(LocalDate.now(), type, valueCm)
                logSheetOpen = false
            },
            onDismiss = { logSheetOpen = false },
        )
    }
}

private const val MAX_SHOWN_FINDINGS = 3

/** The small caps label above a group of cards. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

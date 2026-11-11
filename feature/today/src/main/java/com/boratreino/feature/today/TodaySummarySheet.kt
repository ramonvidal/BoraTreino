package com.boratreino.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.TrainingSummary
import com.boratreino.core.ui.R as CoreUiR

/** The summary on screen: the training, and whether its routine can be updated or just was. */
internal data class SummaryUi(
    val summary: TrainingSummary,
    /** The existing routine update offer would change something. */
    val canUpdateRoutine: Boolean = false,
    /** Set after the routine was updated from this training; the sheet then offers Undo. */
    val routineUpdate: com.boratreino.core.model.RoutineUpdate? = null,
)

/** What the sheet can ask for. */
internal data class SummaryActions(
    val onShare: () -> Unit = {},
    val onUpdateRoutine: () -> Unit = {},
    val onUndoRoutineUpdate: () -> Unit = {},
    /** Reopens the session (the old Undo of Done). */
    val onNotFinished: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Thin wrapper: the sheet closes by swipe, back or tap outside; nothing in it is required. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodaySummarySheet(summary: SummaryUi, actions: SummaryActions) {
    ModalBottomSheet(onDismissRequest = actions.onClose) {
        TrainingSummaryContent(summary, actions)
    }
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun TrainingSummaryContent(
    summary: SummaryUi,
    actions: SummaryActions,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val texts = remember(summary.summary, resources) { summaryTexts(resources, summary.summary) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(texts.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                texts.routineName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                texts.date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            val large = LocalDensity.current.fontScale > 1.3f
            val cells: @Composable (List<SummaryStat>) -> Unit = { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { stat -> StatCell(stat, Modifier.weight(1f)) }
                }
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // At large font sizes two rows of two, so the values wrap instead of overlapping.
                if (large) texts.stats.chunked(2).forEach { cells(it) } else cells(texts.stats)
            }
        }

        val records = texts.records.take(SHEET_RECORDS)
        if (records.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(texts.recordsTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                records.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }

        if (texts.comparison.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(texts.compareTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                texts.comparison.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }

        Button(onClick = actions.onShare, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.summary_share))
        }

        if (summary.routineUpdate != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.summary_routine_updated),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onUndoRoutineUpdate) { Text(stringResource(CoreUiR.string.undo)) }
            }
        } else if (summary.canUpdateRoutine) {
            OutlinedButton(onClick = actions.onUpdateRoutine, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.summary_update_routine))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = actions.onNotFinished) { Text(stringResource(R.string.summary_not_finished)) }
            TextButton(onClick = actions.onClose) { Text(stringResource(R.string.summary_close)) }
        }
    }
}

/** One number, centred in an equal-width column: the value large and bold, the label small and grey. */
@Composable
private fun StatCell(stat: SummaryStat, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stat.value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stat.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

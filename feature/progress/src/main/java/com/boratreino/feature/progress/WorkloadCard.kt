package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.AcwrStatus
import com.boratreino.core.model.WorkloadReport

private const val BASELINE_DAYS = 28

/**
 * Training load: sets in the last 7 days against the 28-day weekly average (the ratio), with a plain
 * meaning for it, plus monotony and strain when they can be computed. Draws nothing without data.
 */
@Composable
internal fun WorkloadCard(workload: WorkloadReport?, modifier: Modifier = Modifier) {
    if (workload == null || workload.status == AcwrStatus.NO_DATA) return
    val dash = stringResource(R.string.progress_dash)
    StatsCard(stringResource(R.string.stats_workload_title), modifier) {
        Text(
            stringResource(R.string.stats_workload_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WorkloadRow(stringResource(R.string.stats_workload_acute), stringResource(R.string.stats_workload_sets, workload.acuteSets.toString()))
        WorkloadRow(
            stringResource(R.string.stats_workload_chronic),
            stringResource(R.string.stats_workload_sets, formatDecimal(workload.chronicWeeklyAverage, 1)),
        )
        WorkloadRow(stringResource(R.string.stats_workload_ratio), workload.acwr?.let { formatDecimal(it, 2) } ?: dash)
        if (workload.status == AcwrStatus.BUILDING_BASELINE) {
            val days = workload.historyDays.coerceIn(0, BASELINE_DAYS)
            StatusLine(stringResource(R.string.stats_workload_baseline, days, BASELINE_DAYS), MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            StatusLine(statusLabel(workload.status), statusColor(workload.status))
        }
        Text(
            statusMeaning(workload.status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        workload.monotony?.let { WorkloadRow(stringResource(R.string.stats_workload_monotony), formatDecimal(it, 2)) }
        workload.strain?.let { WorkloadRow(stringResource(R.string.stats_workload_strain), formatDecimal(it, 0)) }
        if (workload.monotony != null) {
            Text(
                stringResource(R.string.stats_workload_monotony_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkloadRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun statusLabel(status: AcwrStatus): String = stringResource(
    when (status) {
        AcwrStatus.LOW -> R.string.stats_workload_status_low
        AcwrStatus.OPTIMAL -> R.string.stats_workload_status_optimal
        AcwrStatus.HIGH -> R.string.stats_workload_status_high
        AcwrStatus.VERY_HIGH -> R.string.stats_workload_status_very_high
        AcwrStatus.BUILDING_BASELINE, AcwrStatus.NO_DATA -> R.string.stats_workload_status_building
    },
)

@Composable
private fun statusMeaning(status: AcwrStatus): String = stringResource(
    when (status) {
        AcwrStatus.LOW -> R.string.stats_workload_meaning_low
        AcwrStatus.OPTIMAL -> R.string.stats_workload_meaning_optimal
        AcwrStatus.HIGH -> R.string.stats_workload_meaning_high
        AcwrStatus.VERY_HIGH -> R.string.stats_workload_meaning_very_high
        AcwrStatus.BUILDING_BASELINE, AcwrStatus.NO_DATA -> R.string.stats_workload_meaning_building
    },
)

@Composable
private fun statusColor(status: AcwrStatus): Color = when (status) {
    AcwrStatus.OPTIMAL -> MaterialTheme.colorScheme.secondary
    AcwrStatus.LOW, AcwrStatus.HIGH -> StatsPalette.amber()
    AcwrStatus.VERY_HIGH -> MaterialTheme.colorScheme.error
    AcwrStatus.BUILDING_BASELINE, AcwrStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
}

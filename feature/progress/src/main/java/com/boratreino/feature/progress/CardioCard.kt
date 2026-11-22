package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.R as CoreUiR

/**
 * Cardio in the period, per 7-day block: total time, distance and average intensity, a bar chart of the
 * minutes per block and the average speed and intensity. Draws nothing when the period has no cardio.
 */
@Composable
internal fun CardioCard(report: StatsReport, modifier: Modifier = Modifier) {
    val blocks = report.cardioBlocks
    if (blocks.isEmpty()) return
    val totals = remember(blocks) { cardioTotals(blocks) }
    val dash = stringResource(R.string.progress_dash)
    StatsCard(stringResource(R.string.stats_cardio_title), modifier) {
        Text(
            stringResource(R.string.stats_cardio_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardioNumber(durationText(totals.minutes), stringResource(R.string.stats_cardio_time), Modifier.weight(1f))
            CardioNumber(
                if (totals.distanceM > 0) stringResource(CoreUiR.string.cardio_km, formatKmFromMetres(totals.distanceM)) else dash,
                stringResource(R.string.stats_cardio_distance),
                Modifier.weight(1f),
            )
            CardioNumber(
                totals.intensityAvg?.let { formatIntensity(it) } ?: dash,
                stringResource(R.string.stats_cardio_intensity),
                Modifier.weight(1f),
            )
        }
        val bars = remember(blocks) { blocks.map { it.asBar() } }
        val description = pluralStringResource(
            R.plurals.stats_cardio_chart_description,
            blocks.size,
            blocks.size,
            durationText(totals.minutes),
            durationText(blocks.maxOf { it.minutes }),
        )
        WeeklyBars(bars, description)
        if (blocks.size >= 2) {
            val last = blocks.last().weekStart
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BlockLabel(formatShortDate(blocks.first().weekStart, report.to))
                BlockLabel(
                    if (!last.plusDays(6).isBefore(report.to)) {
                        stringResource(R.string.stats_today)
                    } else {
                        formatShortDate(last, report.to)
                    },
                )
            }
        }
        totals.speedKmh?.let { speed ->
            CardioLine(stringResource(R.string.stats_cardio_average_speed, formatSpeed(speed)))
        }
        totals.intensityAvg?.let { intensity ->
            CardioLine(stringResource(R.string.stats_cardio_average_intensity, formatIntensity(intensity)))
        }
    }
}

@Composable
private fun CardioNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BlockLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CardioLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = 28.dp))
}

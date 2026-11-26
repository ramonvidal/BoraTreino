package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.boratreino.core.model.BodyTrendRules
import com.boratreino.core.model.TrendRules
import java.time.LocalDate

/**
 * Waist divided by hips: the latest ratio, a small chart of it over time and its trend. [waistHip] is
 * oldest first, one value per date that has both measurements. Draws nothing when it is empty.
 */
@Composable
internal fun ProportionsCard(waistHip: List<Pair<LocalDate, Double>>, today: LocalDate, modifier: Modifier = Modifier) {
    if (waistHip.isEmpty()) return
    val latest = waistHip.last()
    val trend = remember(waistHip) { TrendRules.verdict(waistHip, minPoints = BodyTrendRules.MIN_BODY_POINTS) }
    StatsCard(stringResource(R.string.stats_proportions_title), modifier) {
        Text(
            stringResource(R.string.stats_proportions_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatDecimal(latest.second, 2),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (waistHip.size >= 2) {
            MeasurementChart(
                waistHip.map { it.second },
                stringResource(R.string.stats_proportions_title),
                digits = 2,
                descriptionRes = R.string.stats_proportions_chart,
            )
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatShortDate(waistHip.first().first, today), style = MaterialTheme.typography.labelMedium, color = muted)
                Text(formatShortDate(latest.first, today), style = MaterialTheme.typography.labelMedium, color = muted)
            }
        }
        Text(
            trendVerdictText(trend, R.string.stats_proportions_amount, digits = 3, noun = TrendNoun.MEASUREMENTS),
            style = MaterialTheme.typography.bodyMedium,
            color = muted(),
        )
    }
}

@Composable
private fun muted() = MaterialTheme.colorScheme.onSurfaceVariant

package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BalanceStats

/** The muted blue-grey of the second part of a balance bar; readable on the white card. */

/** One pair of opposites: the names and the counts of its two sides. */
private data class BalancePair(val firstName: String, val first: Int, val secondName: String, val second: Int)

@Composable
private fun balancePairs(balance: BalanceStats): List<BalancePair> = listOf(
    BalancePair(stringResource(R.string.stats_balance_push), balance.pushSets, stringResource(R.string.stats_balance_pull), balance.pullSets),
    BalancePair(stringResource(R.string.stats_balance_upper), balance.upperSets, stringResource(R.string.stats_balance_lower), balance.lowerSets),
    BalancePair(stringResource(R.string.stats_balance_quads), balance.quadSets, stringResource(R.string.stats_balance_hamstrings), balance.hamstringSets),
).filter { it.first + it.second > 0 }

/** True when at least one pair of opposites has sets to show. */
internal fun hasBalance(balance: BalanceStats): Boolean =
    balance.pushSets + balance.pullSets > 0 ||
        balance.upperSets + balance.lowerSets > 0 ||
        balance.quadSets + balance.hamstringSets > 0

/** Two-part bars of the period's working sets: push vs pull, upper vs lower, quads vs hamstrings. */
@Composable
internal fun BalanceCard(balance: BalanceStats, modifier: Modifier = Modifier) {
    val pairs = balancePairs(balance)
    if (pairs.isEmpty()) return
    StatsCard(stringResource(R.string.stats_balance_title), modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            pairs.forEach { BalanceBar(it) }
        }
        Text(
            stringResource(R.string.stats_balance_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (balance.unclassifiedSets > 0) {
            Text(
                pluralStringResource(R.plurals.stats_balance_unclassified, balance.unclassifiedSets, balance.unclassifiedSets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BalanceBar(pair: BalancePair) {
    val first = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.primaryContainer
    val track = StatsPalette.track()
    val ratio = if (pair.first > 0 && pair.second > 0) {
        stringResource(R.string.stats_balance_ratio, formatOneDecimal(pair.first.toDouble() / pair.second))
    } else {
        null
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.stats_balance_left, pair.firstName, pair.first),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.stats_balance_right, pair.secondName, pair.second),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val radius = CornerRadius(size.height / 2f)
            val total = (pair.first + pair.second).toFloat()
            drawRoundRect(track, Offset.Zero, size, radius)
            val gap = 2.dp.toPx()
            val firstWidth = size.width * pair.first / total
            if (pair.first > 0 && pair.second > 0) {
                val left = (firstWidth - gap / 2f).coerceAtLeast(size.height / 2f)
                val rightStart = left + gap
                drawRoundRect(first, Offset.Zero, Size(left, size.height), radius)
                drawRoundRect(
                    second,
                    Offset(rightStart, 0f),
                    Size((size.width - rightStart).coerceAtLeast(size.height / 2f), size.height),
                    radius,
                )
            } else {
                drawRoundRect(if (pair.first > 0) first else second, Offset.Zero, size, radius)
            }
        }
        if (ratio != null) {
            Text(
                ratio,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

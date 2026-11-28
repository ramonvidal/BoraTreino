package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.ChartRules
import com.boratreino.core.model.EnergyHistory
import com.boratreino.core.model.EnergyPoint
import com.boratreino.core.ui.formatKg
import java.text.NumberFormat
import kotlin.math.max

private val EnergyPlotHeight = 120.dp
private val EnergyAxisGap = 8.dp
private val EnergyTopPadding = 8.dp

/**
 * The daily energy estimate: the 7- and 28-day averages, a bar per day (training days in the accent colour,
 * rest days muted) and what training adds. An estimate from the body data and the logged training, not advice.
 */
@Composable
internal fun EnergyCard(history: EnergyHistory, modifier: Modifier = Modifier) {
    if (history.points.isEmpty()) return
    val format = NumberFormat.getIntegerInstance()
    val last7 = history.last7AverageKcal?.let { stringResource(R.string.stats_energy_last7, format.format(it)) }
    val last28 = history.last28AverageKcal?.let { stringResource(R.string.stats_energy_last28, format.format(it)) }
    val adds = history.averageBurnKcal?.let { stringResource(R.string.stats_energy_training_adds, format.format(it)) }
    val description = stringResource(
        R.string.stats_energy_chart_description,
        history.points.size,
        history.trainedDays,
    )
    StatsCard(stringResource(R.string.stats_energy_title), modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            last7?.let { EnergyNumber(it) }
            last28?.let { EnergyNumber(it) }
        }
        EnergyBars(history.points, description)
        adds?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(
            stringResource(R.string.stats_energy_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnergyNumber(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** One bar per day, oldest first; trained days in the primary colour, rest days muted. */
@Composable
private fun EnergyBars(points: List<EnergyPoint>, description: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val trainedColor = colors.primary
    val restColor = colors.outline
    val gridColor = colors.outlineVariant
    val labelColor = colors.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val scale = remember(points) { ChartRules.niceScale(0.0, points.maxOfOrNull { it.kcal }?.toDouble() ?: 0.0) }
    val tickLabels = remember(scale) { scale.ticks.map { formatKg(it) } }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(EnergyTopPadding + EnergyPlotHeight)
            .semantics { contentDescription = description },
    ) {
        val layouts = tickLabels.map { measurer.measure(it, labelStyle, softWrap = false, maxLines = 1) }
        val labelWidth = layouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
        val left = labelWidth + EnergyAxisGap.toPx()
        val top = EnergyTopPadding.toPx()
        val bottom = size.height
        val plotHeight = max(1f, bottom - top)
        val plotWidth = max(1f, size.width - left)
        val span = max(1.0, scale.max - scale.min)

        scale.ticks.forEachIndexed { index, tick ->
            val y = bottom - ((tick - scale.min) / span).toFloat() * plotHeight
            drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val layout = layouts[index]
            drawText(layout, labelColor, Offset(0f, y - layout.size.height / 2f))
        }

        val slot = plotWidth / max(1, points.size)
        val barWidth = slot * 0.7f
        val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        points.forEachIndexed { index, point ->
            if (point.kcal <= 0) return@forEachIndexed
            val height = ((point.kcal - scale.min) / span).toFloat().coerceIn(0f, 1f) * plotHeight
            drawRoundRect(
                color = if (point.trained) trainedColor else restColor,
                topLeft = Offset(left + slot * index + (slot - barWidth) / 2f, bottom - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}

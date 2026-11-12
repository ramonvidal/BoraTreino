package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.CardioWeek
import com.boratreino.core.model.ChartRules
import com.boratreino.core.ui.formatKg
import kotlin.math.max

private val BarsPlotHeight = 140.dp
private val BarsAxisGap = 8.dp
private val BarsTopPadding = 8.dp

/**
 * Minutes per week as bars, oldest first, the last bar being this week. Drawn on a Canvas; there
 * is no selection, the list under it gives the exact numbers. [description] is the spoken summary.
 */
@Composable
internal fun WeeklyBars(weeks: List<CardioWeek>, description: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val barColor = colors.primary
    val gridColor = colors.outlineVariant
    val labelColor = colors.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scale = remember(weeks) { ChartRules.niceScale(0.0, weeks.maxOfOrNull { it.minutes }?.toDouble() ?: 0.0) }
    val tickLabels = remember(scale) { scale.ticks.map { formatKg(it) } }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(BarsTopPadding + BarsPlotHeight)
            .semantics { contentDescription = description },
    ) {
        val layouts = tickLabels.map { measurer.measure(it, labelStyle, softWrap = false, maxLines = 1) }
        val labelWidth = layouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
        val left = labelWidth + BarsAxisGap.toPx()
        val top = BarsTopPadding.toPx()
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

        val slot = plotWidth / max(1, weeks.size)
        val barWidth = slot * 0.6f
        val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        weeks.forEachIndexed { index, week ->
            if (week.minutes <= 0) return@forEachIndexed
            val height = ((week.minutes - scale.min) / span).toFloat().coerceIn(0f, 1f) * plotHeight
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left + slot * index + (slot - barWidth) / 2f, bottom - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}

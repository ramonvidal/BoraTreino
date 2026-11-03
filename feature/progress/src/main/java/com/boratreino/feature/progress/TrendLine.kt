package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.ui.formatKg

/** "Trend: 100 to 125 kg" for [values] (oldest first). */
@Composable
internal fun trendDescription(metric: ProgressMetric, values: List<Double>): String {
    if (values.isEmpty()) return ""
    val from = formatKg(values.first())
    val to = formatKg(values.last())
    return when (metric) {
        ProgressMetric.WEIGHT -> stringResource(R.string.progress_trend_weight, from, to)
        ProgressMetric.REPS -> stringResource(R.string.progress_trend_reps, from, to)
        ProgressMetric.MINUTES -> stringResource(R.string.progress_trend_minutes, from, to)
    }
}

/** A 72 x 28 dp sparkline: no axes, no labels; the last point is a primary dot. */
@Composable
internal fun TrendLine(values: List<Double>, description: String, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.outline
    val dotColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .size(width = 72.dp, height = 28.dp)
            .semantics { contentDescription = description },
    ) {
        val dotRadius = 3.dp.toPx()
        val offsets = trendOffsets(values, size.width, size.height, inset = dotRadius + 1.dp.toPx())
        if (offsets.size >= 2) {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                for (i in 1 until offsets.size) lineTo(offsets[i].x, offsets[i].y)
            }
            drawPath(
                path,
                lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        offsets.lastOrNull()?.let { drawCircle(dotColor, dotRadius, it) }
    }
}

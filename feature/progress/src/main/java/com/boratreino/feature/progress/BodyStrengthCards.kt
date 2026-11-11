package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyInsightRules
import com.boratreino.core.model.IndexPoint
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.ui.formatKg
import java.time.LocalDate
import kotlin.math.abs

private val VsChartHeight = 180.dp
private val EndLabelWidth = 48.dp
private const val GRID_LINES = 4
private const val BASE_PERCENT = 100.0
private const val RANGE_PADDING = 0.1
private const val MIN_RANGE = 4.0
private const val WEEK_DAYS_AFTER_START = 6L

/** The body weight to divide by: positive, else none. */
private fun usableBodyWeight(kg: Double?): Double? = kg?.takeIf { it > 0.0 }

/** True when the "strength per body weight" card has something to say (a ranking or the hint). */
internal fun hasRelativeStrength(state: StatsUiState): Boolean {
    val report = state.report ?: return false
    val weight = usableBodyWeight(state.bodyWeightKg) ?: return true
    return BodyInsightRules.relativeStrength(report.strengthNow, weight).isNotEmpty()
}

/** True when the strength-vs-body-weight chart can be drawn: at least two strength points. */
internal fun hasStrengthChart(report: StatsReport): Boolean = report.strengthIndex.size >= 2

/** The machines with the highest estimated 1RM per kg of body weight, or a hint to log the weight. */
@Composable
internal fun RelativeStrengthCard(state: StatsUiState, modifier: Modifier = Modifier) {
    val report = state.report ?: return
    val bodyWeight = usableBodyWeight(state.bodyWeightKg)
    StatsCard(stringResource(R.string.stats_relative_title), modifier) {
        if (bodyWeight == null) {
            Text(
                stringResource(R.string.stats_relative_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StatsCard
        }
        val ranking = BodyInsightRules.relativeStrength(report.strengthNow, bodyWeight)
        val bars = ranking.map { (estimate, ratio) ->
            BarValue(
                estimate.machine.name,
                ratio,
                stringResource(R.string.stats_relative_value, formatOneDecimal(ratio)),
            )
        }
        HorizontalBars(bars)
        Text(
            stringResource(R.string.stats_relative_note, formatKg(bodyWeight)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The kg of the last weight entry on or before [date], or null when there is none. */
private fun weightKgOn(weights: List<WeightEntry>, date: LocalDate): Double? =
    weights.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }?.kg

/** Body weight and strength as two lines, both indexed to 100 at their first point. */
@Composable
internal fun StrengthVsWeightCard(state: StatsUiState, modifier: Modifier = Modifier) {
    val report = state.report ?: return
    val strength = report.strengthIndex
    if (strength.size < 2) return
    val weekStarts = report.weeks.map { it.weekStart }
    val weight = BodyInsightRules.weightIndex(state.weights, weekStarts)
    val strengthChange = strength.last().percent - BASE_PERCENT
    val summary = if (weight.size >= 2) {
        val firstKg = weightKgOn(state.weights, weight.first().weekStart.plusDays(WEEK_DAYS_AFTER_START))
        val lastKg = weightKgOn(state.weights, weight.last().weekStart.plusDays(WEEK_DAYS_AFTER_START))
        val deltaKg = if (firstKg != null && lastKg != null) lastKg - firstKg else 0.0
        stringResource(
            R.string.stats_vs_weight_summary,
            signedPercentNumber(deltaKg),
            signedPercentNumber(weight.last().percent - BASE_PERCENT),
            signedPercentNumber(strengthChange),
        )
    } else {
        stringResource(R.string.stats_vs_weight_summary_strength, signedPercentNumber(strengthChange))
    }
    val strengthName = stringResource(R.string.stats_vs_weight_strength)
    val weightName = stringResource(R.string.stats_vs_weight_body)
    val description = stringResource(R.string.stats_vs_weight_description, report.weeks.size, summary)
    val accent = MaterialTheme.colorScheme.secondary
    val grey = MaterialTheme.colorScheme.primary
    StatsCard(stringResource(R.string.stats_vs_weight_title), modifier) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IndexLinesChart(
                weekStarts = weekStarts,
                lines = buildList {
                    if (weight.size >= 2) add(IndexLine(weight, grey))
                    add(IndexLine(strength, accent))
                },
            )
            WeekAxisLabels(weekStarts)
            LegendRow(accent, strengthName, percentOf(strength.last().percent))
            if (weight.size >= 2) LegendRow(grey, weightName, percentOf(weight.last().percent))
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.stats_vs_weight_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun percentOf(percent: Double): String =
    stringResource(R.string.stats_percent_plain, formatDecimal(percent, 0))

@Composable
private fun WeekAxisLabels(weekStarts: List<LocalDate>) {
    val today = weekStarts.lastOrNull() ?: return
    if (weekStarts.size < 2) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(end = EndLabelWidth), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatShortDate(weekStarts.first(), today), style = MaterialTheme.typography.labelSmall, color = color)
        Text(stringResource(R.string.progress_week_this), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** One line of the chart: points by week plus its colour. */
internal data class IndexLine(val points: List<IndexPoint>, val color: Color)

/**
 * Lines over the weeks of [weekStarts] (x), values in percent (y) with a light grid and the
 * final percent written at the end of each line.
 */
@Composable
internal fun IndexLinesChart(weekStarts: List<LocalDate>, lines: List<IndexLine>, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val baseline = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = lines.map { percentOf(it.points.last().percent) }
    val allValues = lines.flatMap { line -> line.points.map { it.percent } } + BASE_PERCENT
    val rawMin = allValues.min()
    val rawMax = allValues.max()
    val pad = ((rawMax - rawMin) * RANGE_PADDING).coerceAtLeast(MIN_RANGE / 2)
    val low = rawMin - pad
    val high = rawMax + pad
    Canvas(modifier.fillMaxWidth().height(VsChartHeight)) {
        val plotWidth = size.width - EndLabelWidth.toPx()
        val line = 1.dp.toPx()
        fun yOf(percent: Double): Float = (size.height * (1.0 - (percent - low) / (high - low))).toFloat()
        fun xOf(weekStart: LocalDate): Float {
            val index = weekStarts.indexOf(weekStart).coerceAtLeast(0)
            val steps = (weekStarts.size - 1).coerceAtLeast(1)
            return plotWidth * index / steps
        }
        for (i in 0 until GRID_LINES) {
            val y = (size.height - line) * i / (GRID_LINES - 1)
            drawLine(grid, Offset(0f, y + line / 2f), Offset(plotWidth, y + line / 2f), strokeWidth = line)
        }
        val baseY = yOf(BASE_PERCENT)
        drawLine(baseline.copy(alpha = 0.6f), Offset(0f, baseY), Offset(plotWidth, baseY), strokeWidth = line)
        val labelYs = mutableListOf<Float>()
        lines.forEachIndexed { index, series ->
            val stroke = 2.5.dp.toPx()
            val offsets = series.points.map { Offset(xOf(it.weekStart), yOf(it.percent)) }
            offsets.zipWithNext().forEach { (a, b) ->
                drawLine(series.color, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
            }
            val end = offsets.last()
            drawCircle(series.color, radius = 4.dp.toPx(), center = end)
            val measured = measurer.measure(labels[index], labelStyle.copy(color = series.color))
            var top = (end.y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
            labelYs.forEach { other ->
                if (abs(top - other) < measured.size.height) top = other + measured.size.height
            }
            top = top.coerceAtMost(size.height - measured.size.height)
            labelYs.add(top)
            drawText(measured, topLeft = Offset(plotWidth + 8.dp.toPx(), top))
        }
    }
}

package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.MuscleWeek
import com.boratreino.core.model.WeekStats
import com.boratreino.core.ui.label

/**
 * Colors of the Stats charts, all derived from the theme so they follow light and dark mode:
 * the Sunny trio (Brand yellow = primaryContainer, Ink = primary, Ok green = secondary) and tints of
 * them toward the page colour, plus greys. [amber] is the one fixed hue, tuned per brightness for the recency dots.
 */
internal object StatsPalette {
    @Composable
    fun muscleColors(): Map<MuscleGroup, Color> {
        val c = MaterialTheme.colorScheme
        return mapOf(
            MuscleGroup.LEGS to c.primary,
            MuscleGroup.CHEST to c.primaryContainer,
            MuscleGroup.BACK to c.secondary,
            MuscleGroup.SHOULDERS to lerp(c.primary, c.surface, 0.55f),
            MuscleGroup.ARMS to lerp(c.primaryContainer, c.surface, 0.5f),
            MuscleGroup.CORE to lerp(c.secondary, c.surface, 0.5f),
            MuscleGroup.CARDIO to c.outline,
        )
    }

    /** Four steps from the accent to light grey, for the rep ranges. */
    @Composable
    fun rangeColors(): List<Color> {
        val c = MaterialTheme.colorScheme
        return listOf(c.primaryContainer, c.secondary, c.primary, c.outline)
    }

    @Composable
    fun amber(): Color = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color(0xFF9A6B00) else Color(0xFFE0B04A)

    @Composable
    fun track(): Color = MaterialTheme.colorScheme.surfaceContainerHigh
}

private val ChartHeight = 160.dp
private val BarGap = 2.dp
private val LegendDot = 12.dp

/** One horizontal bar: [label] on the left, the bar sized by [value], [valueLabel] on the right. */
internal data class BarValue(val label: String, val value: Double, val valueLabel: String)

/** One slice of a [StackedShareBar]. */
internal data class ShareValue(val label: String, val percent: Double, val percentLabel: String, val color: Color)

/** A legend row: colour dot, name, value. */
@Composable
internal fun LegendRow(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(LegendDot)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Stacked bars, one per week of [weeks] (oldest first), each split by muscle group using the
 * volume in [muscleWeeks]. The first and last week are named under the bars.
 */
@Composable
internal fun StackedWeekBars(weeks: List<WeekStats>, muscleWeeks: List<MuscleWeek>, modifier: Modifier = Modifier) {
    val colors = StatsPalette.muscleColors()
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val byWeek = remember(muscleWeeks) { muscleWeeks.groupBy { it.weekStart } }
    val maxVolume = remember(weeks) { weeks.maxOfOrNull { it.volumeKg } ?: 0.0 }
    val description = stringResource(
        R.string.stats_volume_chart_description,
        weeks.size,
        volumeText(weeks.sumOf { it.volumeKg }),
        volumeText(maxVolume),
    )
    val today = weeks.lastOrNull()?.weekStart
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
            val line = 1.dp.toPx()
            drawLine(baseline, Offset(0f, size.height - line / 2f), Offset(size.width, size.height - line / 2f), strokeWidth = line)
            if (weeks.isEmpty() || maxVolume <= 0.0) return@Canvas
            val plotHeight = size.height - line
            val slot = size.width / weeks.size
            val gap = if (slot > 6.dp.toPx()) BarGap.toPx() else 1.dp.toPx()
            val barWidth = (slot - gap).coerceAtLeast(1f)
            weeks.forEachIndexed { i, week ->
                var bottom = plotHeight
                val parts = byWeek[week.weekStart].orEmpty().sortedBy { it.muscleGroup }
                parts.forEach { part ->
                    val h = (part.volumeKg / maxVolume * plotHeight).toFloat()
                    if (h > 0f) {
                        drawRect(
                            colors.getValue(part.muscleGroup),
                            Offset(i * slot + gap / 2f, bottom - h),
                            Size(barWidth, h),
                        )
                        bottom -= h
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (weeks.size >= 2 && today != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatShortDate(weeks.first().weekStart, today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.progress_week_this),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Horizontal bars sized relative to the largest of [values]; one row per value. */
@Composable
internal fun HorizontalBars(values: List<BarValue>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val track = StatsPalette.track()
    val max = values.maxOfOrNull { it.value } ?: 0.0
    val description = values.joinToString(". ") { "${it.label}: ${it.valueLabel}" }
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        values.forEach { bar ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        bar.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(bar.valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(track, Offset.Zero, size, radius)
                    if (max > 0.0 && bar.value > 0.0) {
                        val w = (size.width * (bar.value / max)).toFloat().coerceAtLeast(size.height)
                        drawRoundRect(accent, Offset.Zero, Size(w, size.height), radius)
                    }
                }
            }
        }
    }
}

/** One bar split into [shares] by percent (left to right), with a legend of label and percent. */
@Composable
internal fun StackedShareBar(shares: List<ShareValue>, modifier: Modifier = Modifier) {
    val track = StatsPalette.track()
    val gapColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val description = shares.joinToString(". ") { "${it.label}: ${it.percentLabel}" }
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(track, Offset.Zero, size, radius)
            val total = shares.sumOf { it.percent }
            if (total <= 0.0) return@Canvas
            val gap = 2.dp.toPx()
            var x = 0f
            shares.forEach { share ->
                if (share.percent <= 0.0) return@forEach
                val w = (size.width * (share.percent / total)).toFloat()
                drawRect(share.color, Offset(x, 0f), Size(w, size.height))
                if (x > 0f) drawRect(gapColor, Offset(x - gap / 2f, 0f), Size(gap, size.height))
                x += w
            }
        }
        shares.forEach { LegendRow(it.color, it.label, it.percentLabel) }
    }
}

/** The legend for [StackedWeekBars]: only the groups that have volume in [muscleWeeks]. */
@Composable
internal fun MuscleLegend(muscleWeeks: List<MuscleWeek>, modifier: Modifier = Modifier) {
    val colors = StatsPalette.muscleColors()
    val groups = muscleWeeks.filter { it.volumeKg > 0 }.map { it.muscleGroup }.distinct().sorted()
    Column(modifier.fillMaxWidth()) {
        groups.forEach { group ->
            val total = muscleWeeks.filter { it.muscleGroup == group }.sumOf { it.volumeKg }
            LegendRow(colors.getValue(group), group.label(), volumeText(total))
        }
    }
}


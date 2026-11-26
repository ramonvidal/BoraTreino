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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.PatternBucket
import com.boratreino.core.model.PatternReport
import com.boratreino.core.model.PatternRules
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val PatternPlotHeight = 120.dp
private const val DAYS_IN_WEEK = 7
private const val REST_SLOTS = 3

/** One column of a pattern chart: its label and the bucket behind it (null when nothing was logged). */
private class PatternSlot(val label: String, val bucket: PatternBucket?)

/**
 * When you lift best: the average difference from each exercise's own typical estimated 1RM, by
 * weekday and by days of rest. Whiskers are the standard error; only buckets that passed the test
 * ([PatternBucket.significant]) get the strong colour, the rest are muted. Marked exploratory. Says
 * how many sessions it needs when [patterns] is not [PatternReport.enough], and "no clear pattern yet"
 * without bars when nothing is [PatternReport.anySignificant]. Draws nothing when [patterns] is null.
 */
@Composable
internal fun PatternsCard(patterns: PatternReport?, modifier: Modifier = Modifier) {
    if (patterns == null) return
    StatsCard(stringResource(R.string.stats_patterns_title), modifier) {
        if (!patterns.enough) {
            Text(
                stringResource(R.string.stats_patterns_needs, PatternRules.MIN_SESSIONS, patterns.sessionsUsed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StatsCard
        }
        ExploratoryHint(stringResource(R.string.stats_patterns_hint))
        if (!patterns.anySignificant) {
            Text(
                stringResource(R.string.stats_patterns_none, patterns.sessionsUsed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StatsCard
        }
        Text(
            stringResource(R.string.stats_patterns_note, patterns.sessionsUsed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val locale = Locale.getDefault()
        val weekdaySlots = (1..DAYS_IN_WEEK).map { day ->
            PatternSlot(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale), patterns.weekday.firstOrNull { it.key == day })
        }
        val restLabels = listOf(
            stringResource(R.string.stats_patterns_rest_short),
            stringResource(R.string.stats_patterns_rest_medium),
            stringResource(R.string.stats_patterns_rest_long),
        )
        val restSlots = (0 until REST_SLOTS).map { key -> PatternSlot(restLabels[key], patterns.rest.firstOrNull { it.key == key }) }
        PatternSection(stringResource(R.string.stats_patterns_weekday), weekdaySlots)
        PatternSection(stringResource(R.string.stats_patterns_rest), restSlots)
        Text(
            stringResource(R.string.stats_patterns_highlight_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PatternSection(title: String, slots: List<PatternSlot>) {
    // stringResource is composable and can't run inside the joinToString lambda: use the context directly.
    val context = LocalContext.current
    val description = slots.filter { it.bucket != null }.joinToString(". ") { slot ->
        val bucket = checkNotNull(slot.bucket)
        context.getString(R.string.stats_patterns_slot_description, slot.label, signedWhole(bucket.meanIndex), bucket.n)
    }
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$title. $description" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PatternBars(slots)
        Row(Modifier.fillMaxWidth()) {
            slots.forEach { slot -> SlotLabels(slot, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SlotLabels(slot: PatternSlot, modifier: Modifier) {
    val bucket = slot.bucket
    val highlighted = bucket != null && bucket.significant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val color = if (highlighted) MaterialTheme.colorScheme.onSurface else muted.copy(alpha = 0.6f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(slot.label, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, textAlign = TextAlign.Center)
        Text(
            if (bucket == null) stringResource(R.string.progress_dash) else stringResource(R.string.stats_percent_plain, signedWhole(bucket.meanIndex)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
        Text(
            bucket?.standardError?.let { stringResource(R.string.stats_patterns_error, formatDecimal(it, 0)) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 1,
        )
        Text(
            if (bucket == null) "" else stringResource(R.string.stats_patterns_count, bucket.n),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 1,
        )
    }
}

/** Bars up (above the exercise's own average) or down (below) from a zero line, with a standard-error whisker. */
@Composable
private fun PatternBars(slots: List<PatternSlot>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val strong = colors.primary
    val greyed = colors.onSurfaceVariant.copy(alpha = 0.35f)
    val whisker = colors.onSurfaceVariant
    val grid = colors.outlineVariant
    val reach = slots.maxOfOrNull { slot ->
        val bucket = slot.bucket ?: return@maxOfOrNull 0.0
        abs(bucket.meanIndex) + (bucket.standardError ?: 0.0)
    } ?: 0.0
    val limit = max(reach, 1.0)
    Canvas(modifier.fillMaxWidth().height(PatternPlotHeight)) {
        val mid = size.height / 2f
        val half = size.height / 2f - 4.dp.toPx()
        val slot = size.width / max(1, slots.size)
        val barWidth = slot * 0.5f
        val whiskerCap = 5.dp.toPx()
        val stroke = 1.5.dp.toPx()
        drawLine(grid, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        slots.forEachIndexed { index, item ->
            val bucket = item.bucket ?: return@forEachIndexed
            val highlighted = bucket.significant
            val centre = slot * index + slot / 2f
            val barHeight = (abs(bucket.meanIndex) / limit * half).toFloat()
            val top = if (bucket.meanIndex >= 0) mid - barHeight else mid
            if (barHeight > 0f) {
                drawRoundRect(
                    if (highlighted) strong else greyed,
                    Offset(centre - barWidth / 2f, top),
                    Size(barWidth, barHeight),
                    CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
            bucket.standardError?.let { se ->
                val tip = (bucket.meanIndex / limit * half).toFloat()
                val reachPx = (se / limit * half).toFloat()
                val colorLine = if (highlighted) whisker else whisker.copy(alpha = 0.5f)
                val yTop = mid - tip - reachPx
                val yBottom = mid - tip + reachPx
                drawLine(colorLine, Offset(centre, yTop), Offset(centre, yBottom), strokeWidth = stroke)
                drawLine(colorLine, Offset(centre - whiskerCap, yTop), Offset(centre + whiskerCap, yTop), strokeWidth = stroke)
                drawLine(colorLine, Offset(centre - whiskerCap, yBottom), Offset(centre + whiskerCap, yBottom), strokeWidth = stroke)
            }
        }
    }
}

/** "+4" for a rise, "-3" for a fall, "0" for none (whole percent, no unit). */
private fun signedWhole(value: Double): String {
    val whole = value.roundToInt()
    return if (whole > 0) "+$whole" else whole.toString()
}

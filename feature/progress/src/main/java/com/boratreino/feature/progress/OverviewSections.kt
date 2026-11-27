package com.boratreino.feature.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.BodySnapshot
import com.boratreino.core.model.FrequencySummary
import com.boratreino.core.model.HabitSummary
import com.boratreino.core.model.OverviewRules
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StrengthDirection
import com.boratreino.core.model.StrengthRow
import com.boratreino.core.model.StrengthSummary
import com.boratreino.core.model.SuspiciousJump
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.formatKg
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import com.boratreino.core.ui.R as CoreUiR

private val BAR_AREA_HEIGHT = 56.dp
private val BAR_WIDTH = 28.dp

/** A card title with an optional link on the right ("Calendar >") or plain text (the period). */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    link: String? = null,
    onLink: () -> Unit = {},
    trailing: String? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (link != null) {
            Row(
                Modifier.heightIn(min = 48.dp).clickable(onClick = onLink),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(link, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StatsPalette.amber())
                Icon(
                    painterResource(CoreUiR.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = StatsPalette.amber(),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** This week's trainings against the goal, the last 4 weeks as bars, the streak and the next workout. */
@Composable
internal fun FrequencySection(frequency: FrequencySummary, onOpenCalendar: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val weekLabel = stringResource(R.string.frequency_this_week_label)
    val next = frequency.next
    val nextText = when {
        next == null -> null
        next.date == today -> stringResource(R.string.frequency_next_today, next.routine.shortName)
        else -> stringResource(R.string.frequency_next, next.routine.shortName, formatWeekday(next.date))
    }
    val caption = if (nextText == null) weekLabel else "$weekLabel · $nextText"
    val labels = frequency.weeks.map { formatDayMonth(it.weekStart) }
    val barsDescription = stringResource(
        R.string.frequency_bars_description,
        frequency.weeks.mapIndexed { index, week -> "${labels[index]}: ${week.days}" }.joinToString(", "),
    )
    val streakText = if (frequency.streak > 0) {
        pluralStringResource(R.plurals.frequency_streak, frequency.streak, frequency.streak)
    } else {
        null
    }
    BoraCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            stringResource(R.string.frequency_title),
            link = stringResource(R.string.frequency_calendar_link),
            onLink = onOpenCalendar,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(frequency.thisWeek.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.frequency_this_week, frequency.goal),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                val filled = minOf(frequency.thisWeek, frequency.goal)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(frequency.goal) { index ->
                        Box(
                            Modifier
                                .width(26.dp)
                                .height(8.dp)
                                .background(
                                    if (index < filled) colors.primaryContainer else colors.surfaceVariant,
                                    RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }
            }
            val maxDays = frequency.weeks.maxOfOrNull { it.days } ?: 0
            val scale = max(frequency.goal, maxDays).coerceAtLeast(1)
            Row(
                Modifier.clearAndSetSemantics { contentDescription = barsDescription },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                frequency.weeks.forEachIndexed { index, week ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(BAR_AREA_HEIGHT).width(BAR_WIDTH), contentAlignment = Alignment.BottomCenter) {
                            val barHeight = (BAR_AREA_HEIGHT * (week.days.toFloat() / scale)).coerceAtLeast(4.dp)
                            val shape = RoundedCornerShape(8.dp)
                            val barModifier = Modifier.width(BAR_WIDTH).height(barHeight)
                            if (week.isCurrent) {
                                Box(
                                    barModifier
                                        .background(colors.primaryContainer.copy(alpha = 0.35f), shape)
                                        .border(BorderStroke(1.5.dp, colors.primaryContainer), shape),
                                )
                            } else {
                                Box(barModifier.background(colors.primaryContainer, shape))
                            }
                        }
                        Text(labels[index], style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                streakText.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.frequency_goal, frequency.goal),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** Who is going up, who is flat or falling, and which loads to check, for the period. */
@Composable
internal fun StrengthSection(
    summary: StrengthSummary,
    period: StatsPeriod,
    today: LocalDate,
    onOpenMachine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoraCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            stringResource(R.string.strength_section_title),
            trailing = stringResource(R.string.strength_section_subtitle, periodLabel(period)),
        )
        val empty = summary.risers.isEmpty() && summary.flatOrFalling.isEmpty() && summary.toCheck.isEmpty()
        if (empty) {
            Text(
                stringResource(R.string.strength_section_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (summary.risers.isNotEmpty()) {
            GroupLabel(stringResource(R.string.strength_rising))
            summary.risers.forEach { row -> StrengthRowItem(row, onOpenMachine) }
        }
        if (summary.flatOrFalling.isNotEmpty()) {
            GroupLabel(stringResource(R.string.strength_flat))
            summary.flatOrFalling.forEach { row -> StrengthRowItem(row, onOpenMachine) }
        }
        if (summary.toCheck.isNotEmpty()) {
            GroupLabel(stringResource(R.string.strength_check))
            summary.toCheck.forEach { jump -> CheckRowItem(jump, onOpenMachine) }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun StrengthRowItem(row: StrengthRow, onOpenMachine: (Long) -> Unit) {
    val before = formatEstimate(row.before)
    val after = formatEstimate(row.after)
    val sessions = row.sessionsInPeriod
    val top = row.lastTop
    val topWeight = top?.weightKg
    val topReps = top?.reps
    val subtitle = when {
        row.direction == StrengthDirection.RISING -> stringResource(R.string.strength_from_to, before, after)
        row.direction == StrengthDirection.FALLING ->
            pluralStringResource(R.plurals.strength_from_to_sessions, sessions, before, after, sessions)
        topWeight != null && topReps != null ->
            pluralStringResource(R.plurals.strength_top_sessions, sessions, formatKg(topWeight), topReps, sessions)
        else -> stringResource(R.string.strength_from_to, before, after)
    }
    val rounded = row.changePercent.roundToInt()
    val pillText = when {
        rounded > 0 -> "+$rounded%"
        rounded < 0 -> "$rounded%"
        else -> "0%"
    }
    val colors = MaterialTheme.colorScheme
    val (pillContainer, pillContent) = when {
        rounded > 0 -> colors.secondaryContainer to colors.onSecondaryContainer
        rounded < 0 -> colors.errorContainer to colors.onErrorContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenMachine(row.machine.id) }
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.machine.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        ExerciseSparkline(row.sparkline, subtitle)
        Box(
            Modifier.width(60.dp).heightIn(min = 32.dp).background(pillContainer, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(pillText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = pillContent)
        }
        ChevronIcon()
    }
    HorizontalDivider(color = colors.outlineVariant)
}

@Composable
private fun CheckRowItem(jump: SuspiciousJump, onOpenMachine: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val subtitle = pluralStringResource(
        R.plurals.strength_jump,
        jump.days,
        formatKg(jump.fromKg),
        formatKg(jump.toKg),
        jump.days,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenMachine(jump.machine.id) }
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(jump.machine.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Row(
            Modifier.heightIn(min = 32.dp).background(colors.surfaceVariant, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painterResource(CoreUiR.drawable.ic_info),
                contentDescription = null,
                tint = StatsPalette.amber(),
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(R.string.strength_check_marker),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        ChevronIcon()
    }
    HorizontalDivider(color = colors.outlineVariant)
}

@Composable
private fun ChevronIcon() {
    Icon(
        painterResource(CoreUiR.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(24.dp),
    )
}

/** The latest tape measurements as tiles; "Log" opens the sheet, the tiles open the Measurements card. */
@Composable
internal fun BodySection(
    body: BodySnapshot,
    today: LocalDate,
    onLog: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val latest = body.latestDate
    val note = when {
        latest == null -> null
        body.hasTrend -> stringResource(R.string.body_note_trend, formatShortDate(latest, today).removeSuffix("."))
        else -> stringResource(R.string.body_note_once, formatShortDate(latest, today).removeSuffix("."))
    }
    BoraCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            stringResource(R.string.body_section_title),
            link = stringResource(R.string.body_log_link),
            onLink = onLog,
        )
        if (body.tiles.isEmpty()) {
            Text(
                stringResource(R.string.body_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    body.tiles.forEach { tile -> MeasurementTile(tile, Modifier.weight(1f)) }
                    // Keeps tile widths equal when fewer than MAX_TILES are shown.
                    repeat(OverviewRules.MAX_TILES - body.tiles.size) { Spacer(Modifier.weight(1f)) }
                }
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MeasurementTile(tile: BodyMeasurement, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatDecimal(tile.valueCm, 1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            tile.type.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Cardio and session length of the period; a tile with no data is left out. */
@Composable
internal fun HabitSection(habit: HabitSummary, today: LocalDate, modifier: Modifier = Modifier) {
    val cardioTitle = if (habit.cardioSessions > 0) {
        pluralStringResource(R.plurals.habit_cardio, habit.cardioSessions, habit.cardioSessions)
    } else {
        null
    }
    val cardioDetail = if (habit.cardioSessions > 0) {
        val time = durationText(habit.cardioMinutes)
        if (habit.cardioDistanceM > 0) {
            time + " · " + stringResource(CoreUiR.string.cardio_km, formatKmFromMetres(habit.cardioDistanceM))
        } else {
            time
        }
    } else {
        null
    }
    val average = habit.averageSessionMinutes
    val lengthTitle = if (average != null) stringResource(R.string.habit_session_length, average) else null
    val between = habit.minutesBetweenSets
    val lengthDetail = if (between != null) stringResource(R.string.habit_between_sets, formatDecimal(between, 1)) else null
    BoraCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            stringResource(R.string.habit_section_title),
            trailing = stringResource(R.string.habit_since, formatShortDate(habit.since, today)),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cardioTitle != null) HabitTile(cardioTitle, cardioDetail, Modifier.weight(1f))
            if (lengthTitle != null) HabitTile(lengthTitle, lengthDetail, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HabitTile(title: String, detail: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** An outlined card that opens the Analysis level. */
@Composable
internal fun AnalysisRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.analysis_row_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.analysis_row_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            ChevronIcon()
        }
    }
}

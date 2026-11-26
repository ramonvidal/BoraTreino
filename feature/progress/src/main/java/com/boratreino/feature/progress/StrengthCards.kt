package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.MachineTrend
import com.boratreino.core.model.MuscleVolume
import com.boratreino.core.model.MuscleVolumeRules
import com.boratreino.core.model.TrendVerdict
import com.boratreino.core.model.VolumeBand
import com.boratreino.core.ui.label

/** Names listed per verdict group before "and N more". */
internal const val TREND_NAMES_SHOWN = 3

/** The verdict groups in display order. */
private val TREND_ORDER = listOf(
    TrendVerdict.RISING,
    TrendVerdict.PLATEAU,
    TrendVerdict.FALLING,
    TrendVerdict.NOT_ENOUGH_DATA,
)

/** Rising / Plateau / Falling / Needs more data: a count each and the first few names with their weekly change; a name opens the machine. */
@Composable
internal fun TrendSummaryCard(
    trends: List<MachineTrend>,
    onOpenMachine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kg = stringResource(R.string.trend_unit_kg)
    StatsCard(stringResource(R.string.stats_trends_title), modifier) {
        NoteLine(stringResource(R.string.stats_trends_note))
        TREND_ORDER.forEach { verdict ->
            val group = trends.filter { it.result.verdict == verdict }
            if (group.isEmpty()) return@forEach
            Text(
                stringResource(R.string.stats_trends_group, trendGroupLabel(verdict), group.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Machines that still need data have no slope worth showing, so only the count says it.
            if (verdict != TrendVerdict.NOT_ENOUGH_DATA) {
                group.take(TREND_NAMES_SHOWN).forEach { trend ->
                    val fit = trend.result.fit
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMachine(trend.machineId) }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            trend.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (fit != null) {
                            Text(
                                slopeText(fit.slopePerWeek, kg, ::formatEstimate),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (group.size > TREND_NAMES_SHOWN) {
                    NoteLine(stringResource(R.string.stats_trends_more, group.size - TREND_NAMES_SHOWN))
                }
            }
        }
    }
}

@Composable
private fun trendGroupLabel(verdict: TrendVerdict): String = stringResource(
    when (verdict) {
        TrendVerdict.RISING -> R.string.trend_group_rising
        TrendVerdict.FALLING -> R.string.trend_group_falling
        TrendVerdict.PLATEAU -> R.string.trend_group_plateau
        TrendVerdict.NOT_ENOUGH_DATA -> R.string.trend_group_not_enough
    },
)

/** Multiple of the upper guideline the bars run to, so volume above it still has room. */
private const val BAR_HEADROOM = 1.5

/** One row per muscle: weekly direct sets, a bar over the 10-20 guideline zone, and the band in words. */
@Composable
internal fun MuscleVolumeCard(volumes: List<MuscleVolume>, modifier: Modifier = Modifier) {
    val maxScale = maxOf(MuscleVolumeRules.HIGH * BAR_HEADROOM, volumes.maxOfOrNull { it.setsPerWeek } ?: 0.0)
    val accent = MaterialTheme.colorScheme.primary
    val amber = StatsPalette.amber()
    val track = StatsPalette.track()
    val zone = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    StatsCard(stringResource(R.string.stats_muscle_volume_title), modifier) {
        NoteLine(stringResource(R.string.stats_muscle_volume_guideline))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            volumes.forEach { item ->
                val name = item.muscle.label()
                val sets = formatOneDecimal(item.setsPerWeek)
                val band = muscleBandLabel(item.band)
                val color = if (item.band == VolumeBand.ABOVE) amber else accent
                Column(
                    Modifier.clearAndSetSemantics {
                        contentDescription = "$name: ${sets} / $band"
                    },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.band == VolumeBand.NONE) muted else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.stats_muscle_volume_row, sets, band),
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                        val radius = CornerRadius(size.height / 2f)
                        drawRoundRect(track, Offset.Zero, size, radius)
                        val from = (size.width * (MuscleVolumeRules.LOW / maxScale)).toFloat()
                        val to = (size.width * (MuscleVolumeRules.HIGH / maxScale)).toFloat()
                        drawRoundRect(zone, Offset(from, 0f), Size(to - from, size.height), CornerRadius(2f))
                        if (item.setsPerWeek > 0.0) {
                            val w = (size.width * (item.setsPerWeek / maxScale)).toFloat().coerceAtLeast(size.height)
                            drawRoundRect(color, Offset.Zero, Size(w.coerceAtMost(size.width), size.height), radius)
                        }
                    }
                }
            }
        }
    }
}

/** "No sets", "Below 10", "In range" or "Above 20". */
@Composable
private fun muscleBandLabel(band: VolumeBand): String = when (band) {
    VolumeBand.NONE -> stringResource(R.string.stats_muscle_volume_none)
    VolumeBand.BELOW -> stringResource(R.string.stats_muscle_volume_below, formatDecimal(MuscleVolumeRules.LOW, 0))
    VolumeBand.IN_RANGE -> stringResource(R.string.stats_muscle_volume_in_range)
    VolumeBand.ABOVE -> stringResource(R.string.stats_muscle_volume_above, formatDecimal(MuscleVolumeRules.HIGH, 0))
}

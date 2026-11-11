package com.boratreino.feature.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyInsightRules
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.label
import com.boratreino.core.ui.theme.Sunny

/** Working sets this week per muscle group: the report's current (last) week; zero for groups not trained. */
internal fun weekSetsByGroup(report: StatsReport): Map<MuscleGroup, Int> {
    val weekStart = report.weeks.lastOrNull()?.weekStart ?: return MuscleGroup.entries.associateWith { 0 }
    val sets = report.muscleWeeks.filter { it.weekStart == weekStart }.groupBy { it.muscleGroup }
    return MuscleGroup.entries.associateWith { group -> sets[group].orEmpty().sumOf { it.workingSets } }
}

/** One row of seven rounded squares, shaded by the average effective sets a week over the period. */
@Composable
internal fun WeekMusclesCard(report: StatsReport, modifier: Modifier = Modifier) {
    val load = BodyInsightRules.muscleLoad(report)
    val direct = BodyInsightRules.directLoad(report)
    val names = MuscleGroup.entries.associateWith { it.label() }
    val description = MuscleGroup.entries.map { group ->
        stringResource(
            R.string.stats_week_muscle_sets,
            names.getValue(group),
            formatOneDecimal(load.getValue(group)),
            formatOneDecimal(direct.getValue(group)),
        )
    }.joinToString(", ")
    StatsCard(stringResource(R.string.stats_week_muscles_title), modifier) {
        NoteLine(stringResource(R.string.stats_week_muscles_note))
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MuscleGroup.entries.forEach { group ->
                MuscleSquare(names.getValue(group), load.getValue(group), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MuscleSquare(name: String, sets: Double, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shade = BodyInsightRules.shade(sets)
    val fill: Color = when (shade) {
        0 -> Color.Transparent
        1 -> Sunny.BrandSoft
        2 -> colors.primaryContainer
        else -> colors.secondary
    }
    val numberColor = if (shade >= 3) colors.onSecondary else colors.onSurface
    val shape = RoundedCornerShape(10.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .heightIn(min = 36.dp)
                .background(fill, shape)
                .then(if (shade == 0) Modifier.border(BorderStroke(1.5.dp, colors.outlineVariant), shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(formatOneDecimal(sets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = numberColor)
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

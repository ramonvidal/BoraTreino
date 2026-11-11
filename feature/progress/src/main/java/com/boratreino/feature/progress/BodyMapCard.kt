package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyInsightRules
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.label
import com.boratreino.core.ui.theme.Sunny

/** The groups the body map paints; CARDIO has no place on a body. */
private val MapGroups = listOf(
    MuscleGroup.LEGS,
    MuscleGroup.CHEST,
    MuscleGroup.BACK,
    MuscleGroup.SHOULDERS,
    MuscleGroup.ARMS,
    MuscleGroup.CORE,
)

private const val MIN_SHADE = 1
private const val MAX_SHADE = 3

/** Average effective sets a week over the period per muscle group, drawn on a front and a back figure. */
@Composable
internal fun BodyMapCard(report: StatsReport, modifier: Modifier = Modifier) {
    val sets = BodyInsightRules.muscleLoad(report)
    val names = MapGroups.associateWith { it.label() }
    val direct = BodyInsightRules.directLoad(report)
    val description = MapGroups.map { group ->
        stringResource(
            R.string.stats_week_muscle_sets,
            names.getValue(group),
            formatOneDecimal(sets.getValue(group)),
            formatOneDecimal(direct.getValue(group)),
        )
    }.joinToString(", ")
    val shades = MapGroups.associateWith { BodyInsightRules.shade(sets.getValue(it)) }
    val most = MapGroups.maxByOrNull { sets.getValue(it) }
    val least = MapGroups.minByOrNull { sets.getValue(it) }
    StatsCard(stringResource(R.string.stats_body_title), modifier) {
        NoteLine(stringResource(R.string.stats_body_note))
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BodyFigure(stringResource(R.string.stats_body_front), shades, front = true, modifier = Modifier.weight(1f))
            BodyFigure(stringResource(R.string.stats_body_back), shades, front = false, modifier = Modifier.weight(1f))
        }
        BodyMapLegend()
        if (most != null && least != null && sets.getValue(most) > 0 && most != least) {
            Text(
                stringResource(
                    R.string.stats_body_extremes,
                    names.getValue(most),
                    formatOneDecimal(sets.getValue(most)),
                    names.getValue(least),
                    formatOneDecimal(sets.getValue(least)),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun NoteLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Index 0 is "no sets" (drawn as an outline only); 1 to 3 are the filled steps: light yellow, yellow, dark yellow. */
@Composable
private fun bodyShadeColors(): List<Color> {
    val c = MaterialTheme.colorScheme
    return listOf(Color.Transparent, Sunny.BrandSoft, c.primaryContainer, Sunny.BrandDeep)
}

@Composable
private fun BodyFigure(label: String, shades: Map<MuscleGroup, Int>, front: Boolean, modifier: Modifier = Modifier) {
    val fills = bodyShadeColors()
    val outline = MaterialTheme.colorScheme.outline
    val neutralFill = MaterialTheme.colorScheme.surfaceContainerHigh
    val shapes = if (front) BodyMapShapes.front else BodyMapShapes.back
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(BodyMapShapes.WIDTH / BodyMapShapes.HEIGHT)) {
            val scale = size.width / BodyMapShapes.WIDTH
            val thin = Stroke(0.75.dp.toPx(), join = StrokeJoin.Round)
            val thick = Stroke(1.25.dp.toPx(), join = StrokeJoin.Round)
            for (shape in shapes) {
                val path = Path()
                val p = shape.points
                path.moveTo(p[0] * scale, p[1] * scale)
                var i = 2
                while (i < p.size) {
                    path.lineTo(p[i] * scale, p[i + 1] * scale)
                    i += 2
                }
                path.close()
                val group = shape.group
                val shade = if (group == null) 0 else shades.getValue(group).coerceIn(0, MAX_SHADE)
                when {
                    group == null -> {
                        drawPath(path, neutralFill, style = Fill)
                        drawPath(path, outline, style = thin)
                    }
                    shade >= MIN_SHADE -> {
                        drawPath(path, fills[shade], style = Fill)
                        // A thin outline keeps light shades visible against the white card.
                        drawPath(path, outline.copy(alpha = 0.6f), style = thin)
                    }
                    else -> drawPath(path, outline, style = thick)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BodyMapLegend() {
    val fills = bodyShadeColors()
    val outline = MaterialTheme.colorScheme.outline
    val labels = listOf(
        stringResource(R.string.stats_body_legend_0),
        stringResource(R.string.stats_body_legend_1),
        stringResource(R.string.stats_body_legend_2),
        stringResource(R.string.stats_body_legend_3),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { colIndex, text ->
                    LegendSwatch(fills[rowIndex * 2 + colIndex], outline, text, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(fill: Color, outline: Color, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(14.dp)) {
            val radius = CornerRadius(3.dp.toPx())
            if (fill == Color.Transparent) {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    outline,
                    Offset(stroke / 2f, stroke / 2f),
                    Size(size.width - stroke, size.height - stroke),
                    radius,
                    Stroke(stroke),
                )
            } else {
                drawRoundRect(fill, Offset.Zero, size, radius)
                drawRoundRect(outline.copy(alpha = 0.5f), Offset.Zero, size, radius, Stroke(0.75.dp.toPx()))
            }
        }
        Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
    }
}

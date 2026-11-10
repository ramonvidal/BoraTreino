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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyInsightRules
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.label

/** The groups the body map paints; CARDIO has no place on a body. */
private val MapGroups = listOf(
    MuscleGroup.LEGS,
    MuscleGroup.CHEST,
    MuscleGroup.BACK,
    MuscleGroup.SHOULDERS,
    MuscleGroup.ARMS,
    MuscleGroup.CORE,
)

private const val FIGURE_WIDTH = 100f
private const val FIGURE_HEIGHT = 220f
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

/** Index 0 is "no sets" (drawn as an outline only); 1 to 3 are the filled steps, light to solid. */
@Composable
private fun bodyShadeColors(): List<Color> {
    val c = MaterialTheme.colorScheme
    return listOf(Color.Transparent, lerp(c.surface, c.primary, 0.25f), lerp(c.surface, c.primary, 0.55f), c.primary)
}

@Composable
private fun BodyFigure(label: String, shades: Map<MuscleGroup, Int>, front: Boolean, modifier: Modifier = Modifier) {
    val fills = bodyShadeColors()
    val outline = MaterialTheme.colorScheme.outline
    val headFill = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(FIGURE_WIDTH / FIGURE_HEIGHT)) {
            val scale = size.width / FIGURE_WIDTH
            val stroke = Stroke(1.5.dp.toPx())
            fun shadeOf(group: MuscleGroup): Int = shades.getValue(group).coerceIn(0, MAX_SHADE)

            val figure = Figure(this, scale, fills, outline, stroke)
            // Head, neck and pelvis: neutral light grey with an outline, never shaded.
            drawCircle(headFill, radius = 14f * scale, center = Offset(50f * scale, 17f * scale))
            drawCircle(outline, radius = 14f * scale, center = Offset(50f * scale, 17f * scale), style = stroke)
            figure.neutral(45f, 30f, 10f, 12f, 3f, headFill)
            figure.neutral(33f, 123f, 34f, 18f, 8f, headFill)
            if (front) {
                figure.roundRect(shadeOf(MuscleGroup.CHEST), 34f, 41f, 32f, 38f, 8f)
                figure.roundRect(shadeOf(MuscleGroup.CORE), 35f, 82f, 30f, 38f, 8f)
            } else {
                figure.roundRect(shadeOf(MuscleGroup.BACK), 34f, 41f, 32f, 79f, 10f)
            }
            figure.circle(shadeOf(MuscleGroup.SHOULDERS), 22f, 49f, 11f)
            figure.circle(shadeOf(MuscleGroup.SHOULDERS), 78f, 49f, 11f)
            figure.roundRect(shadeOf(MuscleGroup.ARMS), 6f, 62f, 14f, 70f, 7f)
            figure.roundRect(shadeOf(MuscleGroup.ARMS), 80f, 62f, 14f, 70f, 7f)
            figure.roundRect(shadeOf(MuscleGroup.LEGS), 33f, 138f, 16f, 78f, 8f)
            figure.roundRect(shadeOf(MuscleGroup.LEGS), 51f, 138f, 16f, 78f, 8f)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Draws shapes given in the 100 x 220 figure grid: filled by shade, or outlined when the shade is 0. */
private class Figure(
    private val scope: DrawScope,
    private val scale: Float,
    private val fills: List<Color>,
    private val outline: Color,
    private val stroke: Stroke,
) {
    fun roundRect(shade: Int, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        paint(shade) { color, style ->
            drawRoundRect(
                color,
                Offset(x * scale, y * scale),
                Size(w * scale, h * scale),
                CornerRadius(radius * scale),
                style,
            )
        }
    }

    fun neutral(x: Float, y: Float, w: Float, h: Float, radius: Float, fill: Color) {
        val topLeft = Offset(x * scale, y * scale)
        val size = Size(w * scale, h * scale)
        scope.drawRoundRect(fill, topLeft, size, CornerRadius(radius * scale), Fill)
        scope.drawRoundRect(outline, topLeft, size, CornerRadius(radius * scale), stroke)
    }

    fun circle(shade: Int, cx: Float, cy: Float, r: Float) {
        paint(shade) { color, style -> drawCircle(color, r * scale, Offset(cx * scale, cy * scale), style = style) }
    }

    private fun paint(shade: Int, draw: DrawScope.(Color, DrawStyle) -> Unit) {
        if (shade >= MIN_SHADE) {
            scope.draw(fills[shade], Fill)
            // A thin outline keeps light shades visible against the white card.
            scope.draw(outline.copy(alpha = 0.5f), Stroke(stroke.width / 2f))
        } else {
            scope.draw(outline, stroke)
        }
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

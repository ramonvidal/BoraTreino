package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyInsightRules
import com.boratreino.core.model.Muscle
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.label
import com.boratreino.core.ui.theme.Sunny

private const val MIN_SHADE = 1
private const val MAX_SHADE = 3

/**
 * Average effective sets a week over the period per specific muscle, drawn on a front and a back figure.
 * Tapping a region shows that muscle's number below the figures; tapping it again (or empty space) clears it.
 */
@Composable
internal fun BodyMapCard(report: StatsReport, modifier: Modifier = Modifier) {
    val sets = BodyInsightRules.muscleDetailLoad(report)
    val direct = BodyInsightRules.directDetailLoad(report)
    val names = Muscle.entries.associateWith { it.label() }
    val description = Muscle.entries.map { muscle ->
        stringResource(
            R.string.stats_week_muscle_sets,
            names.getValue(muscle),
            formatOneDecimal(sets.getValue(muscle)),
            formatOneDecimal(direct.getValue(muscle)),
        )
    }.joinToString(", ")
    val shades = Muscle.entries.associateWith { BodyInsightRules.shadeMuscle(sets.getValue(it)) }
    val most = Muscle.entries.maxByOrNull { sets.getValue(it) }
    val least = Muscle.entries.minByOrNull { sets.getValue(it) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = Muscle.entries.firstOrNull { it.name == selectedName }
    val onTap: (Muscle?) -> Unit = { tapped -> selectedName = if (tapped == null || tapped == selected) null else tapped.name }
    StatsCard(stringResource(R.string.stats_body_title), modifier) {
        NoteLine(stringResource(R.string.stats_body_note))
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BodyFigure(stringResource(R.string.stats_body_front), shades, selected, onTap, front = true, modifier = Modifier.weight(1f))
            BodyFigure(stringResource(R.string.stats_body_back), shades, selected, onTap, front = false, modifier = Modifier.weight(1f))
        }
        if (selected != null) {
            Text(
                stringResource(
                    R.string.stats_body_muscle_detail,
                    names.getValue(selected),
                    formatOneDecimal(sets.getValue(selected)),
                    formatOneDecimal(direct.getValue(selected)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
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

/**
 * The muscle drawn at ([x], [y]) in pixels, where [shapes] are on the 100 x 220 grid scaled by [scale];
 * null for empty space or for pieces without a muscle (head, knees, hands, feet). Later shapes are on top.
 */
internal fun muscleAt(x: Float, y: Float, shapes: List<BodyShape>, scale: Float): Muscle? {
    for (i in shapes.indices.reversed()) {
        val shape = shapes[i]
        if (containsPoint(shape.points, x / scale, y / scale)) return shape.muscle
    }
    return null
}

/** Even-odd ray casting over a flat x, y polygon. */
private fun containsPoint(points: FloatArray, x: Float, y: Float): Boolean {
    val n = points.size / 2
    var inside = false
    var j = n - 1
    for (i in 0 until n) {
        val xi = points[2 * i]
        val yi = points[2 * i + 1]
        val xj = points[2 * j]
        val yj = points[2 * j + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
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
private fun BodyFigure(
    label: String,
    shades: Map<Muscle, Int>,
    selected: Muscle?,
    onTap: (Muscle?) -> Unit,
    front: Boolean,
    modifier: Modifier = Modifier,
) {
    val fills = bodyShadeColors()
    val outline = MaterialTheme.colorScheme.outline
    val ink = Sunny.Ink
    val neutralFill = MaterialTheme.colorScheme.surfaceContainerHigh
    val shapes = if (front) BodyMapShapes.front else BodyMapShapes.back
    // The tap gesture below is keyed on the shapes only, so it must read the latest onTap (it knows which muscle is selected).
    val currentOnTap by rememberUpdatedState(onTap)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(BodyMapShapes.WIDTH / BodyMapShapes.HEIGHT)
                .pointerInput(shapes) {
                    detectTapGestures { offset ->
                        currentOnTap(muscleAt(offset.x, offset.y, shapes, size.width / BodyMapShapes.WIDTH))
                    }
                },
        ) {
            val scale = size.width / BodyMapShapes.WIDTH
            val thin = Stroke(0.75.dp.toPx(), join = StrokeJoin.Round)
            val thick = Stroke(1.75.dp.toPx(), join = StrokeJoin.Round)
            val selectedStroke = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round)
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
                val muscle = shape.muscle
                val shade = if (muscle == null) 0 else shades.getValue(muscle).coerceIn(0, MAX_SHADE)
                when {
                    muscle == null -> {
                        drawPath(path, neutralFill, style = Fill)
                        drawPath(path, outline, style = thin)
                    }
                    shade >= MIN_SHADE -> {
                        drawPath(path, fills[shade], style = Fill)
                        // A thin outline keeps light shades visible against the white card.
                        drawPath(path, outline.copy(alpha = 0.6f), style = thin)
                    }
                    else -> drawPath(path, Sunny.Muted.copy(alpha = 0.55f), style = thick)
                }
                if (muscle != null && muscle == selected) drawPath(path, ink, style = selectedStroke)
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

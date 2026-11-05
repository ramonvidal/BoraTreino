package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.boratreino.core.model.ChartRules
import com.boratreino.core.model.ChartScale
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.ui.formatKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

/** The plot is 220 dp tall; the axis and the padding around it come on top. */
private val PlotHeight = 220.dp
private val TopPadding = 8.dp
private val RightPadding = 12.dp
private val AxisGap = 8.dp
private val MarkerDiameter = 8.dp
private val BestMarkerDiameter = 10.dp
private val MarkerRing = 2.dp
private const val MAX_MARKERS = 24
private const val SHORT_SPAN_DAYS = 45L

/** Where the plot sits inside the canvas, and the measured y tick labels. */
private class Frame(val plot: Rect, val tickLayouts: List<TextLayoutResult>)

private fun frame(
    size: Size,
    density: Density,
    measurer: TextMeasurer,
    style: TextStyle,
    tickLabels: List<String>,
    axisHeightPx: Float,
): Frame {
    val layouts = tickLabels.map { measurer.measure(it, style, softWrap = false, maxLines = 1) }
    val labelWidth = layouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
    val left = labelWidth + with(density) { AxisGap.toPx() }
    val top = with(density) { TopPadding.toPx() }
    val right = max(left, size.width - with(density) { RightPadding.toPx() })
    val bottom = max(top, size.height - axisHeightPx)
    return Frame(Rect(left, top, right, bottom), layouts)
}

private fun pointXs(fractions: List<Float>, plot: Rect): List<Float> = fractions.map { xFor(it, plot.left, plot.width) }

/**
 * The line chart of one machine's top value per session. [points] ascending by date; [scale]
 * gives the y ticks; [axisStart]..[axisEnd] is the time window (x is proportional to time).
 * [selectedIndex] shows the crosshair and [tooltip]; tapping or dragging calls [onSelect] with the
 * nearest point, tapping outside the plot calls it with null. [description] is the spoken summary.
 */
@Composable
internal fun ProgressChart(
    points: List<SessionPoint>,
    scale: ChartScale,
    axisStart: LocalDate,
    axisEnd: LocalDate,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    tooltip: String?,
    description: String,
    modifier: Modifier = Modifier,
    valueFormat: (Double) -> String = { formatKg(it) },
) {
    val colors = MaterialTheme.colorScheme
    val lineColor = colors.primary
    val ringColor = colors.surfaceContainerLowest
    val gridColor = colors.outlineVariant
    val crosshairColor = colors.outline
    val valueLabelColor = colors.onSurface
    val axisLabelColor = colors.onSurfaceVariant
    val tooltipBackground = colors.inverseSurface
    val tooltipTextColor = colors.inverseOnSurface
    val labelStyle = MaterialTheme.typography.labelSmall
    val tooltipStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val locale = Locale.getDefault()

    val tickLabels = remember(scale) { scale.ticks.map { formatKg(it) } }
    val xTicks = remember(axisStart, axisEnd) { ChartRules.axisTicks(axisStart, axisEnd) }
    val xTickLabels = remember(xTicks, axisStart, axisEnd) {
        val short = ChronoUnit.DAYS.between(axisStart, axisEnd) < SHORT_SPAN_DAYS
        val formatter = DateTimeFormatter.ofPattern(if (short) "d MMM" else "MMM", locale)
        xTicks.map { formatter.format(it) }
    }
    val xTickFractions = remember(xTicks, axisStart, axisEnd) { ChartRules.timeFractions(xTicks, axisStart, axisEnd) }
    val fractions = remember(points, axisStart, axisEnd) { ChartRules.timeFractions(points.map { it.date }, axisStart, axisEnd) }

    val labelHeightPx = remember(labelStyle, density) { measurer.measure("0", labelStyle).size.height.toFloat() }
    val axisHeightPx = labelHeightPx + with(density) { (AxisGap + 4.dp).toPx() }
    val totalHeight = TopPadding + PlotHeight + with(density) { axisHeightPx.toDp() }

    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Box(modifier.fillMaxWidth().height(totalHeight)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .semantics { contentDescription = description }
                .pointerInput(points, fractions, tickLabels) {
                    detectTapGestures { tap ->
                        val f = frame(size.toSize(), this, measurer, labelStyle, tickLabels, axisHeightPx)
                        val hitArea = f.plot.inflate(AxisGap.toPx())
                        val index = if (hitArea.contains(tap)) nearestIndex(pointXs(fractions, f.plot), tap.x) else null
                        currentOnSelect(if (index != null && index != currentSelected) index else null)
                    }
                }
                .pointerInput(points, fractions, tickLabels) {
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            val f = frame(size.toSize(), this, measurer, labelStyle, tickLabels, axisHeightPx)
                            nearestIndex(pointXs(fractions, f.plot), start.x)?.let { currentOnSelect(it) }
                        },
                        onHorizontalDrag = { change, _ ->
                            val f = frame(size.toSize(), this, measurer, labelStyle, tickLabels, axisHeightPx)
                            nearestIndex(pointXs(fractions, f.plot), change.position.x)?.let { currentOnSelect(it) }
                        },
                    )
                },
        ) {
            val f = frame(size, this, measurer, labelStyle, tickLabels, axisHeightPx)
            val plot = f.plot
            val hairline = 1.dp.toPx()

            // Horizontal hairlines and the y tick labels.
            scale.ticks.forEachIndexed { i, tick ->
                val y = yFor(tick, scale, plot.top, plot.height)
                drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = hairline)
                val layout = f.tickLayouts[i]
                val textY = clampStart(y - layout.size.height / 2f, layout.size.height.toFloat(), 0f, plot.bottom)
                drawText(layout, axisLabelColor, Offset(plot.left - AxisGap.toPx() - layout.size.width, textY))
            }

            // The x axis: month names (or days) at the model's tick dates.
            val xLayouts = xTickLabels.map { measurer.measure(it, labelStyle, softWrap = false, maxLines = 1) }
            val lefts = layoutAxisLabels(
                centers = xTickFractions.map { xFor(it, plot.left, plot.width) },
                widths = xLayouts.map { it.size.width.toFloat() },
                min = 0f,
                max = size.width,
                gap = AxisGap.toPx(),
            )
            xLayouts.forEachIndexed { i, layout ->
                lefts[i]?.let { drawText(layout, axisLabelColor, Offset(it, plot.bottom + AxisGap.toPx() / 2f)) }
            }

            if (points.isEmpty()) return@Canvas
            val xs = pointXs(fractions, plot)
            val ys = points.map { yFor(it.value, scale, plot.top, plot.height) }
            val centers = xs.indices.map { Offset(xs[it], ys[it]) }
            val selected = selectedIndex?.takeIf { it in points.indices }

            if (selected != null) {
                drawLine(crosshairColor, Offset(xs[selected], plot.top), Offset(xs[selected], plot.bottom), strokeWidth = hairline)
            }

            // The line.
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(centers.first().x, centers.first().y)
                    for (i in 1 until centers.size) lineTo(centers[i].x, centers[i].y)
                }
                drawPath(
                    path,
                    lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // Markers: all of them up to 24 points, otherwise the end one (plus best and selected).
            val best = bestIndex(points.map { it.value })
            val last = points.lastIndex
            val ring = MarkerRing.toPx()
            val regular = MarkerDiameter.toPx() / 2f
            val big = BestMarkerDiameter.toPx() / 2f
            centers.forEachIndexed { i, center ->
                val show = points.size <= MAX_MARKERS || i == last || i == best || i == selected
                if (show) marker(center, if (i == best) big else regular, ring, lineColor, ringColor)
            }

            // Value labels: only the last and the best point; the last wins when they collide.
            val gap = 4.dp.toPx()
            val lastBox = valueLabel(last, centers, points, measurer, labelStyle, regular, gap, plot, size, valueLabelColor, valueFormat, draw = true)
            if (best != null && best != last) {
                val bestBox = valueLabel(best, centers, points, measurer, labelStyle, big, gap, plot, size, valueLabelColor, valueFormat, draw = false)
                if (!bestBox.overlaps(lastBox, gap)) {
                    valueLabel(best, centers, points, measurer, labelStyle, big, gap, plot, size, valueLabelColor, valueFormat, draw = true)
                }
            }

            // The tooltip near the top of the plot.
            if (selected != null && tooltip != null) {
                val layout = measurer.measure(
                    tooltip,
                    tooltipStyle,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = max(0, (size.width - 2 * gap).toInt())),
                )
                val padX = 8.dp.toPx()
                val padY = 5.dp.toPx()
                val boxWidth = layout.size.width + 2 * padX
                val boxHeight = layout.size.height + 2 * padY
                val left = clampStart(xs[selected] - boxWidth / 2f, boxWidth, 0f, size.width)
                val top = plot.top + 2.dp.toPx()
                drawRoundRect(tooltipBackground, Offset(left, top), Size(boxWidth, boxHeight), CornerRadius(6.dp.toPx()))
                drawText(layout, tooltipTextColor, Offset(left + padX, top + padY))
            }
        }

        if (points.size < 2) {
            Text(
                stringResource(R.string.progress_chart_not_enough),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(density) { axisHeightPx.toDp() } + 8.dp)
                    .background(ringColor)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

/** Draws (when [draw]) and returns the box of the value label of point [index]. */
private fun DrawScope.valueLabel(
    index: Int,
    centers: List<Offset>,
    points: List<SessionPoint>,
    measurer: TextMeasurer,
    style: TextStyle,
    markerRadius: Float,
    gap: Float,
    plot: Rect,
    canvas: Size,
    color: Color,
    format: (Double) -> String,
    draw: Boolean,
): LabelBox {
    val layout = measurer.measure(format(points[index].value), style, softWrap = false, maxLines = 1)
    val box = pointLabelBox(
        anchorX = centers[index].x,
        anchorY = centers[index].y,
        width = layout.size.width.toFloat(),
        height = layout.size.height.toFloat(),
        markerRadius = markerRadius,
        gap = gap,
        minX = 0f,
        maxX = canvas.width,
        minY = plot.top - gap,
    )
    if (draw) drawText(layout, color, Offset(box.left, box.top))
    return box
}

/** A filled marker with a ring in the card colour around it. */
private fun DrawScope.marker(center: Offset, radius: Float, ring: Float, color: Color, ringColor: Color) {
    drawCircle(ringColor, radius + ring, center)
    drawCircle(color, radius, center)
}

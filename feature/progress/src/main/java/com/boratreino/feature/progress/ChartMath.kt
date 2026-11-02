package com.boratreino.feature.progress

import androidx.compose.ui.geometry.Offset
import com.boratreino.core.model.ChartScale
import com.boratreino.core.model.ProgressRange
import java.time.LocalDate
import kotlin.math.abs

/*
 * Drawing math for the charts, on plain numbers so it can be tested on the JVM.
 */

/** Pixel y of [value] on [scale]: the scale's max at [top], its min at [top] + [height]. A flat scale draws in the middle. */
internal fun yFor(value: Double, scale: ChartScale, top: Float, height: Float): Float {
    val span = scale.max - scale.min
    if (span <= 0.0) return top + height / 2f
    val fraction = ((value - scale.min) / span).toFloat().coerceIn(0f, 1f)
    return top + height * (1f - fraction)
}

/** Pixel x of a 0..1 time [fraction] inside a plot starting at [left]. */
internal fun xFor(fraction: Float, left: Float, width: Float): Float = left + fraction * width

/** Index of the entry of [xs] closest to [x] (the first one on a tie); null when [xs] is empty. */
internal fun nearestIndex(xs: List<Float>, x: Float): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    xs.forEachIndexed { index, value ->
        val distance = abs(value - x)
        if (distance < bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best
}

/** The start of a [size]-long span that stays inside [min]..[max]; if it cannot fit, [min]. */
internal fun clampStart(start: Float, size: Float, min: Float, max: Float): Float {
    val highest = max - size
    return if (highest < min) min else start.coerceIn(min, highest)
}

/** A rectangle for label collision checks. */
internal data class LabelBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    /** True when the two boxes, each grown by [gap], share any area. */
    fun overlaps(other: LabelBox, gap: Float = 0f): Boolean =
        left - gap < other.right && other.left - gap < right && top - gap < other.bottom && other.top - gap < bottom
}

/**
 * Where a value label goes for a marker at ([anchorX], [anchorY]): centred above it, moved
 * sideways to stay inside [minX]..[maxX], and put below the marker when there is no room above [minY].
 */
internal fun pointLabelBox(
    anchorX: Float,
    anchorY: Float,
    width: Float,
    height: Float,
    markerRadius: Float,
    gap: Float,
    minX: Float,
    maxX: Float,
    minY: Float,
): LabelBox {
    val left = clampStart(anchorX - width / 2f, width, minX, maxX)
    val above = anchorY - markerRadius - gap - height
    val top = if (above >= minY) above else anchorY + markerRadius + gap
    return LabelBox(left, top, width, height)
}

/**
 * Left edges for axis labels centred on [centers] with the given [widths], kept inside
 * [min]..[max]. A label that would come closer than [gap] to the previous drawn one is null (skipped).
 */
internal fun layoutAxisLabels(centers: List<Float>, widths: List<Float>, min: Float, max: Float, gap: Float): List<Float?> {
    val result = ArrayList<Float?>(centers.size)
    var previousRight = Float.NEGATIVE_INFINITY
    centers.forEachIndexed { index, center ->
        val width = widths[index]
        val left = clampStart(center - width / 2f, width, min, max)
        if (left - previousRight >= gap) {
            result += left
            previousRight = left + width
        } else {
            result += null
        }
    }
    return result
}

/** Index of the highest value; the last one on a tie (the most recent session). Null when empty. */
internal fun bestIndex(values: List<Double>): Int? {
    var best: Int? = null
    values.forEachIndexed { index, value ->
        val current = best
        if (current == null || value >= values[current]) best = index
    }
    return best
}

/** The time window the x axis covers: the range's own span ending today; for ALL, from the first point. */
internal fun axisWindow(range: ProgressRange, today: LocalDate, firstDate: LocalDate?): Pair<LocalDate, LocalDate> {
    val start = when (range) {
        ProgressRange.THREE_MONTHS -> today.minusMonths(3)
        ProgressRange.ONE_YEAR -> today.minusYears(1)
        ProgressRange.ALL -> firstDate ?: today
    }
    return start to today
}

/** Points of a small trend line inside a [width] x [height] box, [inset] away from the edges. */
internal fun trendOffsets(values: List<Double>, width: Float, height: Float, inset: Float): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val min = values.min()
    val max = values.max()
    val usableWidth = width - 2f * inset
    val usableHeight = height - 2f * inset
    return values.mapIndexed { index, value ->
        val x = if (values.size == 1) width / 2f else inset + usableWidth * index / (values.size - 1)
        val y = if (max <= min) height / 2f else inset + usableHeight * (1f - ((value - min) / (max - min)).toFloat())
        Offset(x, y)
    }
}

/** The last [count] values, oldest first. */
internal fun recentValues(values: List<Double>, count: Int = 12): List<Double> = values.takeLast(count)

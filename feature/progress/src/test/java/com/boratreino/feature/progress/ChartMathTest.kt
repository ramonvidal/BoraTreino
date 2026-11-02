package com.boratreino.feature.progress

import com.boratreino.core.model.ChartScale
import com.boratreino.core.model.ProgressRange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {
    private val eps = 1e-4
    private val scale = ChartScale(min = 0.0, max = 100.0, ticks = listOf(0.0, 50.0, 100.0))

    private fun assertPixels(expected: Double, actual: Float) = assertEquals(expected, actual.toDouble(), eps)

    @Test
    fun `yFor puts the top of the scale at the top and the bottom at the bottom`() {
        assertPixels(10.0, yFor(100.0, scale, top = 10f, height = 200f))
        assertPixels(210.0, yFor(0.0, scale, top = 10f, height = 200f))
    }

    @Test
    fun `yFor places values in between proportionally`() {
        assertPixels(110.0, yFor(50.0, scale, top = 10f, height = 200f))
        assertPixels(60.0, yFor(75.0, scale, top = 10f, height = 200f))
    }

    @Test
    fun `yFor draws a flat scale in the middle`() {
        val flat = ChartScale(min = 40.0, max = 40.0, ticks = listOf(40.0))
        assertPixels(110.0, yFor(40.0, flat, top = 10f, height = 200f))
    }

    @Test
    fun `xFor maps the start, the middle and the end of the plot`() {
        assertPixels(16.0, xFor(0f, left = 16f, width = 300f))
        assertPixels(166.0, xFor(0.5f, left = 16f, width = 300f))
        assertPixels(316.0, xFor(1f, left = 16f, width = 300f))
    }

    @Test
    fun `nearestIndex picks the closest x`() {
        val xs = listOf(10f, 20f, 30f)
        assertEquals(0, nearestIndex(xs, 12f))
        assertEquals(1, nearestIndex(xs, 19f))
        assertEquals(2, nearestIndex(xs, 26f))
    }

    @Test
    fun `nearestIndex gives the first index on a tie`() {
        assertEquals(1, nearestIndex(listOf(10f, 20f, 30f), 25f))
        assertEquals(0, nearestIndex(listOf(10f, 20f), 15f))
    }

    @Test
    fun `nearestIndex is null for no points`() {
        assertNull(nearestIndex(emptyList(), 50f))
    }

    @Test
    fun `nearestIndex still answers far outside the points with the nearest end`() {
        val xs = listOf(10f, 20f, 30f)
        assertEquals(0, nearestIndex(xs, -5000f))
        assertEquals(2, nearestIndex(xs, 5000f))
    }

    @Test
    fun `clampStart keeps a span that fits where it is`() {
        assertPixels(50.0, clampStart(50f, size = 10f, min = 0f, max = 100f))
    }

    @Test
    fun `clampStart moves a span back inside the bounds`() {
        assertPixels(0.0, clampStart(-5f, size = 10f, min = 0f, max = 100f))
        assertPixels(90.0, clampStart(95f, size = 10f, min = 0f, max = 100f))
    }

    @Test
    fun `clampStart returns the minimum when the span cannot fit`() {
        assertPixels(0.0, clampStart(5f, size = 200f, min = 0f, max = 100f))
    }

    @Test
    fun `label boxes that share area overlap`() {
        val a = LabelBox(0f, 0f, 10f, 10f)
        val b = LabelBox(5f, 5f, 10f, 10f)
        assertTrue(a.overlaps(b))
        assertTrue(b.overlaps(a))
    }

    @Test
    fun `label boxes that only touch do not overlap without a gap`() {
        val a = LabelBox(0f, 0f, 10f, 10f)
        assertFalse(a.overlaps(LabelBox(10f, 0f, 10f, 10f)))
        assertFalse(a.overlaps(LabelBox(0f, 10f, 10f, 10f)))
    }

    @Test
    fun `label boxes closer than the gap overlap`() {
        val a = LabelBox(0f, 0f, 10f, 10f)
        assertTrue(a.overlaps(LabelBox(12f, 0f, 10f, 10f), gap = 4f))
        assertTrue(a.overlaps(LabelBox(0f, 12f, 10f, 10f), gap = 4f))
    }

    @Test
    fun `label boxes far apart do not overlap even with a gap`() {
        val a = LabelBox(0f, 0f, 10f, 10f)
        assertFalse(a.overlaps(LabelBox(30f, 0f, 10f, 10f), gap = 4f))
        assertFalse(a.overlaps(LabelBox(0f, 30f, 10f, 10f), gap = 4f))
    }

    @Test
    fun `a point label sits centred above its marker`() {
        val box = pointLabelBox(
            anchorX = 50f, anchorY = 100f, width = 20f, height = 12f,
            markerRadius = 4f, gap = 4f, minX = 0f, maxX = 200f, minY = 0f,
        )
        assertPixels(40.0, box.left)
        assertPixels(80.0, box.top)
        assertTrue(box.bottom <= 100f - 4f)
    }

    @Test
    fun `a point label stays inside the horizontal bounds`() {
        for (anchor in listOf(0f, 2f, 50f, 198f, 200f)) {
            val box = pointLabelBox(
                anchorX = anchor, anchorY = 100f, width = 20f, height = 12f,
                markerRadius = 4f, gap = 4f, minX = 0f, maxX = 200f, minY = 0f,
            )
            assertTrue("left edge for anchor $anchor", box.left >= 0f)
            assertTrue("right edge for anchor $anchor", box.right <= 200f)
        }
    }

    @Test
    fun `a point label near the top goes below its marker and stays under the top limit`() {
        val box = pointLabelBox(
            anchorX = 50f, anchorY = 10f, width = 20f, height = 12f,
            markerRadius = 4f, gap = 4f, minX = 0f, maxX = 200f, minY = 0f,
        )
        assertTrue(box.top >= 0f)
        assertTrue("label should be below the marker", box.top > 10f)
    }

    @Test
    fun `axis labels are placed centred and kept inside the bounds`() {
        assertEquals(listOf(0f), layoutAxisLabels(listOf(2f), listOf(10f), min = 0f, max = 100f, gap = 4f))
        assertEquals(listOf(190f), layoutAxisLabels(listOf(199f), listOf(10f), min = 0f, max = 200f, gap = 4f))
    }

    @Test
    fun `axis labels too close to the previous drawn one are skipped and the first is kept`() {
        val lefts = layoutAxisLabels(listOf(10f, 12f), listOf(10f, 10f), min = 0f, max = 200f, gap = 4f)
        assertEquals(5f, lefts[0]!!, 1e-4f)
        assertNull(lefts[1])
    }

    @Test
    fun `axis labels far enough apart are all drawn`() {
        val lefts = layoutAxisLabels(listOf(10f, 30f, 100f), listOf(10f, 10f, 10f), min = 0f, max = 200f, gap = 4f)
        assertEquals(3, lefts.size)
        assertTrue(lefts.all { it != null })
        assertEquals(25f, lefts[1]!!, 1e-4f)
    }

    @Test
    fun `axis labels for no centres are empty`() {
        assertTrue(layoutAxisLabels(emptyList(), emptyList(), min = 0f, max = 200f, gap = 4f).isEmpty())
    }

    @Test
    fun `bestIndex is the highest value`() {
        assertEquals(1, bestIndex(listOf(100.0, 125.0, 110.0)))
    }

    @Test
    fun `bestIndex is the last index on a tie`() {
        assertEquals(2, bestIndex(listOf(120.0, 80.0, 120.0)))
    }

    @Test
    fun `bestIndex is null for no values and zero for one value`() {
        assertNull(bestIndex(emptyList()))
        assertEquals(0, bestIndex(listOf(5.0)))
    }

    @Test
    fun `three months back from today`() {
        val today = LocalDate.of(2026, 11, 2)
        val (start, end) = axisWindow(ProgressRange.THREE_MONTHS, today, LocalDate.of(2026, 9, 3))
        assertEquals(LocalDate.of(2026, 8, 2), start)
        assertEquals(today, end)
    }

    @Test
    fun `one year back from today`() {
        val today = LocalDate.of(2026, 11, 2)
        val (start, end) = axisWindow(ProgressRange.ONE_YEAR, today, LocalDate.of(2025, 11, 10))
        assertEquals(LocalDate.of(2025, 11, 2), start)
        assertEquals(today, end)
    }

    @Test
    fun `all starts at the first point`() {
        val today = LocalDate.of(2026, 11, 2)
        val first = LocalDate.of(2025, 11, 10)
        val (start, end) = axisWindow(ProgressRange.ALL, today, first)
        assertEquals(first, start)
        assertEquals(today, end)
    }

    @Test
    fun `all with no points starts today`() {
        val today = LocalDate.of(2026, 11, 2)
        val (start, end) = axisWindow(ProgressRange.ALL, today, null)
        assertEquals(today, start)
        assertEquals(today, end)
    }

    @Test
    fun `a trend of one value sits in the middle`() {
        val offsets = trendOffsets(listOf(100.0), width = 72f, height = 28f, inset = 3f)
        assertEquals(1, offsets.size)
        assertPixels(36.0, offsets[0].x)
        assertPixels(14.0, offsets[0].y)
    }

    @Test
    fun `a trend of equal values is a flat line in the middle`() {
        val offsets = trendOffsets(listOf(100.0, 100.0, 100.0), width = 72f, height = 28f, inset = 3f)
        assertEquals(3, offsets.size)
        offsets.forEach { assertPixels(14.0, it.y) }
        assertPixels(3.0, offsets.first().x)
        assertPixels(69.0, offsets.last().x)
    }

    @Test
    fun `a rising trend starts at the bottom and ends at the top inside the inset`() {
        val offsets = trendOffsets(listOf(100.0, 112.5, 125.0), width = 72f, height = 28f, inset = 3f)
        assertPixels(25.0, offsets.first().y)
        assertPixels(14.0, offsets[1].y)
        assertPixels(3.0, offsets.last().y)
        assertPixels(3.0, offsets.first().x)
        assertPixels(69.0, offsets.last().x)
    }

    @Test
    fun `recent values are the last twelve, oldest first`() {
        val values = (1..20).map { it.toDouble() }
        assertEquals((9..20).map { it.toDouble() }, recentValues(values))
    }

    @Test
    fun `recent values keep fewer than twelve`() {
        assertEquals(listOf(1.0, 2.0, 3.0), recentValues(listOf(1.0, 2.0, 3.0)))
        assertTrue(recentValues(emptyList()).isEmpty())
    }

    @Test
    fun `recent values take the count asked for`() {
        assertEquals(listOf(3.0, 4.0), recentValues(listOf(1.0, 2.0, 3.0, 4.0), count = 2))
    }
}

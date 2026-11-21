package com.boratreino.feature.progress

import com.boratreino.core.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The hit test of the body map: which muscle is under a point, on the plain JVM. */
class MuscleAtTest {
    /** A square on the grid from ([x0], [y0]) with sides of [side]. */
    private fun square(muscle: Muscle?, x0: Float, y0: Float, side: Float = 10f) =
        BodyShape(muscle, floatArrayOf(x0, y0, x0 + side, y0, x0 + side, y0 + side, x0, y0 + side))

    @Test
    fun `a point inside a known region gives its muscle`() {
        // The middle of the front chest on the grid.
        assertEquals(Muscle.CHEST, muscleAt(60.2f, 49.85f, BodyMapShapes.front, 1f))
    }

    @Test
    fun `a point inside a known back region gives its muscle`() {
        assertEquals(Muscle.UPPER_BACK, muscleAt(35.8f, 56.4f, BodyMapShapes.back, 1f))
    }

    @Test
    fun `the scale maps screen pixels to the grid`() {
        // The front chest point of the grid, at twice the size on screen.
        assertEquals(Muscle.CHEST, muscleAt(120.4f, 99.7f, BodyMapShapes.front, 2f))
        assertEquals(Muscle.CHEST, muscleAt(30.1f, 24.925f, BodyMapShapes.front, 0.5f))
    }

    @Test
    fun `a point in empty space gives null`() {
        assertNull(muscleAt(-5f, -5f, BodyMapShapes.front, 1f))
        assertNull(muscleAt(1f, 1f, BodyMapShapes.front, 1f))
        assertNull(muscleAt(50f, 50f, listOf(square(Muscle.CHEST, 0f, 0f)), 1f))
    }

    @Test
    fun `the edge of a region is outside it`() {
        val shapes = listOf(square(Muscle.CHEST, 0f, 0f))

        assertNull(muscleAt(10.5f, 5f, shapes, 1f))
        assertNull(muscleAt(-0.5f, 5f, shapes, 1f))
    }

    @Test
    fun `the head has no muscle`() {
        // The middle of the head on the front of the grid.
        assertNull(muscleAt(49.9f, 13.7f, BodyMapShapes.front, 1f))
    }

    @Test
    fun `where two regions overlap the later one is hit`() {
        val shapes = listOf(square(Muscle.CHEST, 0f, 0f), square(Muscle.ABS, 5f, 5f))

        assertEquals(Muscle.ABS, muscleAt(7f, 7f, shapes, 1f))
        assertEquals(Muscle.CHEST, muscleAt(2f, 2f, shapes, 1f))
        assertEquals(Muscle.ABS, muscleAt(12f, 12f, shapes, 1f))
    }

    @Test
    fun `the later region wins whatever the muscles are`() {
        val shapes = listOf(square(Muscle.ABS, 5f, 5f), square(Muscle.CHEST, 0f, 0f))

        assertEquals(Muscle.CHEST, muscleAt(7f, 7f, shapes, 1f))
    }

    @Test
    fun `a piece without a muscle on top of a region gives null`() {
        val shapes = listOf(square(Muscle.CHEST, 0f, 0f), square(null, 0f, 0f))

        assertNull(muscleAt(5f, 5f, shapes, 1f))
    }

    @Test
    fun `an empty list gives null`() {
        assertNull(muscleAt(5f, 5f, emptyList(), 1f))
    }
}

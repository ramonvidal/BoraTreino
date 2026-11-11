package com.boratreino.feature.progress

import com.boratreino.core.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The body map outlines: the shape rules the body map card relies on, checked against the public shape list. */
class BodyMapShapesTest {
    private val sides = mapOf("front" to BodyMapShapes.front, "back" to BodyMapShapes.back)

    private fun groupsOf(shapes: List<BodyShape>): Set<MuscleGroup?> = shapes.map { it.group }.toSet()

    @Test
    fun `the grid is 100 wide and 220 high`() {
        assertEquals(100f, BodyMapShapes.WIDTH, 0f)
        assertEquals(220f, BodyMapShapes.HEIGHT, 0f)
    }

    @Test
    fun `both sides have shapes`() {
        assertTrue(BodyMapShapes.front.isNotEmpty())
        assertTrue(BodyMapShapes.back.isNotEmpty())
    }

    @Test
    fun `every shape has a whole number of x and y pairs of at least three points`() {
        sides.forEach { (side, shapes) ->
            shapes.forEachIndexed { index, shape ->
                val size = shape.points.size
                assertTrue("$side shape $index has $size values", size >= 6 && size % 2 == 0)
            }
        }
    }

    @Test
    fun `every point lies inside the grid`() {
        sides.forEach { (side, shapes) ->
            shapes.forEachIndexed { index, shape ->
                shape.points.toList().chunked(2).forEach { (x, y) ->
                    assertTrue("$side shape $index x $x is outside 0 to WIDTH", x in 0f..BodyMapShapes.WIDTH)
                    assertTrue("$side shape $index y $y is outside 0 to HEIGHT", y in 0f..BodyMapShapes.HEIGHT)
                }
            }
        }
    }

    @Test
    fun `every muscle group except cardio is drawn on the front or the back`() {
        val drawn = groupsOf(BodyMapShapes.front) + groupsOf(BodyMapShapes.back)
        MuscleGroup.entries.filter { it != MuscleGroup.CARDIO }.forEach { group ->
            assertTrue("$group has no shape", group in drawn)
        }
    }

    @Test
    fun `cardio is never drawn on the body map`() {
        sides.forEach { (side, shapes) ->
            assertTrue("$side draws cardio", shapes.none { it.group == MuscleGroup.CARDIO })
        }
    }

    @Test
    fun `the front has chest and core shapes`() {
        val groups = groupsOf(BodyMapShapes.front)
        assertTrue(MuscleGroup.CHEST in groups)
        assertTrue(MuscleGroup.CORE in groups)
    }

    @Test
    fun `the back has back shapes`() {
        assertTrue(MuscleGroup.BACK in groupsOf(BodyMapShapes.back))
    }

    @Test
    fun `both sides have shoulders, arms and legs`() {
        sides.forEach { (side, shapes) ->
            val groups = groupsOf(shapes)
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.ARMS, MuscleGroup.LEGS).forEach { group ->
                assertTrue("$side has no $group shape", group in groups)
            }
        }
    }
}

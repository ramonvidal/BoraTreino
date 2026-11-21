package com.boratreino.feature.progress

import com.boratreino.core.model.Muscle
import com.boratreino.core.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The body map outlines: the shape rules the body map card relies on, checked against the public shape list. */
class BodyMapShapesTest {
    private val sides = mapOf("front" to BodyMapShapes.front, "back" to BodyMapShapes.back)

    private fun musclesOf(shapes: List<BodyShape>): Set<Muscle> = shapes.mapNotNull { it.muscle }.toSet()

    private fun groupsOf(shapes: List<BodyShape>): Set<MuscleGroup> = musclesOf(shapes).map { it.group }.toSet()

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
    fun `every specific muscle is drawn on the front or the back`() {
        val drawn = musclesOf(BodyMapShapes.front) + musclesOf(BodyMapShapes.back)

        Muscle.entries.forEach { muscle ->
            assertTrue("$muscle has no shape", muscle in drawn)
        }
    }

    @Test
    fun `pieces without a muscle are drawn on both sides, for the head, knees, hands and feet`() {
        sides.forEach { (side, shapes) ->
            assertTrue("$side has no piece without a muscle", shapes.any { it.muscle == null })
        }
    }

    @Test
    fun `the front has chest, abs and quadriceps`() {
        val muscles = musclesOf(BodyMapShapes.front)
        assertTrue(Muscle.CHEST in muscles)
        assertTrue(Muscle.ABS in muscles)
        assertTrue(Muscle.QUADRICEPS in muscles)
    }

    @Test
    fun `the back has upper back, traps, lower back, glutes and hamstrings`() {
        val muscles = musclesOf(BodyMapShapes.back)
        assertTrue(Muscle.UPPER_BACK in muscles)
        assertTrue(Muscle.TRAPS in muscles)
        assertTrue(Muscle.LOWER_BACK in muscles)
        assertTrue(Muscle.GLUTES in muscles)
        assertTrue(Muscle.HAMSTRINGS in muscles)
    }

    @Test
    fun `biceps are on the front and triceps are on both sides`() {
        assertTrue(Muscle.BICEPS in musclesOf(BodyMapShapes.front))
        assertTrue(Muscle.TRICEPS in musclesOf(BodyMapShapes.front))
        assertTrue(Muscle.TRICEPS in musclesOf(BodyMapShapes.back))
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

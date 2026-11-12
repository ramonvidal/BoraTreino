package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryExerciseImagesTest {
    @Test
    fun `a library exercise with a picture gives its slug`() {
        assertEquals("air-bike", LibraryExerciseImages.slugFor("Air_Bike"))
        assertEquals("leg-press", LibraryExerciseImages.slugFor("Leg_Press"))
        assertEquals("zottman-curl-with-dumbbells", LibraryExerciseImages.slugFor("Zottman_Curl"))
        assertEquals("biceps-curls-with-barbell", LibraryExerciseImages.slugFor("Barbell_Curl"))
    }

    @Test
    fun `ids with dashes and underscores in the name resolve`() {
        assertEquals("bench-press", LibraryExerciseImages.slugFor("Barbell_Bench_Press_-_Medium_Grip"))
        assertEquals("ab-rollout-on-knees-with-barbell", LibraryExerciseImages.slugFor("Barbell_Ab_Rollout_-_On_Knees"))
    }

    @Test
    fun `a library exercise with a picture gives a slug that is lowercase letters, digits and hyphens`() {
        val pattern = Regex("[a-z0-9-]+")
        listOf("Air_Bike", "Leg_Press", "Zottman_Curl", "Barbell_Curl", "Concentration_Curls").forEach { id ->
            val slug = LibraryExerciseImages.slugFor(id)
            assertTrue("slug '$slug' for $id has other characters", slug != null && pattern.matches(slug))
        }
    }

    @Test
    fun `an id that is not in the library has no picture`() {
        assertNull(LibraryExerciseImages.slugFor("Not_A_Real_Exercise"))
    }

    @Test
    fun `an id of a library exercise without a picture has none`() {
        assertNull(LibraryExerciseImages.slugFor("Running"))
        assertNull(LibraryExerciseImages.slugFor("Pull-Up"))
    }

    @Test
    fun `a blank id has no picture`() {
        assertNull(LibraryExerciseImages.slugFor(""))
    }
}

package com.boratreino.core.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.LibraryExerciseImages
import com.boratreino.core.model.StarterMachines
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The picture frames as the app packages them: every starter picture and known library picture has both frames. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ExercisePictureAssetsRobolectricTest {
    /** The file names in the app's `exercises` assets folder (merged from the module by the Android test setup). */
    private val packaged: Set<String> by lazy {
        RuntimeEnvironment.getApplication().assets.list("exercises").orEmpty().toSet()
    }

    private fun assertBothFrames(slug: String) {
        assertTrue("missing $slug-1.webp in the app assets", "$slug-1.webp" in packaged)
        assertTrue("missing $slug-2.webp in the app assets", "$slug-2.webp" in packaged)
    }

    @Test
    fun `every starter picture has both frames in the app assets`() {
        val slugs = StarterMachines.all.mapNotNull { it.image }.distinct()
        assertTrue("no starter pictures", slugs.isNotEmpty())
        slugs.forEach { assertBothFrames(it) }
    }

    @Test
    fun `the three starters with pictures added last have both frames`() {
        listOf("upright-barbell-rows", "standing-triceps-extension-2", "lying-triceps-press-with-barbell")
            .forEach { assertBothFrames(it) }
    }

    @Test
    fun `known library pictures have both frames in the app assets`() {
        val ids = listOf("Air_Bike", "Leg_Press", "Zottman_Curl", "Barbell_Curl", "Concentration_Curls", "Barbell_Bench_Press_-_Medium_Grip")
        ids.forEach { id ->
            val slug = LibraryExerciseImages.slugFor(id)
            assertTrue("library exercise $id has no picture", slug != null)
            assertBothFrames(slug!!)
        }
    }
}

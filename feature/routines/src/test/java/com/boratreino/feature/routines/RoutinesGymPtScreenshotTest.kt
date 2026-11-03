package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Your gym" in Baianês, to check the translated text fits; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class RoutinesGymPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `routines gym populated pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = GymSamples.populated.copy(portuguese = true),
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-gym-pt.png")
    }
}

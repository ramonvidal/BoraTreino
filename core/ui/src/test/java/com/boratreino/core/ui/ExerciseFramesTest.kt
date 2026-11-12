package com.boratreino.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Both frames of an exercise picture with the licence credit; nothing at all when there is no picture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h800dp")
class ExerciseFramesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(slug: String?, name: String) {
        compose.setContent {
            BoraTreinoTheme {
                ExerciseFrames(slug = slug, name = name, modifier = Modifier)
            }
        }
    }

    @Test
    fun `a picture shows its start and end frames and the credit`() {
        show("leg-press", "Leg press")

        compose.onNodeWithText("Start").assertExists()
        compose.onNodeWithText("End").assertExists()
        compose.onNodeWithText("Exercise illustrations", substring = true).assertExists()
        compose.onAllNodes(hasContentDescription("Illustration of Leg press")).assertCountEquals(2)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/exercise-frames.png")
    }

    @Test
    fun `no picture shows neither frame nor the credit`() {
        show(null, "My own machine")

        compose.onNodeWithText("Start").assertDoesNotExist()
        compose.onNodeWithText("End").assertDoesNotExist()
        compose.onNodeWithText("Exercise illustrations", substring = true).assertDoesNotExist()
    }
}

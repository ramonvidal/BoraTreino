package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.ExercisePreviewDialog
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The start and end pictures of a machine, in a dialog; light and dark; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ExercisePreviewScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val legPress = Machine(
        id = 1L,
        name = "Leg press",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 5.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun shoot(name: String, dark: Boolean) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    ExercisePreviewDialog(machine = legPress, onDismiss = {})
                }
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `exercise preview dialog light`() {
        shoot("exercise-preview-dialog", dark = false)
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("End").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Exercise illustrations: Everkinetic (Greg Priday), CC BY-SA 4.0", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `exercise preview dialog dark`() {
        shoot("exercise-preview-dialog-dark", dark = true)
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }
}

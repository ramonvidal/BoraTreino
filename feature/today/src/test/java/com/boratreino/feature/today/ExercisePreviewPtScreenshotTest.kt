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

/** The picture dialog in Baianês, to check the labels and the close button; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class ExercisePreviewPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `exercise preview dialog pt`() {
        val legPress = Machine(
            id = 1L,
            name = "Leg press",
            muscleGroup = MuscleGroup.LEGS,
            kind = MachineKind.WEIGHT_REPS,
            stepKg = 5.0,
            setupNote = null,
            restSeconds = null,
            archived = false,
        )
        compose.setContent {
            BoraTreinoTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    ExercisePreviewDialog(machine = legPress, onDismiss = {})
                }
            }
        }
        compose.onNodeWithText("Início").assertIsDisplayed()
        compose.onNodeWithText("Fim").assertIsDisplayed()
        compose.onNodeWithText("Fechar").assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/exercise-preview-dialog-pt.png")
    }
}

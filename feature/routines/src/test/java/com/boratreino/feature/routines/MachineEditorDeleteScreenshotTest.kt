package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** An existing machine in the editor, with the Delete button at the bottom; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineEditorDeleteScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `machine editor shows delete for an existing machine`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineEditorContent(
                        state = MachineEditorUiState(
                            loading = false,
                            machineId = 3L,
                            name = "Lat pulldown",
                            muscleGroup = MuscleGroup.BACK,
                            kind = MachineKind.WEIGHT_REPS,
                            stepKg = 2.5,
                            setupNote = "Thigh pad 3",
                            archived = false,
                        ),
                        callbacks = MachineEditorCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        compose.onNodeWithText("Delete").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-editor-delete.png")
    }
}

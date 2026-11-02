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

/** The machine editor for a new machine, an existing one and a blank name; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineEditorScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: MachineEditorUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineEditorContent(state = state, callbacks = MachineEditorCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `new machine, untouched`() = shoot(
        "machine-editor-new",
        MachineEditorUiState(loading = false),
    )

    @Test
    fun `new machine with a blank name shows the error`() = shoot(
        "machine-editor-name-error",
        MachineEditorUiState(loading = false, name = "", nameTouched = true),
    )

    @Test
    fun `existing machine`() = shoot(
        "machine-editor-existing",
        MachineEditorUiState(
            loading = false,
            machineId = 3L,
            name = "Lat pulldown",
            muscleGroup = MuscleGroup.BACK,
            kind = MachineKind.WEIGHT_REPS,
            stepKg = 2.5,
            setupNote = "Thigh pad 3",
            archived = false,
        ),
    )

    /** The rest chips, with 90 seconds chosen. */
    @Test
    fun `rest after each set, 90 seconds chosen`() {
        shoot(
            "machine-editor-rest",
            MachineEditorUiState(
                loading = false,
                machineId = 3L,
                name = "Lat pulldown",
                muscleGroup = MuscleGroup.BACK,
                kind = MachineKind.WEIGHT_REPS,
                stepKg = 2.5,
                setupNote = "Thigh pad 3",
                restSeconds = 90,
            ),
        )
        compose.onNodeWithText("90 s").assertExists()
    }
}

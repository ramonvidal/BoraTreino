package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val cardioMachineState = MachineEditorUiState(
    loading = false,
    machineId = 7L,
    name = "Treadmill",
    muscleGroup = MuscleGroup.CARDIO,
    kind = MachineKind.CARDIO,
    stepKg = 2.5,
    setupNote = "Incline 2",
)

private val weightMachineState = cardioMachineState.copy(
    name = "Lat pulldown",
    muscleGroup = MuscleGroup.BACK,
    kind = MachineKind.WEIGHT_REPS,
    setupNote = "Thigh pad 3",
)

private val routineWithCardio = RoutineEditorUiState(
    loading = false,
    routineId = 2L,
    name = "Routine B",
    shortName = "B",
    items = listOf(
        RoutineEditorItem(
            "Leg press",
            RoutineItem(routineId = 2L, machineId = 1L, position = 0, targetSets = 3, repsMin = 8, repsMax = 12),
            MuscleGroup.LEGS,
            MachineKind.WEIGHT_REPS,
        ),
        RoutineEditorItem(
            "Treadmill",
            RoutineItem(routineId = 2L, machineId = 7L, position = 1, targetSets = 1, repsMin = 1, repsMax = 1),
            MuscleGroup.CARDIO,
            MachineKind.CARDIO,
        ),
    ),
)

private val routineOnlyCardio = routineWithCardio.copy(items = routineWithCardio.items.drop(1))

/** The machine editor on the Cardio kind and the routine editor row of a cardio machine; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CardioEditorScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shootMachine(name: String, state: MachineEditorUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    MachineEditorContent(state = state, callbacks = MachineEditorCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun shootRoutine(name: String, state: RoutineEditorUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    RoutineEditorContent(state = state, callbacks = RoutineEditorCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `machine editor with the Cardio kind hides step and rest`() {
        shootMachine("machine-editor-cardio", cardioMachineState)
        compose.onNodeWithText("Weight step").assertDoesNotExist()
        compose.onNodeWithText("Rest after each set").assertDoesNotExist()
        compose.onNodeWithText("Muscle group").assertExists()
        compose.onNodeWithText("Kind").assertExists()
    }

    @Test
    fun `machine editor with the Cardio kind dark`() = shootMachine("machine-editor-cardio-dark", cardioMachineState, dark = true)

    @Test
    fun `new machine editor still shows step and rest for weights`() {
        shootMachine("machine-editor-cardio-contrast", weightMachineState)
        compose.onNodeWithText("Weight step").assertExists()
        compose.onNodeWithText("Rest after each set").assertExists()
    }

    @Test
    fun `routine editor row of a cardio machine has no sets x reps`() {
        shootRoutine("routine-editor-cardio", routineWithCardio)
        compose.onNodeWithText("Treadmill").assertExists()
        // The weight machine's row shows its target; the cardio row shows none (so only one target on screen).
        compose.onNodeWithText("3 \u00d7 8\u201312").assertExists()
        compose.onAllNodesWithText("\u00d7", substring = true).assertCountEquals(1)
    }

    @Test
    fun `routine editor with only a cardio machine shows no steppers`() {
        shootRoutine("routine-editor-cardio-only", routineOnlyCardio)
        compose.onNodeWithText("Treadmill").assertExists()
        compose.onNodeWithText("Sets").assertDoesNotExist()
        compose.onNodeWithText("Reps from").assertDoesNotExist()
        compose.onNodeWithText("Reps to").assertDoesNotExist()
        compose.onAllNodesWithText("\u00d7", substring = true).assertCountEquals(0)
    }

    @Test
    fun `routine editor row of a cardio machine dark`() = shootRoutine("routine-editor-cardio-dark", routineWithCardio, dark = true)
}

/** The same editors in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class CardioEditorPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `machine editor with the Cardio kind pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineEditorContent(state = cardioMachineState, callbacks = MachineEditorCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-editor-cardio-pt.png")
    }

    @Test
    fun `routine editor row of a cardio machine pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutineEditorContent(state = routineWithCardio, callbacks = RoutineEditorCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routine-editor-cardio-pt.png")
    }
}

package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The routine's target sheet for a timed item: only the number of sets shows, and the rep range is hidden
 * (kept as stored). A reps item keeps its rep range. Checked through the labels on screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutineTargetTimeTest {
    @get:Rule
    val compose = createComposeRule()

    private val plank = RoutineEditorItem(
        machineName = "Plank",
        item = RoutineItem(routineId = 7L, machineId = 23L, position = 0, targetSets = 3, repsMin = 8, repsMax = 12),
        muscleGroup = MuscleGroup.CORE,
        kind = MachineKind.TIME,
    )

    private val legPress = RoutineEditorItem(
        machineName = "Leg press",
        item = RoutineItem(routineId = 7L, machineId = 1L, position = 1, targetSets = 3, repsMin = 8, repsMax = 12),
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
    )

    private fun setContent(entry: RoutineEditorItem) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SetsRepsSheetContent(entry = entry, index = 0, callbacks = RoutineEditorCallbacks())
                }
            }
        }
    }

    @Test
    fun `a time item says 3 sets and shows only the sets stepper`() {
        setContent(plank)

        compose.onNodeWithText("3 sets").assertExists()
        compose.onNodeWithText("Sets").assertExists()
        compose.onNodeWithText("Reps from").assertDoesNotExist()
        compose.onNodeWithText("Reps to").assertDoesNotExist()
        compose.onNodeWithText("3 × 8–12", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a reps item keeps its rep range`() {
        setContent(legPress)

        compose.onNodeWithText("3 × 8–12").assertExists()
        compose.onNodeWithText("Reps from").assertExists()
        compose.onNodeWithText("Reps to").assertExists()
    }
}

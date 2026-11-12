package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/** The routine editor for an existing routine, in rotation and on specific days; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutineEditorScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: RoutineEditorUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutineEditorContent(state = state, callbacks = RoutineEditorCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val legPress = editorMachine(1L, "Leg press", MuscleGroup.LEGS, 5.0)
    private val legExtension = editorMachine(2L, "Leg extension", MuscleGroup.LEGS, 2.5)
    private val latPulldown = editorMachine(3L, "Lat pulldown", MuscleGroup.BACK, 2.5)
    private val seatedRow = editorMachine(4L, "Seated row", MuscleGroup.BACK, 2.5)
    private val cableCurl = editorMachine(8L, "Cable biceps curl", MuscleGroup.ARMS, 2.5)

    /** Routine B: three machines in order, each with its target. */
    private val items = listOf(
        RoutineEditorItem("Leg press", RoutineItem(routineId = 2L, machineId = 1L, position = 0, targetSets = 3, repsMin = 8, repsMax = 12)),
        RoutineEditorItem("Leg extension", RoutineItem(routineId = 2L, machineId = 2L, position = 1, targetSets = 3, repsMin = 10, repsMax = 15)),
        RoutineEditorItem("Lat pulldown", RoutineItem(routineId = 2L, machineId = 3L, position = 2, targetSets = 4, repsMin = 6, repsMax = 10)),
    )

    /** Machines not in the routine yet, by muscle group. */
    private val available = listOf(
        MachineGroup(MuscleGroup.BACK, listOf(seatedRow)),
        MachineGroup(MuscleGroup.ARMS, listOf(cableCurl)),
    )

    private fun existingRoutine(weekdays: Set<DayOfWeek> = emptySet()) = RoutineEditorUiState(
        loading = false,
        routineId = 2L,
        name = "Routine B",
        shortName = "B",
        weekdays = weekdays,
        items = items,
        available = available,
    )

    @Test
    fun `existing routine in rotation with three machines and their targets`() = shoot(
        "routine-editor",
        existingRoutine(),
    )

    @Test
    fun `existing routine on specific days shows the day chips`() = shoot(
        "routine-editor-weekdays",
        existingRoutine(weekdays = setOf(DayOfWeek.WEDNESDAY)),
    )
}

private fun editorMachine(id: Long, name: String, group: MuscleGroup, stepKg: Double): Machine = Machine(
    id = id,
    name = name,
    muscleGroup = group,
    kind = MachineKind.WEIGHT_REPS,
    stepKg = stepKg,
    setupNote = null,
    restSeconds = null,
    archived = false,
)

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
import com.boratreino.core.model.Routine
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Routines tab with three routines in turn, in Baianês, to check the translated text fits; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class RoutinesPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val legPress = ptMachine(1, "Leg press", MuscleGroup.LEGS, 5.0, "Seat 4")
    private val legExtension = ptMachine(2, "Leg extension", MuscleGroup.LEGS, 2.5)
    private val latPulldown = ptMachine(3, "Lat pulldown", MuscleGroup.BACK, 2.5, "Thigh pad 3")
    private val seatedRow = ptMachine(4, "Seated row", MuscleGroup.BACK, 2.5)
    private val pullUps = ptMachine(5, "Pull-ups", MuscleGroup.BACK, 2.5, bodyweight = true)
    private val chestPress = ptMachine(6, "Chest press", MuscleGroup.CHEST, 2.5)
    private val shoulderPress = ptMachine(7, "Shoulder press", MuscleGroup.SHOULDERS, 2.5)
    private val cableCurl = ptMachine(8, "Cable biceps curl", MuscleGroup.ARMS, 2.5)

    private val added = listOf(legPress, legExtension, latPulldown, seatedRow, pullUps, chestPress, shoulderPress, cableCurl)
        .map { it.name }
        .toSet()

    private val starters = MuscleGroup.entries.mapNotNull { group ->
        StarterMachines.all
            .filter { it.muscleGroup == group && it.name !in added }
            .takeIf { it.isNotEmpty() }
            ?.let { pickerGroupOf(group, it) }
    }

    @Test
    fun `routines list pt`() {
        val state = RoutinesUiState(
            routines = listOf(
                RoutineRow(ptRoutine(1L, "Routine A", "A", 0), machineCount = 4),
                RoutineRow(ptRoutine(2L, "Routine B", "B", 1), machineCount = 4),
                RoutineRow(ptRoutine(3L, "Routine C", "C", 2), machineCount = 3),
            ),
            scheduleMode = ScheduleMode.IN_TURN,
            machineGroups = listOf(
                MachineGroup(MuscleGroup.LEGS, listOf(legPress, legExtension)),
                MachineGroup(MuscleGroup.BACK, listOf(latPulldown, seatedRow, pullUps)),
                MachineGroup(MuscleGroup.CHEST, listOf(chestPress)),
                MachineGroup(MuscleGroup.SHOULDERS, listOf(shoulderPress)),
                MachineGroup(MuscleGroup.ARMS, listOf(cableCurl)),
            ),
            pickerGroups = starters,
            gymCount = 8,
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = state,
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = RoutinesPage.ROUTINES,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-list-pt.png")
    }
}

private fun ptMachine(
    id: Long,
    name: String,
    group: MuscleGroup,
    stepKg: Double,
    setupNote: String? = null,
    bodyweight: Boolean = false,
): Machine = Machine(
    id = id,
    name = name,
    muscleGroup = group,
    kind = if (bodyweight) MachineKind.BODYWEIGHT_REPS else MachineKind.WEIGHT_REPS,
    stepKg = stepKg,
    setupNote = setupNote,
    restSeconds = null,
    archived = false,
)

private fun ptRoutine(id: Long, name: String, shortName: String, position: Int): Routine = Routine(
    id = id,
    planId = 1L,
    name = name,
    shortName = shortName,
    position = position,
    weekdays = emptySet(),
)

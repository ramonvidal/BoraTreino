package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The session editor's edit sheet for the measured kinds: the steppers each kind shows, and the Save
 * button that waits for an amount. Checked through the labels and values on screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorMeasuredKindsTest {
    @get:Rule
    val compose = createComposeRule()

    private val farmersWalk = Machine(
        id = 21L,
        name = "Farmer's walk",
        muscleGroup = MuscleGroup.ARMS,
        kind = MachineKind.WEIGHT_TIME,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val sledPush = Machine(
        id = 22L,
        name = "Sled push",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_DISTANCE,
        stepKg = 5.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val plank = Machine(
        id = 23L,
        name = "Plank",
        muscleGroup = MuscleGroup.CORE,
        kind = MachineKind.TIME,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    /** The editor with the edit sheet open on one set of [machine]: [weightKg], [seconds] and [distanceM]. */
    private fun editing(
        machine: Machine,
        weightKg: Double,
        seconds: Int = 0,
        distanceM: Int = 0,
    ): SessionEditorUiState {
        val set = WorkoutSet(
            id = 1L,
            sessionId = 20L,
            machineId = machine.id,
            position = 0,
            weightKg = weightKg,
            reps = null,
            seconds = seconds,
            calories = null,
            distanceM = distanceM,
            loggedAt = Instant.parse("2026-11-02T18:20:00Z"),
        )
        val edit = SetEdit(
            set = set,
            machine = machine,
            number = 1,
            weightKg = weightKg,
            reps = 0,
            seconds = seconds,
            distanceM = distanceM,
        )
        return CalendarSamples.editorState.copy(editor = edit)
    }

    private fun setContent(state: SessionEditorUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `weight and time sheet has a weight stepper and a seconds stepper`() {
        setContent(editing(farmersWalk, weightKg = 47.5, seconds = 30))

        compose.onNodeWithText("Weight (kg)").assertExists()
        compose.onNodeWithText("Time (s)").assertExists()
        compose.onNodeWithText("47.5", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("30", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Distance (m)").assertDoesNotExist()
    }

    @Test
    fun `weight and distance sheet has a weight stepper and a metres stepper, and no seconds`() {
        setContent(editing(sledPush, weightKg = 40.0, distanceM = 20))

        compose.onNodeWithText("Weight (kg)").assertExists()
        compose.onNodeWithText("Distance (m)").assertExists()
        compose.onNodeWithText("20", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Time (s)").assertDoesNotExist()
    }

    @Test
    fun `time sheet has no weight stepper`() {
        setContent(editing(plank, weightKg = 0.0, seconds = 45))

        compose.onNodeWithText("Time (s)").assertExists()
        compose.onNodeWithText("45", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Weight (kg)").assertDoesNotExist()
        compose.onNodeWithText("Added weight (kg)").assertDoesNotExist()
        compose.onNodeWithText("Distance (m)").assertDoesNotExist()
    }

    @Test
    fun `Save waits for a time above zero`() {
        setContent(editing(plank, weightKg = 0.0, seconds = 0))

        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `Save waits for a distance above zero`() {
        setContent(editing(sledPush, weightKg = 40.0, distanceM = 0))

        compose.onNodeWithText("Save").assertIsNotEnabled()
    }
}

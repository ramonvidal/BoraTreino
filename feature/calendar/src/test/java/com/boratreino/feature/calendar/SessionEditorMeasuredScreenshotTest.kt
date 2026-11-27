package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The session editor's edit sheet for a weight and distance set; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorMeasuredScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

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

    @Test
    fun `edit sheet for a weight and distance set`() {
        val set = WorkoutSet(
            id = 1L,
            sessionId = 20L,
            machineId = sledPush.id,
            position = 0,
            weightKg = 40.0,
            reps = null,
            seconds = null,
            calories = null,
            distanceM = 25,
            loggedAt = Instant.parse("2026-11-02T18:20:00Z"),
        )
        val state = CalendarSamples.editorState.copy(
            editor = SetEdit(set = set, machine = sledPush, number = 1, weightKg = 40.0, reps = 0, distanceM = 25),
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-weight-distance-sheet.png")
    }
}

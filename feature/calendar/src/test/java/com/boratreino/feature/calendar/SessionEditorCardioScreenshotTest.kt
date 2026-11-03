package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

private val treadmill = Machine(31L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

private fun cardioSet(id: Long, position: Int, draft: CardioDraft, at: String) =
    CardioRules.toSet(id, CalendarSamples.mondaySession.session.id, treadmill.id, position, draft, Instant.parse(at))

private val firstSet = cardioSet(9001, 0, CardioDraft(30, 250, 3200), "2026-11-02T21:20:00Z")
private val secondSet = cardioSet(9002, 1, CardioDraft(10, 90, 0), "2026-11-02T21:55:00Z")

/** Monday's session with the weight machines and a treadmill with two cardio sets. */
private val withCardio = CalendarSamples.editorState.copy(
    detail = CalendarSamples.mondaySession.let {
        SessionDetail(it.session, it.routine, it.machines + SessionMachine(treadmill, listOf(firstSet, secondSet)))
    },
    machines = CalendarSamples.editorState.machines + treadmill,
)

/** The session editor with cardio sets and the cardio edit sheet; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorCardioScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SessionEditorUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `session editor with cardio sets`() {
        setContent(withCardio)
        compose.onNodeWithText("Treadmill").assertExists()
        compose.onNodeWithText("30 min · 250 kcal · 3.2 km").assertExists()
        compose.onNodeWithText("10 min · 90 kcal").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-cardio.png")
    }

    @Test
    fun `session editor with cardio sets dark`() {
        setContent(withCardio, dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-cardio-dark.png")
    }

    @Test
    fun `cardio edit sheet shows the three steppers and no weight or reps`() {
        val editor = SetEdit(firstSet, treadmill, 1, 0.0, 10, minutes = 30, calories = 250, distanceM = 3200)
        setContent(withCardio.copy(editor = editor))
        compose.onNodeWithText("Minutes").assertExists()
        compose.onNodeWithText("Calories").assertExists()
        compose.onNodeWithText("Distance (km)").assertExists()
        compose.onNodeWithText("Reps").assertDoesNotExist()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-cardio-sheet.png")
    }

    @Test
    fun `cardio edit sheet with nothing logged`() {
        val editor = SetEdit(secondSet, treadmill, 2, 0.0, 10, minutes = 0, calories = 0, distanceM = 0)
        setContent(withCardio.copy(editor = editor))
        compose.onNodeWithText("Minutes").assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-cardio-sheet-empty.png")
    }
}

/** The same screens in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class SessionEditorCardioPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `cardio edit sheet pt`() {
        val editor = SetEdit(firstSet, treadmill, 1, 0.0, 10, minutes = 30, calories = 250, distanceM = 3200)
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = withCardio.copy(editor = editor), actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-cardio-sheet-pt.png")
    }
}

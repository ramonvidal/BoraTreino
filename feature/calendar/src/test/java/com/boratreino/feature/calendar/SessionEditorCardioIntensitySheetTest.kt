package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

private val bike = Machine(32L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

private val bikeSet = CardioRules.toSet(
    id = 9010L,
    sessionId = CalendarSamples.mondaySession.session.id,
    machineId = bike.id,
    position = 0,
    draft = CardioDraft(30, 250, 3200, 12),
    loggedAt = Instant.parse("2026-11-02T21:20:00Z"),
)

/** Monday's session with a bike that has one set with an intensity of 12. */
private val withBike = CalendarSamples.editorState.copy(
    detail = CalendarSamples.mondaySession.let {
        SessionDetail(it.session, it.routine, it.machines + SessionMachine(bike, listOf(bikeSet)))
    },
    machines = CalendarSamples.editorState.machines + bike,
)

/** The session editor's cardio sheet: the Intensity stepper, checked through what is shown and what a tap reports. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorCardioIntensitySheetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SessionEditorUiState, actions: SessionEditorActions = SessionEditorActions(), dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun editing(intensity: Int?) = withBike.copy(
        editor = SetEdit(
            set = bikeSet,
            machine = bike,
            number = 1,
            weightKg = 0.0,
            reps = 10,
            minutes = 30,
            calories = 250,
            distanceM = 3200,
            intensity = intensity,
        ),
    )

    @Test
    fun `the cardio sheet has an Intensity stepper beside Calories`() {
        setContent(editing(intensity = 12))

        compose.onNodeWithText("Intensity").assertExists()
        compose.onNodeWithContentDescription("Increase Intensity").assertExists()
        compose.onNodeWithContentDescription("Decrease Intensity").assertExists()
        compose.onNodeWithText("Calories").assertExists()
    }

    @Test
    fun `the Intensity stepper reports each step`() {
        val steps = mutableListOf<Boolean>()
        setContent(editing(intensity = null), SessionEditorActions(onStepEditorIntensity = { steps += it }))

        compose.onNodeWithContentDescription("Increase Intensity").performClick()
        compose.onNodeWithContentDescription("Decrease Intensity").performClick()

        assertEquals(listOf(true, false), steps)
    }

    @Test
    fun `the cardio sheet with an intensity captures a picture`() {
        setContent(editing(intensity = 12))
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-cardio-intensity-sheet.png")
    }

    @Test
    fun `the cardio sheet with no intensity captures a picture`() {
        setContent(editing(intensity = null), dark = true)
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-cardio-intensity-sheet-unset-dark.png")
    }
}

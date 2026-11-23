package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

private val padBike = Machine(34L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

/** A cardio set with every value: 30 min, 250 kcal, 3.2 km and intensity 0.79. */
private val padSet = CardioRules.toSet(
    id = 9300L,
    sessionId = CalendarSamples.mondaySession.session.id,
    machineId = padBike.id,
    position = 0,
    draft = CardioDraft(minutes = 30, calories = 250, distanceM = 3200, intensity = 0.79),
    loggedAt = Instant.parse("2026-11-02T21:20:00Z"),
)

/** Monday's editor with the cardio set open in the editor; [pad] is the number pad to show over it. */
private fun padEditing(pad: NumberPad?): SessionEditorUiState = CalendarSamples.editorState.copy(
    detail = CalendarSamples.mondaySession.let {
        SessionDetail(it.session, it.routine, it.machines + SessionMachine(padBike, listOf(padSet)))
    },
    machines = CalendarSamples.editorState.machines + padBike,
    editor = SetEdit(
        set = padSet,
        machine = padBike,
        number = 1,
        weightKg = 0.0,
        reps = 10,
        minutes = 30,
        calories = 250,
        distanceM = 3_200,
        intensity = 0.79,
    ),
    pad = pad,
)

/** The session editor's cardio values opened on the number pad: which field each value asks for, and the pad itself. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorCardioPadTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SessionEditorUiState, actions: SessionEditorActions = SessionEditorActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** The stepper value showing [text]: the clickable one. */
    private fun stepperValue(text: String) = compose.onNode(hasText(text) and hasClickAction())

    /** The pad dialog is the last window on screen; the editor behind it is still in the tree. */
    private fun padDialogHas(matcher: SemanticsMatcher): Boolean =
        matcher.matches(compose.onAllNodes(isRoot()).onLast().fetchSemanticsNode())

    private fun pressKey(label: String) {
        compose.onAllNodesWithText(label).onLast().performClick()
    }

    private fun assertPadShows(title: String, decimalKey: Boolean) {
        // OK is only on the pad: without it the "last window" would be the editor, and its stepper labels would match.
        assertTrue("pad is open", padDialogHas(hasAnyDescendant(hasText("OK"))))
        assertTrue("pad titled '$title'", padDialogHas(hasAnyDescendant(hasText(title))))
        assertEquals("decimal key on the pad for '$title'", decimalKey, padDialogHas(hasAnyDescendant(hasText("."))))
    }

    // Each value asks for the pad on its field

    @Test
    fun `tapping each cardio value asks for the pad on that field`() {
        val asked = mutableListOf<PadTarget>()
        setContent(padEditing(pad = null), SessionEditorActions(onOpenPad = { asked += it }))

        val values = listOf(
            "30" to PadTarget.EDIT_MINUTES,
            "3.2" to PadTarget.EDIT_DISTANCE_KM,
            "0.79" to PadTarget.EDIT_INTENSITY,
            "250" to PadTarget.EDIT_CALORIES,
        )
        values.forEach { (value, _) -> stepperValue(value).performClick() }

        assertEquals(values.map { it.second }, asked)
    }

    // The pad: title, decimal key and the text it starts with

    @Test
    fun `the minutes pad is titled Minutes and has no decimal key`() {
        setContent(padEditing(NumberPad(PadTarget.EDIT_MINUTES, "30")))

        assertPadShows("Minutes", decimalKey = false)
    }

    @Test
    fun `the distance pad is titled Distance (km), starts at 3 2 and has a decimal key`() {
        setContent(padEditing(NumberPad(PadTarget.EDIT_DISTANCE_KM, "3.2")))

        assertPadShows("Distance (km)", decimalKey = true)
        assertTrue(padDialogHas(hasAnyDescendant(hasText("3.2"))))
    }

    @Test
    fun `the calories pad is titled Calories and has no decimal key`() {
        setContent(padEditing(NumberPad(PadTarget.EDIT_CALORIES, "250")))

        assertPadShows("Calories", decimalKey = false)
    }

    @Test
    fun `the intensity pad is titled Intensity, starts at 0 79 and has a decimal key`() {
        setContent(padEditing(NumberPad(PadTarget.EDIT_INTENSITY, "0.79")))

        assertPadShows("Intensity", decimalKey = true)
        assertTrue(padDialogHas(hasAnyDescendant(hasText("0.79"))))
    }

    // OK and Cancel

    @Test
    fun `OK sends the typed intensity to the view model`() {
        val confirmed = mutableListOf<String>()
        setContent(padEditing(NumberPad(PadTarget.EDIT_INTENSITY, "0.79")), SessionEditorActions(onConfirmPad = { confirmed += it }))

        pressKey("1")
        pressKey(".")
        pressKey("0")
        pressKey("5")
        pressKey("OK")

        assertEquals(listOf("1.05"), confirmed)
    }

    @Test
    fun `OK sends the typed distance to the view model`() {
        val confirmed = mutableListOf<String>()
        setContent(padEditing(NumberPad(PadTarget.EDIT_DISTANCE_KM, "3.2")), SessionEditorActions(onConfirmPad = { confirmed += it }))

        pressKey("1")
        pressKey("2")
        pressKey(".")
        pressKey("5")
        pressKey("OK")

        assertEquals(listOf("12.5"), confirmed)
    }

    @Test
    fun `Cancel dismisses the pad without confirming`() {
        var dismissed = 0
        val confirmed = mutableListOf<String>()
        setContent(
            padEditing(NumberPad(PadTarget.EDIT_MINUTES, "30")),
            SessionEditorActions(onDismissPad = { dismissed++ }, onConfirmPad = { confirmed += it }),
        )

        compose.onAllNodesWithText("Cancel").onLast().performClick()

        assertEquals(1, dismissed)
        assertTrue(confirmed.isEmpty())
    }
}

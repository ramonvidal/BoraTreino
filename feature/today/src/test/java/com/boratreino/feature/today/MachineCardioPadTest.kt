package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioField
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SetSummary
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Tapping a cardio stepper value opens the number pad: its title is the stepper's label, it has a decimal key only
 * for the decimal fields (Distance and Intensity), and OK puts the typed value on the stepper.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineCardioPadTest {
    @get:Rule
    val compose = createComposeRule()

    private val bike = Machine(
        id = 21L,
        name = "Bike",
        muscleGroup = MuscleGroup.CARDIO,
        kind = MachineKind.CARDIO,
        stepKg = 1.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val lastVisit = Instant.parse("2026-10-24T18:00:00Z")
    private val lastTime = SetSummary.Cardio(count = 1, minutes = 30, calories = 250, distanceM = 0)

    /** Every stepper holds a value, so each one shows its number. */
    private val allValues = CardioDraft(
        minutes = 20,
        calories = 250,
        distanceM = 17_300,
        intensity = 0.79,
    )

    private fun state(draft: CardioDraft = allValues, editor: CardioEditor? = null) = MachineUiState(
        loaded = true,
        machine = bike,
        lastTime = lastTime,
        lastTimeAt = lastVisit,
        sets = emptyList(),
        cardio = draft,
        cardioSameAgain = null,
        cardioDistanceShown = draft.distanceM > 0,
        cardioEditor = editor,
    )

    /**
     * The screen with its pad wired as the view model wires it: a value tap opens the pad for that field, OK types
     * the pad's text into the draft (through the same rules), Cancel closes it.
     */
    private fun showWithPad(initial: MachineUiState = state()) {
        compose.setContent {
            BoraTreinoTheme {
                var current by remember { mutableStateOf(initial) }
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(
                        state = current,
                        actions = MachineActions(
                            onOpenCardioPad = { field ->
                                current = current.copy(
                                    pad = NumberPad(PadTarget.CARDIO, CardioRules.fieldText(current.cardio, field), field),
                                )
                            },
                            onConfirmPad = { text ->
                                val typed = current.pad?.field?.let { CardioRules.withField(current.cardio, it, text) }
                                current = current.copy(
                                    pad = null,
                                    cardio = typed ?: current.cardio,
                                    cardioDistanceShown = current.cardioDistanceShown || (typed?.distanceM ?: 0) > 0,
                                )
                            },
                            onDismissPad = { current = current.copy(pad = null) },
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    /** The stepper value showing [text]: the clickable one, so the same text elsewhere on the screen is not picked. */
    private fun stepperValue(text: String) = compose.onNode(hasText(text) and hasClickAction())

    /** The pad dialog is the last window on screen (the screen behind it is still in the tree). */
    private fun padDialogHas(matcher: SemanticsMatcher): Boolean =
        matcher.matches(compose.onAllNodes(isRoot()).onLast().fetchSemanticsNode())

    private fun pressKey(label: String) {
        compose.onAllNodesWithText(label).onLast().performClick()
    }

    private fun assertPadOpenedFor(title: String, decimalKey: Boolean) {
        // OK is only on the pad: without it the "last window" would be the screen, and its stepper labels would match.
        assertTrue("pad is open", padDialogHas(hasAnyDescendant(hasText("OK"))))
        assertTrue("pad titled '$title'", padDialogHas(hasAnyDescendant(hasText(title))))
        assertEquals("decimal key on the pad for '$title'", decimalKey, padDialogHas(hasAnyDescendant(hasText("."))))
    }

    // Each value opens the pad titled with its label

    @Test
    fun `tapping Minutes opens the pad titled Minutes, without a decimal key`() {
        showWithPad()

        stepperValue("20").performClick()

        assertPadOpenedFor("Minutes", decimalKey = false)
    }

    @Test
    fun `tapping Distance opens the pad titled Distance (km) with the distance in kilometres, with a decimal key`() {
        showWithPad()

        stepperValue("17.3").performClick()

        assertPadOpenedFor("Distance (km)", decimalKey = true)
        assertTrue(padDialogHas(hasAnyDescendant(hasText("17.3"))))
    }

    @Test
    fun `tapping Calories opens the pad titled Calories, without a decimal key`() {
        showWithPad()

        stepperValue("250").performClick()

        assertPadOpenedFor("Calories", decimalKey = false)
    }

    @Test
    fun `tapping Intensity opens the pad titled Intensity, with a decimal key`() {
        showWithPad()

        stepperValue("0.79").performClick()

        assertPadOpenedFor("Intensity", decimalKey = true)
    }

    // OK and Cancel

    @Test
    fun `OK puts the typed distance on the stepper`() {
        showWithPad()
        stepperValue("17.3").performClick()

        pressKey("5")
        pressKey(".")
        pressKey("2")
        pressKey("OK")

        stepperValue("5.2").assertExists()
        compose.onAllNodesWithText("OK").assertCountEquals(0)
    }

    @Test
    fun `OK puts the typed minutes on the stepper and keeps the other values`() {
        showWithPad()
        stepperValue("20").performClick()

        pressKey("4")
        pressKey("5")
        pressKey("OK")

        stepperValue("45").assertExists()
        stepperValue("250").assertExists()
    }

    @Test
    fun `OK puts the typed intensity on the stepper, rounded to 2 decimals`() {
        showWithPad()
        stepperValue("0.79").performClick()

        pressKey("0")
        pressKey(".")
        pressKey("7")
        pressKey("8")
        pressKey("5")
        pressKey("OK")

        stepperValue("0.79").assertExists()
        compose.onAllNodesWithText("OK").assertCountEquals(0)
    }

    @Test
    fun `Cancel closes the pad and leaves the value as it was`() {
        showWithPad()
        stepperValue("20").performClick()
        pressKey("9")

        compose.onAllNodesWithText("Cancel").onLast().performClick()

        compose.onAllNodesWithText("OK").assertCountEquals(0)
        stepperValue("20").assertExists()
    }

    // The logged set's sheet asks for the pad on the edit fields

    @Test
    fun `tapping a value in a logged set's sheet asks for the pad on that field`() {
        val opened = mutableListOf<CardioField>()
        val logged = CardioRules.toSet(
            id = 2L, sessionId = 1L, machineId = bike.id, position = 0,
            draft = CardioDraft(35, 300, 0), loggedAt = lastVisit,
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(
                        state = state(editor = CardioEditor(logged, 1, CardioDraft(35, 300, 0))),
                        actions = MachineActions(onOpenEditorCardioPad = { opened += it }),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        stepperValue("35").performClick()

        assertEquals(listOf(CardioField.MINUTES), opened)
    }
}

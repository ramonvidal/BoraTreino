package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The routine switcher's "Extra workout" switch: shown in the sheet only with an active routine, the pill says
 * "extra" while it is on, and each tap reports the opposite state. Checked through what is shown and what each tap reports.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayExtraTest {
    @get:Rule
    val compose = createComposeRule()

    private val extraTitle = "Extra workout"
    private val extraHint = "Does not change the rotation: the next routine stays the same."

    private fun show(state: TodayUiState, actions: TodayActions = TodayActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** Taps the switcher pill with the given label, which opens the routine sheet. */
    private fun openSwitcher(label: String) {
        compose.onNode(hasClickAction() and hasText(label)).performClick()
    }

    /** Routine B, nothing done yet, with the switch in [extra]; [suggested] marks B as the rotation's next. */
    private fun routineB(extra: Boolean, suggested: Boolean = false): TodayUiState {
        val state = TodaySamples.routineBNotStarted
        val routines = requireNotNull(state.routines)
        return state.copy(
            routines = routines.copy(
                extra = extra,
                chips = routines.chips.map { chip ->
                    if (chip.id == TodaySamples.routineB.id) chip.copy(suggested = suggested) else chip
                },
            ),
        )
    }

    /** No routine chosen: the pill says "No routine" and the sheet has no switch. */
    private val noRoutine: TodayUiState = TodaySamples.routineBNotStarted.copy(
        routines = requireNotNull(TodaySamples.routineBNotStarted.routines).copy(
            chips = TodaySamples.chips(null),
            freeSelected = true,
            activeName = null,
            activeId = null,
            machines = emptyList(),
        ),
    )

    @Test
    fun `the routine sheet has the Extra workout switch and its hint while a routine is active`() {
        show(routineB(extra = false))

        openSwitcher("Routine B")
        compose.onNodeWithText("Choose a routine").assertIsDisplayed()
        compose.onNodeWithText(extraTitle).assertIsDisplayed()
        compose.onNodeWithText(extraHint).assertIsDisplayed()
    }

    @Test
    fun `the routine sheet has no Extra workout switch without a routine`() {
        show(noRoutine)

        // The pill and the sheet's chip both say "No routine", so the sheet is checked by its title.
        openSwitcher("No routine")
        compose.onNodeWithText("Choose a routine").assertIsDisplayed()
        compose.onNodeWithText(extraTitle).assertDoesNotExist()
        compose.onNodeWithText(extraHint).assertDoesNotExist()
    }

    @Test
    fun `tapping the switch of a routine that is not extra reports true`() {
        var reported: Boolean? = null
        show(routineB(extra = false), TodayActions(onExtra = { reported = it }))

        openSwitcher("Routine B")
        compose.onNodeWithText(extraTitle).performClick()

        assertEquals(true, reported)
    }

    @Test
    fun `tapping the switch of an extra routine reports false`() {
        var reported: Boolean? = null
        show(routineB(extra = true), TodayActions(onExtra = { reported = it }))

        openSwitcher("Routine B · extra")
        compose.onNodeWithText(extraTitle).performClick()

        assertEquals(false, reported)
    }

    @Test
    fun `the pill says extra while the switch is on`() {
        show(routineB(extra = true))

        compose.onNodeWithText("Routine B · extra").assertIsDisplayed()
    }

    @Test
    fun `the pill of an extra routine drops the next in rotation suffix`() {
        show(routineB(extra = true, suggested = true))

        compose.onNodeWithText("Routine B · extra").assertIsDisplayed()
        compose.onNodeWithText("next in rotation", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the pill of the rotation's next routine says next in rotation when the switch is off`() {
        show(routineB(extra = false, suggested = true))

        compose.onNodeWithText("Routine B · next in rotation").assertIsDisplayed()
        compose.onNodeWithText("Routine B · extra").assertDoesNotExist()
    }

    @Test
    fun `picking another routine in the sheet chooses it without the extra flag`() {
        val chosen = mutableListOf<RoutineChoice>()
        show(routineB(extra = true), TodayActions(onChooseRoutine = { chosen += it }))

        openSwitcher("Routine B · extra")
        compose.onNodeWithText("A").performClick()

        assertEquals(listOf<RoutineChoice>(RoutineChoice.Chosen(TodaySamples.routineA.id)), chosen)
    }
}

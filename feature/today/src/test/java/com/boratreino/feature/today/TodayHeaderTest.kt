package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Today hero card (date line, routine name, "N of M exercises", the progress ring and the routine
 * switcher pill) and the routine sheet with its "Next" label; saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 11, 9)

    private fun setContent(state: TodayUiState, actions: TodayActions = TodayActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** Routine B with the chips given: the suggested routine and the selected one are chosen by the test. */
    private fun withChips(base: TodayUiState, vararg chips: RoutineChipUi): TodayUiState =
        base.copy(routines = requireNotNull(base.routines).copy(chips = chips.toList()))

    /** Opens the routine sheet by tapping the switcher pill (it reads "Routine B" or "Routine B · next in rotation"). */
    private fun openSwitcher() {
        compose.onNode(hasClickAction() and hasText("Routine B", substring = true)).performClick()
    }

    @Test
    fun `the header shows the date line when today is set`() {
        setContent(TodaySamples.routineBNotStarted.copy(today = monday))

        compose.onNodeWithText("Monday", substring = true).assertExists()
        compose.onNodeWithText("November", substring = true).assertExists()
    }

    @Test
    fun `the header shows no date line when today is null`() {
        setContent(TodaySamples.routineBNotStarted)

        compose.onAllNodesWithText("Monday", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("November", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the hero shows the routine name and N of M exercises under the date`() {
        setContent(TodaySamples.routineBNotStarted.copy(today = monday))

        // The hero title and the switcher pill both read "Routine B" while no routine is suggested.
        compose.onAllNodesWithText("Routine B").assertCountEquals(2)
        compose.onNodeWithText("0 of 4 exercises").assertExists()
        compose.onNodeWithText("0%").assertExists()
    }

    @Test
    fun `the progress ring shows the percentage of exercises done`() {
        setContent(TodaySamples.routineBInProgress.copy(today = monday))

        compose.onNodeWithText("1 of 4 exercises").assertExists()
        compose.onNodeWithText("25%").assertExists()
    }

    @Test
    fun `the switcher pill says next in rotation for the suggested routine that is selected`() {
        setContent(
            withChips(
                TodaySamples.routineBNotStarted,
                RoutineChipUi(1L, "A", selected = false),
                RoutineChipUi(2L, "B", selected = true, suggested = true),
                RoutineChipUi(3L, "C", selected = false),
            ),
        )

        compose.onNodeWithText("Routine B · next in rotation").assertExists()
    }

    @Test
    fun `the suggested routine in the sheet carries a Next label`() {
        setContent(
            withChips(
                TodaySamples.routineBNotStarted,
                RoutineChipUi(1L, "A", selected = false, suggested = true),
                RoutineChipUi(2L, "B", selected = true),
                RoutineChipUi(3L, "C", selected = false),
            ),
        )
        openSwitcher()

        compose.onAllNodesWithText("Next").assertCountEquals(1)
    }

    @Test
    fun `no Next label on the sheet chips without a suggestion`() {
        setContent(TodaySamples.routineBNotStarted)
        openSwitcher()

        compose.onAllNodesWithText("Next").assertCountEquals(0)
    }

    @Test
    fun `the sheet chips show their short names and No routine`() {
        setContent(TodaySamples.routineBNotStarted)
        openSwitcher()

        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("B").assertExists()
        compose.onNodeWithText("C").assertExists()
        compose.onNodeWithText("No routine").assertExists()
    }

    @Test
    fun `Finish training is enabled while exercises are left and reports the finish`() {
        var done = 0
        setContent(TodaySamples.routineBInProgress, TodayActions(onDone = { done++ }))

        compose.onNodeWithText("Finish training").assertIsEnabled()
        compose.onNodeWithText("Finish training").performClick()

        assertEquals(1, done)
    }

    @Test
    fun `once every exercise is done the hero button is Finish training and reports the finish`() {
        var done = 0
        val allDone = TodaySamples.routineBInProgress.copy(
            heroPrimary = HeroAction.FINISH,
            routines = requireNotNull(TodaySamples.routineBInProgress.routines).copy(
                machines = requireNotNull(TodaySamples.routineBInProgress.routines).machines.map {
                    it.copy(done = true, highlighted = false, setCount = 3)
                },
            ),
        )
        setContent(allDone.copy(today = monday), TodayActions(onDone = { done++ }))

        compose.onNodeWithText("4 of 4 exercises").assertExists()
        compose.onNodeWithText("100%").assertExists()
        compose.onAllNodesWithText("Finish training").assertCountEquals(1)
        compose.onNodeWithText("Finish training").assertIsEnabled()
        compose.onNodeWithText("Finish training").performClick()

        assertEquals(1, done)
    }

    @Test
    fun `the header and routine shot`() {
        setContent(TodaySamples.routineBNotStarted.copy(today = monday))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-header-routine.png")
    }

    @Test
    fun `the training card shot with progress`() {
        setContent(TodaySamples.routineBInProgress.copy(today = monday))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-training-card-progress.png")
    }

    @Test
    fun `the training card shot when every exercise is done`() {
        val base = TodaySamples.routineBInProgress
        val allDone = base.copy(
            today = monday,
            heroPrimary = HeroAction.FINISH,
            routines = requireNotNull(base.routines).copy(
                machines = requireNotNull(base.routines).machines.map { it.copy(done = true, highlighted = false) },
            ),
        )
        setContent(allDone)

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-training-card-done.png")
    }
}

package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
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
 * The Today header (date line and routine summary), the routine chips with their "Next" label and
 * the training card (progress line and Done); saved to build/outputs/roborazzi.
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

    @Test
    fun `the header shows the date line when today is set`() {
        setContent(TodaySamples.routineBNotStarted.copy(today = monday))

        compose.onNodeWithText("Bora pro Treino!").assertExists()
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
    fun `the routine summary is one line under the date`() {
        setContent(TodaySamples.routineBNotStarted.copy(today = monday))

        compose.onNodeWithText("Routine B · 4 machines · 12 sets").assertExists()
    }

    @Test
    fun `the suggested routine that is not selected carries a Next label`() {
        setContent(
            withChips(
                TodaySamples.routineBNotStarted,
                RoutineChipUi(1L, "A", selected = false, suggested = true),
                RoutineChipUi(2L, "B", selected = true),
                RoutineChipUi(3L, "C", selected = false),
            ),
        )

        compose.onAllNodesWithText("Next").assertCountEquals(1)
    }

    @Test
    fun `no Next label when the suggested routine is the selected one`() {
        setContent(
            withChips(
                TodaySamples.routineBNotStarted,
                RoutineChipUi(1L, "A", selected = false),
                RoutineChipUi(2L, "B", selected = true, suggested = true),
                RoutineChipUi(3L, "C", selected = false),
            ),
        )

        compose.onAllNodesWithText("Next").assertCountEquals(0)
    }

    @Test
    fun `no Next label on the chips without a suggestion`() {
        setContent(TodaySamples.routineBNotStarted)

        compose.onAllNodesWithText("Next").assertCountEquals(0)
    }

    @Test
    fun `the chips show their short names`() {
        setContent(TodaySamples.routineBNotStarted)

        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("B").assertExists()
        compose.onNodeWithText("C").assertExists()
        compose.onNodeWithText("Free").assertExists()
    }

    @Test
    fun `the training card shows the routine name and N of M machines`() {
        setContent(TodaySamples.routineBInProgress.copy(today = monday))

        compose.onNodeWithText("Training now").assertExists()
        compose.onNodeWithText("Routine B").assertExists()
        compose.onNodeWithText("1 of 4 machines").assertExists()
    }

    @Test
    fun `Done is enabled while machines are left and reports the finish`() {
        var done = 0
        setContent(TodaySamples.routineBInProgress, TodayActions(onDone = { done++ }))

        compose.onNodeWithText("Done").assertIsEnabled()
        compose.onNodeWithText("Done").performClick()

        assertEquals(1, done)
    }

    @Test
    fun `Done is enabled once every machine is done and still reports the finish`() {
        var done = 0
        val allDone = TodaySamples.routineBInProgress.copy(
            routines = requireNotNull(TodaySamples.routineBInProgress.routines).copy(
                machines = requireNotNull(TodaySamples.routineBInProgress.routines).machines.map {
                    it.copy(done = true, highlighted = false)
                },
            ),
        )
        setContent(allDone.copy(today = monday), TodayActions(onDone = { done++ }))

        compose.onNodeWithText("4 of 4 machines").assertExists()
        compose.onNodeWithText("Done").assertIsEnabled()
        compose.onNodeWithText("Done").performClick()

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
    fun `the training card shot when every machine is done`() {
        val base = TodaySamples.routineBInProgress
        val allDone = base.copy(
            today = monday,
            routines = requireNotNull(base.routines).copy(
                machines = requireNotNull(base.routines).machines.map { it.copy(done = true, highlighted = false) },
            ),
        )
        setContent(allDone)

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-training-card-done.png")
    }
}

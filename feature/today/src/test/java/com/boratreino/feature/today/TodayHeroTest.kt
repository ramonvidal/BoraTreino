package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Today hero: its one main button (Start, Continue, Finish training, Add exercise), the routine
 * switcher sheet, the rest day and the empty state. Checked through what is shown and which action each tap
 * reports. Saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayHeroTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 9)

    private fun setToday(state: TodayUiState, actions: TodayActions = TodayActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** Opens the routine sheet by tapping the switcher pill. */
    private fun openSwitcher() {
        compose.onNode(hasClickAction() and hasText("Routine B", substring = true)).performClick()
    }

    /** The rest day of the samples with the next training set to Wednesday, Routine B. */
    private val restDayWithNext: TodayUiState = TodaySamples.restDay.let { state ->
        val routines = requireNotNull(state.routines)
        state.copy(
            routines = routines.copy(
                upcoming = UpcomingUi(LocalDate.of(2026, 11, 11), TodaySamples.routineB.name, tomorrow = false),
            ),
        )
    }

    // The hero's main button

    @Test
    fun `the hero starts with the first exercise when nothing is logged yet`() {
        setToday(TodaySamples.routineBNotStarted.copy(today = today))

        compose.onNodeWithText("Start: Leg press").assertExists()
        compose.onNodeWithText("Finish training").assertDoesNotExist()
    }

    @Test
    fun `Start opens the first exercise by its id`() {
        val opened = mutableListOf<Long>()
        setToday(TodaySamples.routineBNotStarted, TodayActions(onOpenMachine = { opened += it }))

        compose.onNodeWithText("Start: Leg press").performClick()

        assertEquals(listOf(TodaySamples.legPress.id), opened)
    }

    @Test
    fun `the hero continues with the next exercise once one is in progress`() {
        setToday(TodaySamples.routineBInProgress.copy(today = today))

        compose.onNodeWithText("Continue: Leg extension").assertExists()
        compose.onNodeWithText("Start: Leg press").assertDoesNotExist()
        compose.onNodeWithText("Finish training").assertExists()
    }

    @Test
    fun `Continue opens the next exercise by its id`() {
        val opened = mutableListOf<Long>()
        setToday(TodaySamples.routineBInProgress, TodayActions(onOpenMachine = { opened += it }))

        compose.onNodeWithText("Continue: Leg extension").performClick()

        assertEquals(listOf(TodaySamples.legExtension.id), opened)
    }

    @Test
    fun `Finish training in a session reports the finish and does not open an exercise`() {
        var done = 0
        val opened = mutableListOf<Long>()
        setToday(TodaySamples.routineBInProgress, TodayActions(onDone = { done++ }, onOpenMachine = { opened += it }))

        compose.onNodeWithText("Finish training").performClick()

        assertEquals(1, done)
        assertEquals(emptyList<Long>(), opened)
    }

    // The routine switcher

    @Test
    fun `the switcher sheet offers No routine and picking it chooses no routine`() {
        val chosen = mutableListOf<RoutineChoice>()
        setToday(TodaySamples.routineBNotStarted, TodayActions(onChooseRoutine = { chosen += it }))

        openSwitcher()
        compose.onNodeWithText("No routine").assertExists()
        compose.onNodeWithText("No routine").performClick()

        assertEquals(listOf<RoutineChoice>(RoutineChoice.Free), chosen)
    }

    @Test
    fun `picking a routine in the switcher chooses that routine`() {
        val chosen = mutableListOf<RoutineChoice>()
        setToday(TodaySamples.routineBNotStarted, TodayActions(onChooseRoutine = { chosen += it }))

        openSwitcher()
        compose.onNodeWithText("A").performClick()

        assertEquals(listOf<RoutineChoice>(RoutineChoice.Chosen(TodaySamples.routineA.id)), chosen)
    }

    // The rest day

    @Test
    fun `a rest day shows Rest day, the next training and Train anyway`() {
        setToday(restDayWithNext)

        compose.onNodeWithText("Rest day").assertExists()
        compose.onNodeWithText("Next training: Wednesday, Routine B").assertExists()
        compose.onNodeWithText("Train anyway").assertExists()
    }

    @Test
    fun `a rest day has no exercise rows and no hero button`() {
        setToday(restDayWithNext)

        compose.onNodeWithText("Leg press").assertDoesNotExist()
        compose.onNodeWithText("Lat pulldown").assertDoesNotExist()
        compose.onNodeWithText("Exercises").assertDoesNotExist()
        compose.onNodeWithText("Repeat last time").assertDoesNotExist()
        compose.onNodeWithText("Add exercise").assertDoesNotExist()
    }

    @Test
    fun `Train anyway chooses the suggested routine`() {
        val chosen = mutableListOf<RoutineChoice>()
        val routines = requireNotNull(restDayWithNext.routines)
        val withSuggestion = restDayWithNext.copy(
            routines = routines.copy(
                chips = routines.chips.map { if (it.id == TodaySamples.routineB.id) it.copy(suggested = true) else it },
            ),
        )
        setToday(withSuggestion, TodayActions(onChooseRoutine = { chosen += it }))

        compose.onNodeWithText("Train anyway").performClick()

        assertEquals(listOf<RoutineChoice>(RoutineChoice.Chosen(TodaySamples.routineB.id)), chosen)
    }

    // The empty state

    @Test
    fun `with no routines the empty state shows the recent exercises and All exercises`() {
        setToday(TodaySamples.noSession)

        compose.onNodeWithText("Pick an exercise to start").assertExists()
        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("All exercises").assertExists()
    }

    @Test
    fun `All exercises opens the picker`() {
        var all = 0
        setToday(TodaySamples.noSession, TodayActions(onAllMachines = { all++ }))

        compose.onNodeWithText("All exercises").performClick()

        assertEquals(1, all)
    }

    @Test
    fun `the empty state offers Set up in 3 steps`() {
        setToday(TodaySamples.noSession)

        compose.onNodeWithText("Set up in 3 steps").assertExists()
    }

    @Test
    fun `tapping Set up in 3 steps calls the setup action`() {
        var setup = 0
        setToday(TodaySamples.noSession, TodayActions(onStartSetup = { setup++ }))

        compose.onNodeWithText("Set up in 3 steps").performClick()

        assertEquals(1, setup)
    }

    // Shots

    @Test
    fun `the hero shot before the session`() {
        setToday(TodaySamples.routineBNotStarted.copy(today = today))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-hero.png")
    }

    @Test
    fun `the hero shot during the session`() {
        setToday(TodaySamples.routineBInProgress.copy(today = today))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-hero-session.png")
    }

    @Test
    fun `the rest day shot`() {
        setToday(restDayWithNext)

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-rest-day-sunny.png")
    }
}

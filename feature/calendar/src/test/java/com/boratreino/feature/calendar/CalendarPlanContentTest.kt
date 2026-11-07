package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "Plan a workout" part of the Calendar day detail, drawn from fixed states. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class CalendarPlanContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 5)
    private val yesterday = LocalDate.of(2026, 11, 4)
    private val nextWeek = LocalDate.of(2026, 11, 10)

    private val routineChoices = listOf(RoutineChoiceUi(1L, "Treino A"), RoutineChoiceUi(2L, "Treino B"))
    private val planB = PlannedUi(2L, "Treino B", "B")
    private val planA = PlannedUi(1L, "Treino A", "A")

    private fun state(
        selected: LocalDate,
        planned: Map<LocalDate, PlannedUi> = emptyMap(),
        dayLabels: Map<LocalDate, String> = emptyMap(),
        sessions: List<SessionDetail> = emptyList(),
        chooser: Boolean = false,
    ) = CalendarUiState(
        loaded = true,
        today = today,
        zone = ZoneId.of("UTC"),
        month = YearMonth.of(2026, 11),
        selected = selected,
        dayLabels = dayLabels,
        sessions = sessions,
        planned = planned,
        routines = routineChoices,
        planChooser = chooser,
    )

    private fun show(state: CalendarUiState, actions: CalendarActions = CalendarActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    // Future days and today

    @Test
    fun `a future day with nothing planned offers Plan a workout, enabled, and no Nothing logged card`() {
        show(state(nextWeek))

        compose.onNodeWithText("Plan a workout").assertExists()
        compose.onNodeWithText("Plan a workout").assertIsEnabled()
        compose.onNodeWithText("Nothing logged").assertDoesNotExist()
        compose.onNodeWithText("Add a training").assertDoesNotExist()
    }

    @Test
    fun `tapping Plan a workout asks to open the chooser`() {
        var opened = false
        show(state(nextWeek), CalendarActions(onOpenPlanChooser = { opened = true }))

        compose.onNodeWithText("Plan a workout").performClick()

        assertTrue(opened)
    }

    @Test
    fun `today offers Plan a workout and also the Nothing logged card`() {
        show(state(today))

        compose.onNodeWithText("Plan a workout").assertExists()
        compose.onNodeWithText("Nothing logged").assertExists()
        compose.onNodeWithText("Add a training").assertExists()
    }

    @Test
    fun `a future day with a plan shows the planned card with Change and Remove and no Plan a workout button`() {
        show(state(nextWeek, planned = mapOf(nextWeek to planB)))

        compose.onNodeWithText("Planned: Treino B").assertExists()
        compose.onNodeWithText("Change").assertExists()
        compose.onNodeWithText("Remove").assertExists()
        compose.onNodeWithText("Plan a workout").assertDoesNotExist()
    }

    @Test
    fun `Change asks to open the chooser and Remove asks to unplan the day`() {
        var changed = false
        var removed = false
        show(
            state(nextWeek, planned = mapOf(nextWeek to planB)),
            CalendarActions(
                onOpenPlanChooser = { changed = true },
                onUnplan = { removed = true },
            ),
        )

        compose.onNodeWithText("Change").performClick()
        compose.onNodeWithText("Remove").performClick()

        assertTrue(changed)
        assertTrue(removed)
    }

    @Test
    fun `the chooser lists the routines and picking one plans it by id`() {
        var picked: Long? = null
        show(state(nextWeek, chooser = true), CalendarActions(onPlan = { picked = it }))

        compose.onNodeWithText("Treino B").performClick()

        assertEquals(2L, picked)
    }

    @Test
    fun `Cancel in the chooser closes it and plans nothing`() {
        var dismissed = false
        var picked: Long? = null
        show(
            state(nextWeek, chooser = true),
            CalendarActions(onDismissPlanChooser = { dismissed = true }, onPlan = { picked = it }),
        )

        compose.onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
        assertNull(picked)
    }

    @Test
    fun `a planned day with no session shows its short name on the grid`() {
        show(state(nextWeek, planned = mapOf(nextWeek to planB)))

        compose.onNodeWithText("B").assertExists()
    }

    @Test
    fun `a day with a session shows its trained badge, not the plan marker`() {
        show(state(nextWeek, planned = mapOf(nextWeek to planB), dayLabels = mapOf(nextWeek to "A")))

        compose.onNodeWithText("B").assertDoesNotExist()
        compose.onNodeWithText("A").assertExists()
    }

    @Test
    fun `a planned day draws the planned card on a tall screen`() {
        show(state(nextWeek, planned = mapOf(nextWeek to planB)))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/calendar-planned-future-day.png")
    }

    // Past days

    @Test
    fun `a past day shows no Plan a workout button and no planned card`() {
        show(state(yesterday))

        compose.onNodeWithText("Plan a workout").assertDoesNotExist()
        compose.onNodeWithText("Change").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
        compose.onNodeWithText("Nothing logged").assertExists()
    }

    @Test
    fun `a past day with a plan in the state still shows no planned card`() {
        show(state(yesterday, planned = mapOf(yesterday to planA)))

        compose.onNodeWithText("Planned: Treino A").assertDoesNotExist()
        compose.onNodeWithText("Plan a workout").assertDoesNotExist()
    }
}

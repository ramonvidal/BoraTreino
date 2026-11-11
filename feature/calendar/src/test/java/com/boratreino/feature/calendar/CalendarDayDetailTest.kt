package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.ui.theme.BoraTreinoTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The selected day of the Agenda: the session card (Edit, Delete) on a past day with sessions, the "Nothing logged"
 * card on a past day without, and the actions of today and later days. Today is 4 November 2026, as in [CalendarSamples].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
class CalendarDayDetailTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 4)
    private val yesterday = LocalDate.of(2026, 11, 3)
    private val nextWeek = LocalDate.of(2026, 11, 10)
    private val sessionId = CalendarSamples.mondaySession.session.id

    private val routineChoices = listOf(RoutineChoiceUi(1L, "Treino A", "A"), RoutineChoiceUi(2L, "Treino B", "B"))
    private val planA = PlannedUi(1L, "Treino A", "A")
    private val planB = PlannedUi(2L, "Treino B", "B")

    private fun show(state: CalendarUiState, actions: CalendarActions = CalendarActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun dayState(
        selected: LocalDate,
        planned: Map<LocalDate, List<PlannedUi>> = emptyMap(),
        sessions: List<SessionDetail> = emptyList(),
    ) = CalendarUiState(
        loaded = true,
        today = today,
        zone = ZoneId.of("UTC"),
        month = YearMonth.of(2026, 11),
        selected = selected,
        planned = planned,
        routines = routineChoices,
        sessions = sessions,
    )

    // A past day with a session

    @Test
    fun `a past day with a session shows its routine, its counts and no add or nothing-logged card`() {
        show(CalendarSamples.monthWithTraining)

        compose.onNodeWithText("Routine B").assertExists()
        compose.onNodeWithText("3 exercises · 8 sets").assertExists()
        compose.onNodeWithText("Nothing logged").assertDoesNotExist()
        compose.onNodeWithText("Add a workout").assertDoesNotExist()
        compose.onNodeWithText("Plan a workout").assertDoesNotExist()
    }

    @Test
    fun `the session card names each exercise of the session`() {
        show(CalendarSamples.monthWithTraining)

        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("Seated row").assertExists()
        compose.onNodeWithText("Chest press").assertExists()
    }

    @Test
    fun `Edit on the session card asks to open that session`() {
        var edited: Long? = null
        show(CalendarSamples.monthWithTraining, CalendarActions(onEditSession = { edited = it }))

        compose.onNodeWithText("Edit").performClick()

        assertEquals(sessionId, edited)
    }

    @Test
    fun `Delete on the session card asks to delete that session`() {
        var deleted: Long? = null
        show(CalendarSamples.monthWithTraining, CalendarActions(onDeleteSession = { deleted = it }))

        compose.onNodeWithText("Delete").performClick()

        assertEquals(sessionId, deleted)
    }

    // A past day without a session

    @Test
    fun `a past day without a session shows Nothing logged and a tonal Add a workout`() {
        show(dayState(yesterday))

        compose.onNodeWithText("Nothing logged").assertExists()
        compose.onNodeWithText("Add a workout").assertExists()
        compose.onNodeWithText("Plan a workout").assertDoesNotExist()
    }

    @Test
    fun `Add a workout on a past day asks to add a session`() {
        var added = false
        show(dayState(yesterday), CalendarActions(onAddTraining = { added = true }))

        compose.onNodeWithText("Add a workout").performClick()

        assertTrue(added)
    }

    // Today and later

    @Test
    fun `today with nothing logged offers an enabled Plan a workout and Add a workout`() {
        show(dayState(today))

        compose.onNodeWithText("Plan a workout").assertIsEnabled()
        compose.onNodeWithText("Add a workout").assertExists()
        compose.onNodeWithText("Nothing logged").assertDoesNotExist()
    }

    @Test
    fun `Add a workout today asks to add a session`() {
        var added = false
        show(dayState(today), CalendarActions(onAddTraining = { added = true }))

        compose.onNodeWithText("Add a workout").performClick()

        assertTrue(added)
    }

    @Test
    fun `today with a session offers Plan a workout but no Add a workout`() {
        show(dayState(today, sessions = listOf(CalendarSamples.mondaySession)))

        compose.onNodeWithText("Plan a workout").assertExists()
        compose.onNodeWithText("Add a workout").assertDoesNotExist()
    }

    @Test
    fun `a future day with a planned routine lists it as a row and still offers Plan a workout`() {
        show(dayState(nextWeek, planned = mapOf(nextWeek to listOf(planA))))

        compose.onNodeWithText("Treino A").assertExists()
        compose.onAllNodesWithContentDescription("Remove").assertCountEquals(1)
        compose.onNodeWithText("Plan a workout").assertExists()
        compose.onNodeWithText("Add a workout").assertDoesNotExist()
    }

    @Test
    fun `Remove on a planned row asks to unplan that routine`() {
        var removed: Long? = null
        show(
            dayState(nextWeek, planned = mapOf(nextWeek to listOf(planA, planB))),
            CalendarActions(onUnplan = { removed = it }),
        )

        compose.onAllNodesWithContentDescription("Remove")[1].performClick()

        assertEquals(2L, removed)
    }
}

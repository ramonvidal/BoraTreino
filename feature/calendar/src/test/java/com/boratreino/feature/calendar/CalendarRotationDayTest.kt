package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The rotation on the Agenda: a projected day's row in the selected-day card, the legend's third entry, the week
 * strip and the month grid marks, and the Extra pill of an extra workout. Today is 4 November 2026, as in [CalendarSamples].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
class CalendarRotationDayTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 4)
    private val tomorrow = LocalDate.of(2026, 11, 5)
    private val yesterday = LocalDate.of(2026, 11, 3)

    private val routineChoices = listOf(RoutineChoiceUi(1L, "Treino A", "A"), RoutineChoiceUi(2L, "Treino B", "B"))
    private val projectedB = ProjectedUi(2L, "B", "Treino B")
    private val projectedC = ProjectedUi(3L, "C", "Treino C")
    private val projectedFixedF = ProjectedUi(4L, "F", "Treino F")
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
        projected: Map<LocalDate, ProjectedUi> = emptyMap(),
        projectedMore: Map<LocalDate, List<ProjectedUi>> = emptyMap(),
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
        projected = projected,
        projectedMore = projectedMore,
        sessions = sessions,
    )

    /** The week strip cell of a day: its weekday letter and its day number are in the same merged node. */
    private fun weekCell(weekday: String, day: Int) = compose.onNode(hasText(weekday) and hasText(day.toString()))

    // The selected day with a rotation routine

    @Test
    fun `a projected day shows its routine, From your rotation and an enabled Plan a workout`() {
        show(dayState(tomorrow, projected = mapOf(tomorrow to projectedB)))

        compose.onNodeWithText("Treino B").assertIsDisplayed()
        compose.onNodeWithText("From your rotation").assertIsDisplayed()
        compose.onNodeWithText("Plan a workout").assertIsEnabled()
    }

    @Test
    fun `the rotation row of a projected day has no Remove control`() {
        show(dayState(tomorrow, projected = mapOf(tomorrow to projectedB)))

        compose.onAllNodesWithContentDescription("Remove").assertCountEquals(0)
    }

    @Test
    fun `Plan a workout on a projected day asks to open the chooser`() {
        var opened = false
        show(dayState(tomorrow, projected = mapOf(tomorrow to projectedB)), CalendarActions(onOpenPlanChooser = { opened = true }))

        compose.onNodeWithText("Plan a workout").performClick()

        assertTrue(opened)
    }

    @Test
    fun `a day with a manual plan shows the planned row and the rotation row next to it`() {
        show(
            dayState(
                tomorrow,
                projected = mapOf(tomorrow to projectedC),
                planned = mapOf(tomorrow to listOf(planB)),
            ),
        )

        compose.onNodeWithText("Treino B").assertIsDisplayed()
        compose.onNodeWithText("Treino C").assertIsDisplayed()
        compose.onNodeWithText("From your rotation").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Remove").assertCountEquals(1)
    }

    @Test
    fun `a routine already logged on a day with a session is not suggested again`() {
        show(dayState(today, projected = mapOf(today to projectedB), sessions = listOf(CalendarSamples.mondaySession)))

        compose.onNodeWithText("From your rotation").assertDoesNotExist()
    }

    @Test
    fun `a day with a session still shows the rotation row of a routine not logged yet`() {
        show(dayState(today, projected = mapOf(today to projectedC), sessions = listOf(CalendarSamples.mondaySession)))

        compose.onNodeWithText("Treino C").assertIsDisplayed()
        compose.onNodeWithText("From your rotation").assertIsDisplayed()
    }

    @Test
    fun `a mixed day shows the rotation routine and the fixed-day one as rows`() {
        show(
            dayState(
                tomorrow,
                projected = mapOf(tomorrow to projectedC),
                projectedMore = mapOf(tomorrow to listOf(projectedFixedF)),
            ),
        )

        compose.onNodeWithText("Treino C").assertIsDisplayed()
        compose.onNodeWithText("Treino F").assertIsDisplayed()
        compose.onNodeWithText("Plan a workout").assertIsEnabled()
    }

    @Test
    fun `a past day never shows the rotation suggestion`() {
        show(dayState(yesterday, projected = mapOf(yesterday to projectedB)))

        compose.onNodeWithText("From your rotation").assertDoesNotExist()
        compose.onNodeWithText("Treino B").assertDoesNotExist()
        compose.onNodeWithText("Plan a workout").assertDoesNotExist()
        compose.onNodeWithText("Nothing logged").assertIsDisplayed()
    }

    // The legend

    @Test
    fun `the legend has a third entry for the rotation`() {
        show(CalendarSamples.monthWithTraining)

        compose.onNodeWithText("Rotation").assertIsDisplayed()
        compose.onNodeWithText("Trained").assertIsDisplayed()
        compose.onNodeWithText("Planned").assertIsDisplayed()
    }

    // The week strip and the month grid

    @Test
    fun `a projected day shows its routine letter in the week strip`() {
        show(CalendarSamples.monthWithTraining.copy(projected = mapOf(tomorrow to projectedB)))

        weekCell("T", 5).assertTextContains("B")
    }

    @Test
    fun `a trained day wins over a projected one in the week strip`() {
        // Monday 2 November has a session of routine B; the rotation's C does not show.
        show(CalendarSamples.monthWithTraining.copy(projected = mapOf(LocalDate.of(2026, 11, 2) to ProjectedUi(3L, "C", "Treino C"))))

        weekCell("M", 2).assertTextContains("B")
        compose.onNode(hasText("M") and hasText("2") and hasText("C")).assertDoesNotExist()
    }

    @Test
    fun `a projected day shows its routine letter in the month grid as well as in the week strip`() {
        show(CalendarSamples.monthWithTraining.copy(projected = mapOf(tomorrow to projectedB)))

        // The week strip cell and the grid cell of 5 November each merge the day number and the letter.
        compose.onAllNodes(hasText("5") and hasText("B")).assertCountEquals(2)
    }

    @Test
    fun `a planned day wins over a projected one in the month grid and the week strip`() {
        show(
            CalendarSamples.monthWithTraining.copy(
                planned = mapOf(tomorrow to listOf(planA)),
                projected = mapOf(tomorrow to projectedB),
            ),
        )

        compose.onAllNodes(hasText("5") and hasText("A")).assertCountEquals(2)
        compose.onAllNodes(hasText("5") and hasText("B")).assertCountEquals(0)
    }

    // Extra workouts

    @Test
    fun `a session of an extra workout shows the Extra pill`() {
        val extra = CalendarSamples.mondaySession.let { it.copy(session = it.session.copy(isExtra = true)) }
        show(CalendarSamples.monthWithTraining.copy(sessions = listOf(extra)))

        compose.onNodeWithText("Extra").assertIsDisplayed()
        compose.onNodeWithText("Routine B").assertIsDisplayed()
    }

    @Test
    fun `a normal session has no Extra pill`() {
        show(CalendarSamples.monthWithTraining)

        compose.onNodeWithText("Extra").assertDoesNotExist()
    }
}

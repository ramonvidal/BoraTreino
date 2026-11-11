package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Agenda's week strip (goal, streak, the seven day cells), and the legend under the month grid.
 * Monday is the first day, as in [CalendarSamples]: this week is 2 to 8 November 2026, today is 4 November.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
class CalendarWeekStripTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: CalendarUiState, actions: CalendarActions = CalendarActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /**
     * The strip's cell of one day. A cell merges its weekday letter, its routine letter (or check) and its day number
     * into one node, so the cell is the node that has both the weekday letter and the day number.
     */
    private fun weekCell(weekday: String, day: Int) = compose.onNode(hasText(weekday) and hasText(day.toString()))

    private val planned = PlannedUi(2L, "Treino B", "B")
    private val wednesday = LocalDate.of(2026, 11, 4)

    @Test
    fun `the week strip has a cell for each day of this week`() {
        show(CalendarSamples.monthWithTraining)

        weekCell("M", 2).assertExists()
        weekCell("S", 8).assertExists()
    }

    @Test
    fun `the week strip has no cell for a day of the next week`() {
        show(CalendarSamples.monthWithTraining)

        weekCell("M", 9).assertDoesNotExist()
    }

    @Test
    fun `today has its cell in the week strip`() {
        show(CalendarSamples.monthWithTraining)

        weekCell("W", 4).assertExists()
    }

    @Test
    fun `a trained day shows its routine letter in its week strip cell`() {
        // 2 November has a session of routine B.
        show(CalendarSamples.monthWithTraining)

        weekCell("M", 2).assertTextContains("B")
    }

    @Test
    fun `a planned day shows the planned routine letter in its week strip cell`() {
        show(CalendarSamples.monthWithTraining.copy(planned = mapOf(wednesday to listOf(planned))))

        weekCell("W", 4).assertTextContains("B")
    }

    @Test
    fun `tapping a day in the week strip selects that day`() {
        var selected: LocalDate? = null
        show(CalendarSamples.monthWithTraining, CalendarActions(onSelectDay = { selected = it }))

        weekCell("T", 3).performClick()

        assertEquals(LocalDate.of(2026, 11, 3), selected)
    }

    @Test
    fun `tapping the goal opens the goal dialog`() {
        var opened = false
        show(CalendarSamples.monthWithTraining, CalendarActions(onOpenGoal = { opened = true }))

        compose.onNodeWithText("2 of 4").performClick()

        assertTrue(opened)
    }

    @Test
    fun `the streak is shown under the goal`() {
        show(CalendarSamples.monthWithTraining)

        compose.onNodeWithText("Streak: 5 weeks").assertIsDisplayed()
    }

    @Test
    fun `the legend names the trained and the planned marks`() {
        show(CalendarSamples.monthWithTraining)

        compose.onNodeWithText("Trained").assertIsDisplayed()
        compose.onNodeWithText("Planned").assertIsDisplayed()
    }
}

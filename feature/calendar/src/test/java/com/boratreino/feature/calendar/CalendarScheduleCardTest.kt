package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.ui.theme.BoraTreinoTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "Which routine is next" card at the bottom of the Agenda: the mode chips and the training days. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CalendarScheduleCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 5)

    private val inTurnHint = "Rotation moves to the next routine on these days. If you skip a day the routine stays the next one."
    private val weekdaysPointer = "Choose each routine's days when you edit it."

    private fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun state(mode: ScheduleMode, days: Set<DayOfWeek> = emptySet()) = CalendarUiState(
        loaded = true,
        today = today,
        zone = ZoneId.of("UTC"),
        month = YearMonth.of(2026, 11),
        selected = today,
        scheduleMode = mode,
        trainingDays = days,
    )

    private fun show(
        state: CalendarUiState,
        onScheduleModeChange: (ScheduleMode) -> Unit = {},
        onTrainingDaysChange: (Set<DayOfWeek>) -> Unit = {},
    ) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(
                        state = state,
                        actions = CalendarActions(
                            onScheduleModeChange = onScheduleModeChange,
                            onTrainingDaysChange = onTrainingDaysChange,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun `in turn shows the title, both modes, the training days and the hint`() {
        show(state(ScheduleMode.IN_TURN))

        compose.onNodeWithText("Which routine is next").assertIsDisplayed()
        compose.onNodeWithText("In turn").assertIsDisplayed()
        compose.onNodeWithText("On weekdays").assertIsDisplayed()
        compose.onNodeWithText("Training days").assertIsDisplayed()
        compose.onNodeWithText(inTurnHint).assertIsDisplayed()
    }

    @Test
    fun `in turn shows a chip for every day of the week`() {
        show(state(ScheduleMode.IN_TURN))

        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertIsDisplayed()
        }
    }

    @Test
    fun `on weekdays hides the training day chips and their hint, and points to the routine editor`() {
        show(state(ScheduleMode.WEEKDAYS))

        compose.onNodeWithText("In turn").assertIsDisplayed()
        compose.onNodeWithText("On weekdays").assertIsDisplayed()
        compose.onNodeWithText(weekdaysPointer).assertIsDisplayed()
        compose.onNodeWithText("Training days").assertDoesNotExist()
        compose.onNodeWithText(inTurnHint).assertDoesNotExist()
        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertDoesNotExist()
        }
    }

    @Test
    fun `in turn does not show the weekdays pointer`() {
        show(state(ScheduleMode.IN_TURN))

        compose.onNodeWithText(weekdaysPointer).assertDoesNotExist()
    }

    @Test
    fun `tapping On weekdays asks for the weekdays mode`() {
        var mode: ScheduleMode? = null
        show(state(ScheduleMode.IN_TURN), onScheduleModeChange = { mode = it })

        compose.onNodeWithText("On weekdays").performClick()

        assertEquals(ScheduleMode.WEEKDAYS, mode)
    }

    @Test
    fun `tapping In turn asks for the in turn mode`() {
        var mode: ScheduleMode? = null
        show(state(ScheduleMode.WEEKDAYS), onScheduleModeChange = { mode = it })

        compose.onNodeWithText("In turn").performClick()

        assertEquals(ScheduleMode.IN_TURN, mode)
    }

    @Test
    fun `tapping a day that is not picked reports the set with that day added`() {
        var reported: Set<DayOfWeek>? = null
        show(
            state(ScheduleMode.IN_TURN, days = setOf(DayOfWeek.WEDNESDAY)),
            onTrainingDaysChange = { reported = it },
        )

        compose.onNodeWithText(dayLabel(DayOfWeek.FRIDAY)).performClick()

        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), reported)
    }

    @Test
    fun `tapping a picked day reports the set with that day removed`() {
        var reported: Set<DayOfWeek>? = null
        show(
            state(ScheduleMode.IN_TURN, days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            onTrainingDaysChange = { reported = it },
        )

        compose.onNodeWithText(dayLabel(DayOfWeek.MONDAY)).performClick()

        assertEquals(setOf(DayOfWeek.WEDNESDAY), reported)
    }

    @Test
    fun `the last picked day can be removed, leaving an empty set`() {
        var reported: Set<DayOfWeek>? = null
        show(
            state(ScheduleMode.IN_TURN, days = setOf(DayOfWeek.WEDNESDAY)),
            onTrainingDaysChange = { reported = it },
        )

        compose.onNodeWithText(dayLabel(DayOfWeek.WEDNESDAY)).performClick()

        assertEquals(emptySet<DayOfWeek>(), reported)
    }

    @Test
    fun `the preview line lists the rest of this week's suggested trainings`() {
        val preview = listOf(
            UpcomingUi(LocalDate.of(2026, 11, 6), "A"),
            UpcomingUi(LocalDate.of(2026, 11, 7), "B"),
        )
        show(state(ScheduleMode.IN_TURN).copy(weekPreview = preview))

        compose.onNodeWithText(
            "This week: ${dayLabel(DayOfWeek.FRIDAY)} A, ${dayLabel(DayOfWeek.SATURDAY)} B",
        ).assertIsDisplayed()
    }

    @Test
    fun `the preview line is hidden when no training is left this week`() {
        show(state(ScheduleMode.IN_TURN).copy(weekPreview = emptyList()))

        compose.onNodeWithText("This week:", substring = true).assertDoesNotExist()
    }
}

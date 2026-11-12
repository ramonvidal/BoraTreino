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

/** The "Rotation days" card at the bottom of the Agenda: the training day chips, each routine's schedule and the week preview. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CalendarScheduleCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 5)

    private val title = "Rotation days"
    private val hint = "Routines in rotation are trained on these days, one after the other. " +
        "If you skip a day the routine stays the next one. Routines set to specific days come up on their own days."
    private val pointer = "Change a routine's days in its editor (Routines tab)."

    private val inRotationRoutine = RoutineChoiceUi(1L, "Treino A", "A")
    private val fixedRoutine = RoutineChoiceUi(2L, "Treino B", "B", setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY))

    private fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun state(
        days: Set<DayOfWeek> = emptySet(),
        routines: List<RoutineChoiceUi> = emptyList(),
        weekPreview: List<UpcomingUi> = emptyList(),
        mode: ScheduleMode = ScheduleMode.IN_TURN,
    ) = CalendarUiState(
        loaded = true,
        today = today,
        zone = ZoneId.of("UTC"),
        month = YearMonth.of(2026, 11),
        selected = today,
        scheduleMode = mode,
        trainingDays = days,
        routines = routines,
        weekPreview = weekPreview,
    )

    private fun show(
        state: CalendarUiState,
        onTrainingDaysChange: (Set<DayOfWeek>) -> Unit = {},
    ) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(
                        state = state,
                        actions = CalendarActions(onTrainingDaysChange = onTrainingDaysChange),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun `the card shows the rotation title, its hint and the pointer to the routine editor`() {
        show(state())

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(hint).assertIsDisplayed()
        compose.onNodeWithText(pointer).assertIsDisplayed()
    }

    @Test
    fun `the card has no mode chips and none of the old texts`() {
        show(state())

        compose.onNodeWithText("Which routine is next").assertDoesNotExist()
        compose.onNodeWithText("In turn").assertDoesNotExist()
        compose.onNodeWithText("On weekdays").assertDoesNotExist()
        compose.onNodeWithText("Training days").assertDoesNotExist()
    }

    @Test
    fun `the global schedule mode does not change the card`() {
        show(state(mode = ScheduleMode.WEEKDAYS))

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("On weekdays").assertDoesNotExist()
        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertIsDisplayed()
        }
    }

    @Test
    fun `a chip for every day of the week is always shown`() {
        show(state(routines = listOf(inRotationRoutine, fixedRoutine)))

        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertIsDisplayed()
        }
    }

    @Test
    fun `each routine is listed with In rotation or with its days`() {
        show(state(routines = listOf(inRotationRoutine, fixedRoutine)))

        compose.onNodeWithText("Treino A · In rotation").assertIsDisplayed()
        compose.onNodeWithText("Treino B · ${dayLabel(DayOfWeek.MONDAY)}, ${dayLabel(DayOfWeek.WEDNESDAY)}")
            .assertIsDisplayed()
    }

    @Test
    fun `tapping a day that is not picked reports the set with that day added`() {
        var reported: Set<DayOfWeek>? = null
        show(
            state(days = setOf(DayOfWeek.WEDNESDAY)),
            onTrainingDaysChange = { reported = it },
        )

        compose.onNodeWithText(dayLabel(DayOfWeek.FRIDAY)).performClick()

        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), reported)
    }

    @Test
    fun `tapping a picked day reports the set with that day removed`() {
        var reported: Set<DayOfWeek>? = null
        show(
            state(days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            onTrainingDaysChange = { reported = it },
        )

        compose.onNodeWithText(dayLabel(DayOfWeek.MONDAY)).performClick()

        assertEquals(setOf(DayOfWeek.WEDNESDAY), reported)
    }

    @Test
    fun `the last picked day can be removed, leaving an empty set`() {
        var reported: Set<DayOfWeek>? = null
        show(
            state(days = setOf(DayOfWeek.WEDNESDAY)),
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
        show(state(weekPreview = preview))

        compose.onNodeWithText(
            "This week: ${dayLabel(DayOfWeek.FRIDAY)} A, ${dayLabel(DayOfWeek.SATURDAY)} B",
        ).assertIsDisplayed()
    }

    @Test
    fun `the preview line is hidden when no training is left this week`() {
        show(state(weekPreview = emptyList()))

        compose.onNodeWithText("This week:", substring = true).assertDoesNotExist()
    }
}

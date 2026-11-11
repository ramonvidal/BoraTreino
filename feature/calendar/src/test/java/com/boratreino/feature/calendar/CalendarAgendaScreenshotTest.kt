package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Routine
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Agenda in its new states, drawn on a tall screen so the week strip, the month, the selected day and the
 * schedule card all fit. Saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
class CalendarAgendaScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val nov9 = LocalDate.of(2026, 11, 9)
    private val nov11 = LocalDate.of(2026, 11, 11)
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val nov13 = LocalDate.of(2026, 11, 13)

    private fun planned(routine: Routine) = PlannedUi(routine.id, routine.name, routine.shortName)

    private fun choice(routine: Routine) = RoutineChoiceUi(routine.id, routine.name, routine.shortName)

    private fun shoot(name: String, state: CalendarUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(state = state, actions = CalendarActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `calendar month with trained and planned days`() = shoot(
        "calendar-agenda-month-marks",
        CalendarSamples.monthWithTraining.copy(
            planned = mapOf(
                nov9 to listOf(planned(CalendarSamples.routineA)),
                nov11 to listOf(planned(CalendarSamples.routineB)),
                nov13 to listOf(planned(CalendarSamples.routineC)),
            ),
        ),
    )

    @Test
    fun `calendar future day with planned workouts`() = shoot(
        "calendar-agenda-future-day",
        CalendarSamples.monthWithTraining.copy(
            selected = nov12,
            sessions = emptyList(),
            planned = mapOf(nov12 to listOf(planned(CalendarSamples.routineA), planned(CalendarSamples.routineB))),
            routines = listOf(
                choice(CalendarSamples.routineA),
                choice(CalendarSamples.routineB),
                choice(CalendarSamples.routineC),
            ),
        ),
    )

    @Test
    fun `calendar past day with a session`() = shoot("calendar-agenda-past-day", CalendarSamples.monthWithTraining)

    @Test
    fun `calendar schedule card with this week's preview`() = shoot(
        "calendar-agenda-schedule-card",
        CalendarSamples.monthWithTraining.copy(
            scheduleMode = ScheduleMode.IN_TURN,
            weekPreview = listOf(
                UpcomingUi(LocalDate.of(2026, 11, 5), "A"),
                UpcomingUi(LocalDate.of(2026, 11, 6), "B"),
                UpcomingUi(LocalDate.of(2026, 11, 7), "C"),
                UpcomingUi(LocalDate.of(2026, 11, 8), "A"),
            ),
        ),
    )
}

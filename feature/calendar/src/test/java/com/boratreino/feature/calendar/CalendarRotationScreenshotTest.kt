package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The rotation days on the Agenda, the Extra pill and the session editor's Extra switch, drawn from fixed states.
 * Saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
class CalendarRotationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val nov5 = LocalDate.of(2026, 11, 5)

    private fun projected(routine: com.boratreino.core.model.Routine) = ProjectedUi(routine.id, routine.shortName, routine.name)

    private fun choice(routine: com.boratreino.core.model.Routine) = RoutineChoiceUi(routine.id, routine.name, routine.shortName)

    /** The rotation from 5 November on: B, C, A, B, C, A, B, C, one routine a day. */
    private val rotation = mapOf(
        LocalDate.of(2026, 11, 5) to projected(CalendarSamples.routineB),
        LocalDate.of(2026, 11, 6) to projected(CalendarSamples.routineC),
        LocalDate.of(2026, 11, 7) to projected(CalendarSamples.routineA),
        LocalDate.of(2026, 11, 8) to projected(CalendarSamples.routineB),
        LocalDate.of(2026, 11, 9) to projected(CalendarSamples.routineC),
        LocalDate.of(2026, 11, 10) to projected(CalendarSamples.routineA),
        LocalDate.of(2026, 11, 11) to projected(CalendarSamples.routineB),
        LocalDate.of(2026, 11, 12) to projected(CalendarSamples.routineC),
    )

    private val routineChoices = listOf(
        choice(CalendarSamples.routineA),
        choice(CalendarSamples.routineB),
        choice(CalendarSamples.routineC),
    )

    private val extraMonday = CalendarSamples.mondaySession.let { it.copy(session = it.session.copy(isExtra = true)) }

    private fun shootCalendar(name: String, state: CalendarUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(state = state, actions = CalendarActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun shootEditor(name: String, state: SessionEditorUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `calendar month with the rotation days projected`() = shootCalendar(
        "calendar-rotation-month",
        CalendarSamples.monthWithTraining.copy(projected = rotation, routines = routineChoices),
    )

    @Test
    fun `calendar projected day with the rotation routine and Plan a workout`() = shootCalendar(
        "calendar-rotation-day",
        CalendarSamples.monthWithTraining.copy(
            selected = nov5,
            sessions = emptyList(),
            projected = rotation,
            routines = routineChoices,
        ),
    )

    @Test
    fun `calendar mixed day with the rotation routine and a fixed-day routine`() = shootCalendar(
        "calendar-mixed-day",
        CalendarSamples.monthWithTraining.copy(
            selected = nov5,
            sessions = emptyList(),
            routines = routineChoices,
            projected = mapOf(nov5 to projected(CalendarSamples.routineC)),
            projectedMore = mapOf(nov5 to listOf(ProjectedUi(4L, "F", "Treino F", inRotation = false))),
        ),
    )

    @Test
    fun `calendar session of an extra workout with the Extra pill`() = shootCalendar(
        "calendar-extra-session",
        CalendarSamples.monthWithTraining.copy(sessions = listOf(extraMonday), routines = routineChoices),
    )

    @Test
    fun `session editor with the Extra workout switch`() = shootEditor(
        "calendar-editor-extra-switch",
        CalendarSamples.editorState.copy(detail = extraMonday),
    )
}

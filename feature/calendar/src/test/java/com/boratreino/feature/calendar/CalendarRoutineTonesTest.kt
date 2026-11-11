package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The routine colours on the Calendar: four tones that repeat by routine, never red, and the month's badges,
 * planned outline and today's number as the grid draws them. Written from the tone function and the state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CalendarRoutineTonesTest {
    @get:Rule
    val compose = createComposeRule()

    /** Is [color] a red: clearly more red than green and blue. */
    private fun isRed(color: Color): Boolean = color.red > color.green + 0.2f && color.red > color.blue + 0.2f

    /** The tone of each of the ids 0..7, read while composing. */
    private fun tonesOf(): Map<Long, Color> {
        val tones = linkedMapOf<Long, Color>()
        compose.setContent {
            BoraTreinoTheme {
                (0L..7L).forEach { id -> tones[id] = routineTone(id) }
            }
        }
        compose.waitForIdle()
        return tones
    }

    // The tones

    @Test
    fun `four routines get four different tones`() {
        val tones = tonesOf()

        assertEquals(4, (0L..3L).map { tones.getValue(it) }.toSet().size)
    }

    @Test
    fun `the tone repeats every four routines`() {
        val tones = tonesOf()

        (0L..3L).forEach { id -> assertEquals(tones.getValue(id), tones.getValue(id + 4)) }
    }

    @Test
    fun `no routine tone is red`() {
        val tones = tonesOf()

        tones.values.forEach { assertFalse("A routine tone is red: $it", isRed(it)) }
    }

    @Test
    fun `adjacent routines never share a tone`() {
        val tones = tonesOf()

        (0L..6L).forEach { id -> assertNotEquals(tones.getValue(id), tones.getValue(id + 1)) }
    }

    // The month as drawn

    private val month = YearMonth.of(2026, 11)
    private val zone = ZoneId.of("UTC")

    private val coloured = CalendarUiState(
        loaded = true,
        today = LocalDate.of(2026, 11, 9),
        zone = zone,
        firstDayOfWeek = DayOfWeek.MONDAY,
        month = month,
        selected = LocalDate.of(2026, 11, 4),
        weeklyGoal = 3,
        daysTrained = 3,
        streak = 1,
        dayLabels = mapOf(
            LocalDate.of(2026, 11, 2) to "B",
            LocalDate.of(2026, 11, 4) to "A",
            LocalDate.of(2026, 11, 6) to "C",
        ),
        dayRoutineIds = mapOf(
            LocalDate.of(2026, 11, 2) to 2L,
            LocalDate.of(2026, 11, 4) to 5L,
            LocalDate.of(2026, 11, 6) to 4L,
        ),
        planned = mapOf(
            LocalDate.of(2026, 11, 12) to listOf(PlannedUi(3L, "Routine D", "D")),
        ),
    )

    private fun shoot(state: CalendarUiState, name: String) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(state = state, actions = CalendarActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `each trained day shows its routine's short name`() {
        shoot(coloured, "calendar-tones")

        compose.onNodeWithText("B").assertExists()
        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("C").assertExists()
    }

    @Test
    fun `a planned day shows the planned routine's short name in the week strip and the grid`() {
        // The planned day (12 November) is in the week of today (9 November): the strip and the grid both show it.
        shoot(coloured, "calendar-tones")

        compose.onAllNodesWithText("D").assertCountEquals(2)
    }

    @Test
    fun `today's number is drawn with its ring and is still shown in the strip and the grid`() {
        shoot(coloured, "calendar-tones")

        compose.onAllNodesWithText("9").assertCountEquals(2)
    }

    @Test
    fun `the month with coloured badges is captured`() {
        shoot(coloured, "calendar-tones")

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/calendar-tones.png")
    }
}

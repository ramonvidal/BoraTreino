package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "When do you train this?" section of the routine editor: in rotation or on specific days, and the day chips. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutineEditorScheduleTest {
    @get:Rule
    val compose = createComposeRule()

    private val whenTitle = "When do you train this?"
    private val rotationHint =
        "It comes up in turn with your other rotation routines, on your training days (set in the Agenda tab)."
    private val daysHint = "It is always the next routine on the days you pick. Pick at least one day."

    private fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun state(weekdays: Set<DayOfWeek> = emptySet()) = RoutineEditorUiState(
        loading = false,
        routineId = 2L,
        name = "Routine B",
        shortName = "B",
        weekdays = weekdays,
    )

    private fun show(state: RoutineEditorUiState, onToggle: (DayOfWeek) -> Unit = {}) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutineEditorContent(
                        state = state,
                        callbacks = RoutineEditorCallbacks(onToggleWeekday = onToggle),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun `the section is shown for a routine in rotation`() {
        show(state())

        compose.onNodeWithText(whenTitle).assertExists()
        compose.onNodeWithText("In rotation").assertExists()
        compose.onNodeWithText("On specific days").assertExists()
        compose.onNodeWithText(rotationHint).assertExists()
    }

    @Test
    fun `the section is shown for a routine on specific days too`() {
        show(state(setOf(DayOfWeek.WEDNESDAY)))

        compose.onNodeWithText(whenTitle).assertExists()
        compose.onNodeWithText("In rotation").assertExists()
        compose.onNodeWithText("On specific days").assertExists()
        compose.onNodeWithText(daysHint).assertExists()
    }

    @Test
    fun `a routine in rotation shows no day chips`() {
        show(state())

        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertDoesNotExist()
        }
    }

    @Test
    fun `tapping On specific days shows the day chips and the days hint`() {
        show(state())

        compose.onNodeWithText("On specific days").performClick()

        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertExists()
        }
        compose.onNodeWithText(daysHint).assertExists()
        compose.onNodeWithText(rotationHint).assertDoesNotExist()
    }

    @Test
    fun `tapping On specific days reports no change to the days`() {
        val reported = mutableListOf<DayOfWeek>()
        show(state(), onToggle = { reported += it })

        compose.onNodeWithText("On specific days").performClick()

        assertEquals(emptyList<DayOfWeek>(), reported)
    }

    @Test
    fun `a routine on specific days shows its day chips at once`() {
        show(state(setOf(DayOfWeek.WEDNESDAY)))

        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertExists()
        }
    }

    @Test
    fun `tapping a day that is not picked reports that day`() {
        val reported = mutableListOf<DayOfWeek>()
        show(state(setOf(DayOfWeek.WEDNESDAY)), onToggle = { reported += it })

        compose.onNodeWithText(dayLabel(DayOfWeek.FRIDAY)).performClick()

        assertEquals(listOf(DayOfWeek.FRIDAY), reported)
    }

    @Test
    fun `tapping a picked day reports that day, so it can be removed`() {
        val reported = mutableListOf<DayOfWeek>()
        show(state(setOf(DayOfWeek.WEDNESDAY)), onToggle = { reported += it })

        compose.onNodeWithText(dayLabel(DayOfWeek.WEDNESDAY)).performClick()

        assertEquals(listOf(DayOfWeek.WEDNESDAY), reported)
    }

    @Test
    fun `tapping In rotation clears every picked day`() {
        val reported = mutableListOf<DayOfWeek>()
        show(state(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), onToggle = { reported += it })

        compose.onNodeWithText("In rotation").performClick()

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), reported.toSet())
        assertEquals(2, reported.size)
    }

    @Test
    fun `tapping In rotation on a routine with no days reports nothing`() {
        val reported = mutableListOf<DayOfWeek>()
        show(state(), onToggle = { reported += it })

        compose.onNodeWithText("In rotation").performClick()

        assertEquals(emptyList<DayOfWeek>(), reported)
    }
}

package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The schedule line under each routine's name on the Fichas cards: "In rotation" or the days it is set to. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutinesFichasScheduleTest {
    @get:Rule
    val compose = createComposeRule()

    private fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun setTab(state: RoutinesUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = state,
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = RoutinesPage.ROUTINES,
                    )
                }
            }
        }
    }

    /** The sample Fichas with the routine at [index] set to [days] (empty means in rotation). */
    private fun fichasWithDays(index: Int, days: Set<DayOfWeek>): RoutinesUiState {
        val rows = GymSamples.routines.mapIndexed { i, row ->
            if (i == index) row.copy(routine = row.routine.copy(weekdays = days)) else row
        }
        return GymSamples.populated.copy(routines = rows)
    }

    @Test
    fun `every routine in rotation says In rotation on its card`() {
        setTab(GymSamples.populated)

        compose.onAllNodesWithText("In rotation").assertCountEquals(GymSamples.routines.size)
    }

    @Test
    fun `a routine on specific days lists its days in week order`() {
        setTab(fichasWithDays(index = 1, days = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)))

        compose.onNodeWithText("${dayLabel(DayOfWeek.MONDAY)} · ${dayLabel(DayOfWeek.WEDNESDAY)}").assertExists()
    }

    @Test
    fun `a routine on specific days does not say In rotation`() {
        setTab(fichasWithDays(index = 1, days = setOf(DayOfWeek.FRIDAY)))

        compose.onAllNodesWithText("In rotation").assertCountEquals(GymSamples.routines.size - 1)
        compose.onNodeWithText(dayLabel(DayOfWeek.FRIDAY)).assertExists()
    }

    @Test
    fun `the Fichas cards with a routine on specific days and the rest in rotation`() {
        setTab(fichasWithDays(index = 1, days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-fichas-schedule.png")
    }
}

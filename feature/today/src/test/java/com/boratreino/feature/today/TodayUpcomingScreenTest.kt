package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The rest-day line "Next training: ..." on the Today tab: a weekday, tomorrow, or nothing coming up. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayUpcomingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** The fixed rest-day sample with the given upcoming training (or none). */
    private fun restDayWith(upcoming: UpcomingUi?): TodayUiState = TodaySamples.restDay.copy(
        routines = RoutineUi(
            chips = TodaySamples.chips(null),
            freeSelected = false,
            restDay = true,
            upcoming = upcoming,
        ),
    )

    private fun setContent(state: TodayUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = TodayActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `a rest day with the next training on another weekday names the day and the routine`() {
        setContent(
            restDayWith(UpcomingUi(LocalDate.of(2026, 11, 4), TodaySamples.routineB.name, tomorrow = false)),
        )

        compose.onNodeWithText("Rest day").assertIsDisplayed()
        compose.onNodeWithText("Next training: Wednesday, Routine B").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-rest-upcoming-weekday.png")
    }

    @Test
    fun `a rest day with the next training tomorrow says tomorrow`() {
        setContent(
            restDayWith(UpcomingUi(LocalDate.of(2026, 11, 3), TodaySamples.routineB.name, tomorrow = true)),
        )

        compose.onNodeWithText("Rest day").assertIsDisplayed()
        compose.onNodeWithText("Next training: Tomorrow, Routine B").assertIsDisplayed()
        compose.onNodeWithText("Next training: Wednesday, Routine B").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-rest-upcoming-tomorrow.png")
    }

    @Test
    fun `a rest day with nothing coming up shows no next training line`() {
        setContent(restDayWith(null))

        compose.onNodeWithText("Rest day").assertIsDisplayed()
        compose.onNodeWithText("Next training", substring = true).assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-rest-no-upcoming.png")
    }
}

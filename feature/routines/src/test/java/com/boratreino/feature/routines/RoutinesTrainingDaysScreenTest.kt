package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Fichas page no longer draws the "which routine is next" card: the training days and the mode
 * chips live in the Agenda now. Whatever the stored schedule is, none of them is drawn here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutinesTrainingDaysScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val trainingDaysLabel: String
        get() = RuntimeEnvironment.getApplication().getString(R.string.routines_training_days)

    private val hint: String
        get() = RuntimeEnvironment.getApplication().getString(R.string.routines_training_days_hint)

    private fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun setContent(state: RoutinesUiState) {
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

    @Test
    fun `in turn, the Fichas page shows no training day label, hint or day chips`() {
        setContent(GymSamples.populated.copy(scheduleMode = ScheduleMode.IN_TURN, trainingDays = setOf(DayOfWeek.WEDNESDAY)))

        compose.onNodeWithText("Treino A").assertExists()
        compose.onNodeWithText(trainingDaysLabel).assertDoesNotExist()
        compose.onNodeWithText(hint).assertDoesNotExist()
        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertDoesNotExist()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-page-no-schedule.png")
    }

    @Test
    fun `on fixed weekdays, the Fichas page shows no training day label, hint or day chips either`() {
        setContent(GymSamples.populated.copy(scheduleMode = ScheduleMode.WEEKDAYS))

        compose.onNodeWithText(trainingDaysLabel).assertDoesNotExist()
        compose.onNodeWithText(hint).assertDoesNotExist()
        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertDoesNotExist()
        }
    }
}

package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The training day chips in the schedule card: shown in In turn only, and each tap reports the new set. */
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

    private fun setContent(state: RoutinesUiState, onTrainingDaysChange: (Set<DayOfWeek>) -> Unit = {}) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = state,
                        callbacks = RoutinesCallbacks(onTrainingDaysChange = onTrainingDaysChange),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = RoutinesPage.ROUTINES,
                    )
                }
            }
        }
    }

    @Test
    fun `in turn, the training days label, the hint and all seven day chips are shown`() {
        setContent(GymSamples.populated.copy(scheduleMode = ScheduleMode.IN_TURN, trainingDays = emptySet()))

        compose.onNodeWithText(trainingDaysLabel).assertIsDisplayed()
        compose.onNodeWithText(hint).assertIsDisplayed()
        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertIsDisplayed()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-training-days.png")
    }

    @Test
    fun `on fixed weekdays, no training day label, hint or chips are shown`() {
        setContent(GymSamples.populated.copy(scheduleMode = ScheduleMode.WEEKDAYS))

        compose.onNodeWithText(trainingDaysLabel).assertDoesNotExist()
        compose.onNodeWithText(hint).assertDoesNotExist()
        for (day in DayOfWeek.entries) {
            compose.onNodeWithText(dayLabel(day)).assertDoesNotExist()
        }
    }

    @Test
    fun `tapping a day that is not picked adds it to the picked days`() {
        var reported: Set<DayOfWeek>? = null
        setContent(
            GymSamples.populated.copy(scheduleMode = ScheduleMode.IN_TURN, trainingDays = setOf(DayOfWeek.WEDNESDAY)),
            onTrainingDaysChange = { reported = it },
        )

        compose.onNodeWithText(dayLabel(DayOfWeek.FRIDAY)).performClick()

        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), reported)
    }

    @Test
    fun `tapping a picked day removes it from the picked days`() {
        var reported: Set<DayOfWeek>? = null
        setContent(
            GymSamples.populated.copy(scheduleMode = ScheduleMode.IN_TURN, trainingDays = setOf(DayOfWeek.WEDNESDAY)),
            onTrainingDaysChange = { reported = it },
        )

        compose.onNodeWithText(dayLabel(DayOfWeek.WEDNESDAY)).performClick()

        assertEquals(emptySet<DayOfWeek>(), reported)
    }
}

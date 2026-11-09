package com.boratreino.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Settings grouped in four labelled sections (Training, Body, Data, App) and the compact weekly
 * goal row. Tall viewport so every section is on screen at once; saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h4000dp")
class SettingsSectionsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun state(goal: Int = 4) = SettingsUiState(
        loaded = true,
        weeklyGoal = goal,
        reminders = ReminderSettings(
            enabled = true,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            time = LocalTime.of(18, 0),
        ),
        restTimer = RestTimerSettings(enabled = true, defaultSeconds = 90, beep = false),
        notificationsGranted = true,
        is24Hour = true,
        installedVersion = "0.40",
        language = AppLanguage.ENGLISH,
    )

    private fun setContent(state: SettingsUiState, actions: SettingsActions = SettingsActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** The top edge of a visible title, in root coordinates. */
    private fun top(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `the four section titles are in order`() {
        setContent(state())

        val tops = listOf("Training", "Body", "Data", "App").map { top(it) }

        assertTrue("sections out of order: $tops", tops.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `the training cards sit under the Training title and above Body`() {
        setContent(state())

        val training = top("Training")
        val body = top("Body")
        assertTrue(top("Weekly goal") > training)
        assertTrue(top("Rest timer") > training)
        assertTrue(top("Rest timer") < body)
        assertTrue(top("Warm-up") < body)
    }

    @Test
    fun `the weekly goal row shows the value`() {
        setContent(state(goal = 4))

        compose.onNodeWithText("Weekly goal").assertExists()
        compose.onNode(hasText("4") and hasContentDescription("Days per week")).assertExists()
    }

    @Test
    fun `the weekly goal row shows a changed value`() {
        setContent(state(goal = 5))

        compose.onNode(hasText("5") and hasContentDescription("Days per week")).assertExists()
    }

    @Test
    fun `plus and minus change the weekly goal by one step each`() {
        val steps = mutableListOf<Boolean>()
        setContent(state(goal = 4), SettingsActions(onStepGoal = { steps += it }))

        compose.onNodeWithContentDescription("Increase Days per week").performClick()
        compose.onNodeWithContentDescription("Decrease Days per week").performClick()

        assertEquals(listOf(true, false), steps)
    }

    @Test
    fun `the sections shot`() {
        setContent(state())

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-sections.png")
    }
}

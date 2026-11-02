package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Calendar tab and the session editor, drawn from fixed states; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CalendarScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shootCalendar(name: String, state: CalendarUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        CalendarContent(state = state, actions = CalendarActions(), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun shootEditor(name: String, state: SessionEditorUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `calendar month`() = shootCalendar("calendar-month", CalendarSamples.monthWithTraining)

    @Test
    fun `calendar month dark`() = shootCalendar("calendar-month-dark", CalendarSamples.monthWithTraining, dark = true)

    @Test
    fun `calendar empty day`() = shootCalendar("calendar-empty-day", CalendarSamples.emptyToday)

    @Test
    fun `calendar goal dialog`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(state = CalendarSamples.goalDialog, actions = CalendarActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/calendar-goal-dialog.png")
    }

    @Test
    fun `session editor`() = shootEditor("session-editor", CalendarSamples.editorState)

    @Test
    fun `session editor picker`() = shootEditor("session-editor-picker", CalendarSamples.editorPicking)

    @Test
    fun `the day detail is blank before the first read`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(
                        state = CalendarSamples.emptyToday.copy(loaded = false),
                        actions = CalendarActions(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        compose.onNodeWithText("Nothing logged").assertDoesNotExist()
        compose.onNodeWithText("Add a training").assertDoesNotExist()
    }
}

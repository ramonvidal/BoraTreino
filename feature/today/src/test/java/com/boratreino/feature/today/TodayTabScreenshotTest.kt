package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
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

/** The Today tab with nothing open and with a session open; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayTabScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: TodayUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        TodayContent(state = state, actions = TodayActions(), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `empty light`() = shoot("today-empty", TodaySamples.noSession)

    @Test
    fun `empty dark`() = shoot("today-empty-dark", TodaySamples.noSession, dark = true)

    @Test
    fun `empty large text`() = shoot("today-empty-large", TodaySamples.noSession, fontScale = 2f)

    @Test
    fun `session light`() = shoot("today-session", TodaySamples.openSession)

    @Test
    fun `session dark`() = shoot("today-session-dark", TodaySamples.openSession, dark = true)

    @Test
    fun `session large text`() = shoot("today-session-large", TodaySamples.openSession, fontScale = 2f)

    @Test
    fun `routine B is next, nothing done yet`() = shoot("today-routine", TodaySamples.routineBNotStarted)

    @Test
    fun `routine B in progress`() = shoot("today-routine-session", TodaySamples.routineBInProgress)

    @Test
    fun `routine B in progress dark`() = shoot("today-routine-dark", TodaySamples.routineBInProgress, dark = true)

    @Test
    fun `rest day on fixed weekdays`() = shoot("today-rest-day", TodaySamples.restDay)

    @Test
    fun `before the first read the body is blank`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = TodayUiState(loaded = false), actions = TodayActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }

        compose.onNodeWithText("Pick a machine to start").assertDoesNotExist()
        compose.onNodeWithText("All machines").assertDoesNotExist()
    }
}

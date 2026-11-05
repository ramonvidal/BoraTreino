package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.DayKind
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The quiet energy line under the selected day's sessions, in English; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CalendarEnergyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val trained = CalendarSamples.monthWithTraining.copy(energy = DayEnergyUi(2450, DayKind.TRAINED))
    private val rest = CalendarSamples.emptyToday.copy(energy = DayEnergyUi(2150, DayKind.REST))
    private val hidden = CalendarSamples.monthWithTraining

    private fun shoot(name: String, state: CalendarUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(state = state, actions = CalendarActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `trained day`() = shoot("calendar-energy", trained)

    @Test
    fun `rest day`() = shoot("calendar-energy-rest", rest)

    @Test
    fun `hidden`() = shoot("calendar-energy-hidden", hidden)

    @Test
    fun `trained day dark`() = shoot("calendar-energy-dark", trained, dark = true)
}

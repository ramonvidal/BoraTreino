package com.boratreino.feature.today

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

/** The quiet energy line on Today: light and dark, English; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayEnergyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val trainingDay = TodaySamples.routineBNotStarted.copy(energy = DayEnergyUi(2420, DayKind.TRAINING_EXPECTED))
    private val trainedDay = TodaySamples.routineBInProgress.copy(energy = DayEnergyUi(2450, DayKind.TRAINED))
    private val restDay = TodaySamples.restDay.copy(energy = DayEnergyUi(2150, DayKind.REST))
    private val hidden = TodaySamples.routineBNotStarted
    private val withoutRoutines = TodaySamples.openSession.copy(energy = DayEnergyUi(2450, DayKind.TRAINED))

    private fun shoot(name: String, state: TodayUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = TodayActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `training day light`() = shoot("today-energy", trainingDay)

    @Test
    fun `trained day light`() = shoot("today-energy-trained", trainedDay)

    @Test
    fun `rest day light`() = shoot("today-energy-rest", restDay)

    @Test
    fun `hidden light`() = shoot("today-energy-hidden", hidden)

    @Test
    fun `training day dark`() = shoot("today-energy-dark", trainingDay, dark = true)

    @Test
    fun `rest day dark`() = shoot("today-energy-rest-dark", restDay, dark = true)

    @Test
    fun `hidden dark`() = shoot("today-energy-hidden-dark", hidden, dark = true)

    @Test
    fun `without routines light`() = shoot("today-energy-free", withoutRoutines)
}

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

/** The quiet energy line on Today: in Baianês; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class TodayEnergyPtScreenshotTest {
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
    fun `training day pt`() = shoot("today-energy-pt", trainingDay)

    @Test
    fun `rest day pt`() = shoot("today-energy-rest-pt", restDay)

    @Test
    fun `hidden pt`() = shoot("today-energy-hidden-pt", hidden)

    @Test
    fun `training day dark pt`() = shoot("today-energy-dark-pt", trainingDay, dark = true)
}

package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.CardioWeek
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Cardio in Progress: a cardio machine's detail (minutes chart, weekly bars, weeks list) and the overview card. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ProgressCardioScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today

    private fun shootMachine(name: String, state: MachineProgressUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        MachineProgressContent(state = state, actions = MachineProgressActions(), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun shootOverview(name: String, state: ProgressUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    ProgressContent(state = state, actions = ProgressActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun treadmill(range: ProgressRange = ProgressRange.THREE_MONTHS) = buildMachineState(
        ProgressCardioSamples.treadmillHistory, range, today, selected = null, showAll = false,
    )

    @Test
    fun `cardio machine progress`() {
        shootMachine("progress-cardio", treadmill())
        compose.onNodeWithText("Minutes per week").assertExists()
        compose.onNodeWithText("This week · 45 min · 320 kcal · 3.2 km").assertExists()
    }

    @Test
    fun `cardio machine progress dark`() = shootMachine("progress-cardio-dark", treadmill(), dark = true)

    @Test
    fun `cardio machine progress large font`() = shootMachine("progress-cardio-large", treadmill(), fontScale = 2f)

    @Test
    fun `cardio machine progress over a year`() =
        shootMachine("progress-cardio-1year", treadmill(ProgressRange.ONE_YEAR))

    @Test
    fun `cardio machine with no week used`() {
        val state = buildMachineState(
            ProgressCardioSamples.treadmillHistory.copy(weeks = ProgressCardioSamples.emptyWeeks),
            ProgressRange.THREE_MONTHS, today, selected = null, showAll = false,
        )
        shootMachine("progress-cardio-empty-weeks", state)
        compose.onNodeWithText("This week · 45 min · 320 kcal · 3.2 km").assertDoesNotExist()
    }

    @Test
    fun `a weight machine has no weeks card`() {
        shootMachine(
            "progress-cardio-none-on-weight",
            buildMachineState(ProgressSamples.legPressHistory, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false),
        )
        compose.onNodeWithText("Minutes per week").assertDoesNotExist()
    }

    @Test
    fun `overview with the cardio this week card`() {
        shootOverview("progress-cardio-overview", buildProgressState(ProgressCardioSamples.overview, today))
        compose.onNodeWithText("Cardio this week: 45 min · 320 kcal · 3.2 km").assertExists()
    }

    @Test
    fun `overview with the cardio this week card dark`() =
        shootOverview("progress-cardio-overview-dark", buildProgressState(ProgressCardioSamples.overview, today), dark = true)

    @Test
    fun `overview without cardio this week has no card`() {
        shootOverview("progress-cardio-overview-none", buildProgressState(ProgressSamples.overview, today))
        compose.onNodeWithText("Cardio this week: 45 min · 320 kcal · 3.2 km").assertDoesNotExist()
    }

    // WeeklyBars on its own

    private fun shootBars(name: String, weeks: List<CardioWeek>, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    WeeklyBars(weeks, description = "Minutes per week", modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `weekly bars with twelve weeks`() = shootBars("progress-cardio-bars", ProgressCardioSamples.twelveWeeks)

    @Test
    fun `weekly bars with empty weeks draw only the grid`() =
        shootBars("progress-cardio-bars-empty", ProgressCardioSamples.emptyWeeks)

    @Test
    fun `weekly bars with no weeks at all do not crash`() = shootBars("progress-cardio-bars-none", emptyList())

    @Test
    fun `weekly bars dark`() = shootBars("progress-cardio-bars-dark", ProgressCardioSamples.twelveWeeks, dark = true)
}

/** The same cardio screens in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class ProgressCardioPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `cardio machine progress pt`() {
        val state = buildMachineState(
            ProgressCardioSamples.treadmillHistory, ProgressRange.THREE_MONTHS, ProgressSamples.today, selected = null, showAll = false,
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineProgressContent(state = state, actions = MachineProgressActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithText("Minutos por semana").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/progress-cardio-pt.png")
    }

    @Test
    fun `overview with the cardio card pt`() {
        val state = buildProgressState(ProgressCardioSamples.overview, ProgressSamples.today)
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProgressContent(state = state, actions = ProgressActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithText("Cardio da semana: 45 min · 320 kcal · 3,2 km").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/progress-cardio-overview-pt.png")
    }
}

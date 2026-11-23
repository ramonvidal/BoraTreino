package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The cardio detail of a bike with every chip (phase 42); saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class BikeProgressScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today

    private fun shoot(name: String, state: MachineProgressUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    MachineProgressContent(state = state, actions = MachineProgressActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `bike progress with every chip`() {
        val state = buildMachineState(BikeProgressSamples.allChips, ProgressRange.THREE_MONTHS, today, null, false)
        shoot("progress-bike-all-chips", state)

        compose.onNodeWithText("Best power: 160 W").assertExists()
        compose.onNodeWithText("Best TSS: 48").assertExists()
    }

    @Test
    fun `bike progress with every chip in the dark`() {
        val state = buildMachineState(BikeProgressSamples.allChips, ProgressRange.THREE_MONTHS, today, null, false)
        shoot("progress-bike-all-chips-dark", state, dark = true)
    }

    @Test
    fun `bike progress with the TSS chip chosen`() {
        val state = buildMachineState(
            BikeProgressSamples.allChips, ProgressRange.THREE_MONTHS, today, null, false, cardioMetric = "TSS",
        )
        shoot("progress-bike-tss-chosen", state)
    }
}

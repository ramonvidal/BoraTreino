package com.boratreino.feature.setup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The three steps of the first-run setup, saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SetupScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: SetupUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SetupContent(state = state, callbacks = SetupCallbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `step one with two exercises ticked`() = shoot("setup-gym", SetupSamples.gym)

    @Test
    fun `step one with nothing ticked`() = shoot("setup-gym-empty", SetupSamples.gymEmpty)

    @Test
    fun `step two with the exercises from step one`() = shoot("setup-routine", SetupSamples.routine)

    @Test
    fun `step two with nothing chosen`() = shoot("setup-routine-none", SetupSamples.routineNone)

    @Test
    fun `step three with three days picked`() = shoot("setup-days", SetupSamples.days)
}

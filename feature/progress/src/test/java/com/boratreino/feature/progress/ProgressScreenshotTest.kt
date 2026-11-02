package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Progress overview in a few states; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ProgressScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: ProgressUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        ProgressContent(state = state, actions = ProgressActions(), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val overview: ProgressUiState = buildProgressState(ProgressSamples.overview, ProgressSamples.today)
    private val empty: ProgressUiState = buildProgressState(ProgressOverview(emptyList(), 0, 0), ProgressSamples.today)

    @Test
    fun `progress overview`() = shoot("progress-overview", overview)

    @Test
    fun `progress overview dark`() = shoot("progress-overview-dark", overview, dark = true)

    @Test
    fun `progress overview large text`() = shoot("progress-overview-large", overview, fontScale = 2f)

    @Test
    fun `progress empty`() = shoot("progress-empty", empty)
}

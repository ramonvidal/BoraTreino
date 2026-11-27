package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Analysis level opened from the Overview, as one tall image so that no card is cut off; saved to
 * build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h12000dp")
class AnalysisScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun analysis(name: String, state: StatsUiState, dark: Boolean = false) {
        compose.setContent { AnalysisHost(state, dark = dark) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `analysis full`() = analysis("analysis-full", StatsSamples.state())

    @Test
    fun `analysis full dark`() = analysis("analysis-full-dark", StatsSamples.state(), dark = true)

    @Test
    fun `analysis one week`() = analysis("analysis-one-week", StatsSamples.state(StatsSamples.oneWeek))
}

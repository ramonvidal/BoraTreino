package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Analysis level in Baianês, as one tall image so that no card is cut off; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h12000dp")
class AnalysisPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `analysis full`() {
        compose.setContent { AnalysisHost(StatsSamples.state()) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/analysis-full-pt.png")
    }
}

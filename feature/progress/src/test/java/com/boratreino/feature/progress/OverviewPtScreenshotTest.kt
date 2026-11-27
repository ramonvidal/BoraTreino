package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.StatsPeriod
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Overview in Baianês at the narrowest phone width, with the strength section; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w360dp-h3000dp")
class OverviewPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun card(name: String, content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-pt.png")
    }

    @Test
    fun `overview full`() {
        compose.setContent { OverviewHost(OverviewSamples.state()) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/overview-full-pt.png")
    }

    @Test
    fun `finding low volume`() = card("overview-finding-low-volume") {
        FindingCard(OverviewSamples.lowVolume, onClick = {})
    }

    @Test
    fun `strength section with a flagged jump`() = card("overview-strength-check") {
        StrengthSection(OverviewSamples.strength, StatsPeriod.FOUR_WEEKS, OverviewSamples.today, onOpenMachine = {})
    }
}

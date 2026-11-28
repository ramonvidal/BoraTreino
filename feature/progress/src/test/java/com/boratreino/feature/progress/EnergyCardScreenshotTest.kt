package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.EnergyHistory
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Daily energy card, light and dark, and with only a week of data; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1000dp")
class EnergyCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, history: EnergyHistory, dark: Boolean = false) {
        compose.setContent { StatsCardHost(dark = dark) { EnergyCard(history) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `energy card`() = shoot("energy-card", StatsSamples.energyHistory)

    @Test
    fun `energy card dark`() = shoot("energy-card-dark", StatsSamples.energyHistory, dark = true)

    @Test
    fun `energy card with a week of data`() = shoot(
        "energy-card-week",
        StatsSamples.energyHistory.copy(points = StatsSamples.energyHistory.points.takeLast(7), last28AverageKcal = null),
    )
}

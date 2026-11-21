package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The body cards on their own, light; saved to build/outputs/roborazzi. The words are checked in the card tests. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class BodyInsightScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun card(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost(dark = dark, fontScale = fontScale) { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `stats body map`() = card("stats-body-map") { BodyMapCard(BodyInsightSamples.bodyMapReport) }

    @Test
    fun `stats body map with every shade`() = card("stats-body-map-shades") { BodyMapCard(BodyInsightSamples.shadeReport) }

    @Test
    fun `stats body map with a muscle selected`() {
        compose.setContent { StatsCardHost { BodyMapCard(BodyInsightSamples.bodyMapReport) } }
        // The middle of the front chest: its number shows below the figures.
        compose.tapBodyMap(BodyInsightSamples.BODY_MAP_FIGURES, front = true, gridX = 60.2f, gridY = 49.85f)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/stats-body-map-selected.png")
    }

    @Test
    fun `stats body map dark`() =
        card("stats-body-map-dark", dark = true) { BodyMapCard(BodyInsightSamples.bodyMapReport) }

    @Test
    fun `stats body map large font`() =
        card("stats-body-map-large-font", fontScale = 1.6f) { BodyMapCard(BodyInsightSamples.bodyMapReport) }

    @Test
    fun `stats muscles`() = card("stats-muscles") { MusclesCard(BodyInsightSamples.bodyMapReport) }

    @Test
    fun `stats muscles dark`() = card("stats-muscles-dark", dark = true) { MusclesCard(BodyInsightSamples.bodyMapReport) }

    @Test
    fun `stats relative strength`() = card("stats-relative-strength") {
        RelativeStrengthCard(BodyInsightSamples.state(StatsSamples.full.copy(strengthNow = BodyInsightSamples.strengthNow), bodyWeightKg = 80.0))
    }

    @Test
    fun `stats vs weight`() = card("stats-vs-weight") {
        StrengthVsWeightCard(
            BodyInsightSamples.state(
                StatsSamples.full.copy(strengthIndex = BodyInsightSamples.strengthIndex),
                bodyWeightKg = 76.0,
                weights = BodyInsightSamples.weights,
            ),
        )
    }

    @Test
    fun `stats balance`() = card("stats-balance") { BalanceCard(BodyInsightSamples.balance) }
}

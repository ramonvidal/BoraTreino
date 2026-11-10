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

    private fun card(name: String, content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `stats body map`() = card("stats-body-map") { BodyMapCard(BodyInsightSamples.bodyMapReport) }

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

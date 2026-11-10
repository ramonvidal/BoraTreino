package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.IndexPoint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Strength per body weight" and "Body weight and strength together": what they say, and when they are left out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h3000dp")
class BodyStrengthCardsTest {
    @get:Rule
    val compose = createComposeRule()

    private val report = StatsSamples.full

    private fun showCard(content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost { content() } }
    }

    private fun showPage(state: StatsUiState) {
        compose.setContent { StatsScreenHost { StatsContent(state, StatsActions(), Modifier.fillMaxSize()) } }
    }

    // Strength per body weight

    @Test
    fun `the machines are ranked by estimate over body weight, best first`() {
        val state = BodyInsightSamples.state(report.copy(strengthNow = BodyInsightSamples.strengthNow), bodyWeightKg = 80.0)
        showCard { RelativeStrengthCard(state) }

        compose.onNodeWithText("Strength per body weight").assertIsDisplayed()
        compose.onNodeWithContentDescription("Leg press: 1.4 ×. Bench press: 1.2 ×. Lat pulldown: 0.8 ×").assertIsDisplayed()
        compose.onNodeWithText("Times your body weight (80 kg), by estimated 1RM").assertIsDisplayed()
    }

    @Test
    fun `a bodyweight machine is never ranked`() {
        val state = BodyInsightSamples.state(report.copy(strengthNow = BodyInsightSamples.strengthNow), bodyWeightKg = 80.0)
        showCard { RelativeStrengthCard(state) }

        compose.onNodeWithContentDescription("Pull-up", substring = true).assertDoesNotExist()
    }

    @Test
    fun `only the eight best machines are ranked`() {
        val state = BodyInsightSamples.state(report.copy(strengthNow = BodyInsightSamples.tenMachines), bodyWeightKg = 80.0)
        showCard { RelativeStrengthCard(state) }

        compose.onNodeWithContentDescription("Machine 10: 2.0 ×", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Machine 3: 1.3 ×", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Machine 2: 1.2 ×", substring = true).assertDoesNotExist()
    }

    @Test
    fun `without a body weight the card gives a hint instead of a ranking`() {
        val state = BodyInsightSamples.state(report.copy(strengthNow = BodyInsightSamples.strengthNow), bodyWeightKg = null)
        showCard { RelativeStrengthCard(state) }

        compose.onNodeWithText("Log your weight in Settings to see strength per body weight.").assertIsDisplayed()
        compose.onNodeWithContentDescription("×", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a body weight of zero also gives the hint`() {
        val state = BodyInsightSamples.state(report.copy(strengthNow = BodyInsightSamples.strengthNow), bodyWeightKg = 0.0)
        showCard { RelativeStrengthCard(state) }

        compose.onNodeWithText("Log your weight in Settings to see strength per body weight.").assertIsDisplayed()
        compose.onNodeWithContentDescription("×", substring = true).assertDoesNotExist()
    }

    // Body weight and strength together

    @Test
    fun `body weight and strength are described together, both indexed to 100`() {
        val state = BodyInsightSamples.state(
            report.copy(strengthIndex = BodyInsightSamples.strengthIndex),
            bodyWeightKg = 76.0,
            weights = BodyInsightSamples.weights,
        )
        showCard { StrengthVsWeightCard(state) }

        compose.onNodeWithText("Body weight and strength together").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Lines indexed to 100 at the first week, over 13 weeks. Body weight -4.0 kg (-5.0 %), strength +8.0 %",
        ).assertIsDisplayed()
        compose.onNodeWithText("Body weight", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("95 %", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("108 %", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `without weight entries only the strength line is drawn`() {
        val state = BodyInsightSamples.state(report.copy(strengthIndex = BodyInsightSamples.strengthIndex))
        showCard { StrengthVsWeightCard(state) }

        compose.onNodeWithContentDescription(
            "Lines indexed to 100 at the first week, over 13 weeks. Strength +8.0 %",
        ).assertIsDisplayed()
        compose.onNodeWithText("Body weight", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Strength", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the chart is left out of the page with fewer than two strength points`() {
        showPage(BodyInsightSamples.state(report.copy(strengthIndex = listOf(IndexPoint(BodyInsightSamples.weekStart(12), 100.0)))))

        compose.onNodeWithText("Body weight and strength together").assertDoesNotExist()
    }

    @Test
    fun `the chart is on the page with two strength points`() {
        showPage(BodyInsightSamples.state(report.copy(strengthIndex = BodyInsightSamples.strengthIndex.takeLast(2))))

        compose.onNodeWithText("Body weight and strength together").assertIsDisplayed()
    }
}

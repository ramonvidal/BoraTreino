package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.StatsReport
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The body map card: what it says about the average sets a week over the period, and when the page leaves it out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h3000dp")
class BodyMapCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val report = BodyInsightSamples.bodyMapReport

    private fun showCard(report: StatsReport) {
        compose.setContent { StatsCardHost { BodyMapCard(report) } }
    }

    private fun showPage(report: StatsReport) {
        compose.setContent {
            StatsScreenHost { StatsContent(BodyInsightSamples.state(report), StatsActions(), Modifier.fillMaxSize()) }
        }
    }

    @Test
    fun `the card has its title and says the sets are averages per week in this period`() {
        showCard(report)

        compose.onNodeWithText("Body map").assertIsDisplayed()
        compose.onNodeWithText("Average sets per week in this period (indirect sets count as half)").assertIsDisplayed()
    }

    @Test
    fun `the figures describe the average sets a week of each group over the whole period`() {
        showCard(report)

        compose.onNodeWithContentDescription(
            "Legs: 10.0 sets a week (10.0 direct), Chest: 8.0 sets a week (8.0 direct), " +
                "Back: 0.9 sets a week (0.9 direct), Shoulders: 0.2 sets a week (0.2 direct), " +
                "Arms: 0.1 sets a week (0.1 direct), Core: 0.0 sets a week (0.0 direct)",
        ).assertIsDisplayed()
    }

    @Test
    fun `the front and back figures are labelled`() {
        showCard(report)

        compose.onNodeWithText("Front", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Back", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the legend names the four weekly shades`() {
        showCard(report)

        listOf("No sets", "Under 5 a week", "5 to 9 a week", "10 or more a week").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `the most and least trained groups are named with their average sets a week`() {
        showCard(report)

        compose.onNodeWithText("Most trained: Legs (10.0 a week); least: Core (0.0 a week)").assertIsDisplayed()
    }

    @Test
    fun `the most and least line is left out when no group has sets`() {
        showCard(report.copy(muscleWeeks = emptyList()))

        compose.onNodeWithText("Most trained", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the body map is on the page between the muscle groups card and the summary`() {
        showPage(report)

        val week = compose.onNodeWithText("Muscle groups").fetchSemanticsNode().positionInRoot.y
        val body = compose.onNodeWithText("Body map").fetchSemanticsNode().positionInRoot.y
        val summary = compose.onNodeWithText("Summary").fetchSemanticsNode().positionInRoot.y
        assertTrue(week < body && body < summary)
    }

    @Test
    fun `the body map is left out of the page when the period has no sets`() {
        showPage(report.copy(muscleWeeks = emptyList()))

        compose.onNodeWithText("Body map").assertDoesNotExist()
    }
}

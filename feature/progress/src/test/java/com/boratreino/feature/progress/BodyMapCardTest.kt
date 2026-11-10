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

/** The body map card: what it says about the last four weeks, and when the page leaves it out. */
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
    fun `the card has its title and says it counts the last four weeks`() {
        showCard(report)

        compose.onNodeWithText("Body map").assertIsDisplayed()
        compose.onNodeWithText("Working sets in the last 4 weeks").assertIsDisplayed()
    }

    @Test
    fun `the figures describe the sets of each group in the last four weeks, old weeks left out`() {
        showCard(report)

        compose.onNodeWithContentDescription(
            "Legs: 40 sets, Chest: 6 sets, Back: 0 sets, Shoulders: 15 sets, Arms: 1 set, Core: 0 sets",
        ).assertIsDisplayed()
    }

    @Test
    fun `the front and back figures are labelled`() {
        showCard(report)

        compose.onNodeWithText("Front", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Back", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the legend names the four shades`() {
        showCard(report)

        listOf("No sets", "1 to 9 sets", "10 to 19 sets", "20 or more sets").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `the most and least trained groups are named with their sets`() {
        showCard(report)

        compose.onNodeWithText("Most trained: Legs (40 sets); least: Back (0)").assertIsDisplayed()
    }

    @Test
    fun `the most and least line is left out when no group has sets`() {
        showCard(report.copy(muscleWeeks = emptyList()))

        compose.onNodeWithText("Most trained", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the body map is on the page between the muscle groups card and the summary`() {
        showPage(report)

        val week = compose.onNodeWithText("Muscle groups this week").fetchSemanticsNode().positionInRoot.y
        val body = compose.onNodeWithText("Body map").fetchSemanticsNode().positionInRoot.y
        val summary = compose.onNodeWithText("Summary").fetchSemanticsNode().positionInRoot.y
        assertTrue(week < body && body < summary)
    }

    @Test
    fun `the body map is left out of the page when the last four weeks have no sets`() {
        // Only week 6 has sets: outside the last four weeks.
        val oldOnly = report.copy(muscleWeeks = BodyInsightSamples.muscleWeeks.filter { it.weekStart == BodyInsightSamples.weekStart(6) })

        showPage(oldOnly)

        compose.onNodeWithText("Body map").assertDoesNotExist()
    }
}

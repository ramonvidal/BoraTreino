package com.boratreino.feature.progress

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.StatsReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The body map card: what it says about the average sets a week of each specific muscle over the period, what a tap
 * on a region shows, and when the page leaves it out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h20000dp")
class BodyMapCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val report = BodyInsightSamples.bodyMapReport

    private fun showCard(report: StatsReport) {
        compose.setContent { StatsCardHost { BodyMapCard(report) } }
    }

    private fun showPage(report: StatsReport) {
        compose.setContent { AnalysisHost(BodyInsightSamples.state(report), section = AnalysisSection.MUSCLES) }
    }

    private fun yOf(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    @Test
    fun `the card has its title and says the sets are averages per week in this period`() {
        showCard(report)

        compose.onNodeWithText("Body map").assertIsDisplayed()
        compose.onNodeWithText("Average sets per week in this period (indirect sets count as half)").assertIsDisplayed()
    }

    @Test
    fun `the figures describe the average sets a week of each specific muscle over the whole period`() {
        showCard(report)

        compose.onNodeWithContentDescription(BodyInsightSamples.BODY_MAP_FIGURES).assertIsDisplayed()
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

        listOf("No sets", "Under 3 a week", "3 to 5 a week", "6 or more a week").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `the most and least trained muscles are named with their average sets a week`() {
        showCard(report)

        compose.onNodeWithText("Most trained: Chest (8.0 a week); least: Abs (0.0 a week)").assertIsDisplayed()
    }

    @Test
    fun `the most and least line is left out when no muscle has sets`() {
        showCard(report.copy(muscleWeeks = emptyList(), detailWeeks = emptyList()))

        compose.onNodeWithText("Most trained", substring = true).assertDoesNotExist()
    }

    // Taps: the number of the muscle under the finger

    @Test
    fun `tapping a region of the front shows the number of its muscle below the figures`() {
        showCard(report)

        // The middle of the front chest.
        compose.tapBodyMap(BodyInsightSamples.BODY_MAP_FIGURES, front = true, gridX = 60.2f, gridY = 49.85f)

        compose.onNodeWithText("Chest: 8.0 sets a week (8.0 direct)").assertIsDisplayed()
    }

    @Test
    fun `tapping the same region again hides its number`() {
        showCard(report)

        compose.tapBodyMap(BodyInsightSamples.BODY_MAP_FIGURES, front = true, gridX = 60.2f, gridY = 49.85f)
        compose.tapBodyMap(BodyInsightSamples.BODY_MAP_FIGURES, front = true, gridX = 60.2f, gridY = 49.85f)

        compose.onNodeWithText("Chest: 8.0 sets a week (8.0 direct)").assertDoesNotExist()
    }

    @Test
    fun `tapping a region of the back shows its number`() {
        showCard(report)

        // The middle of the upper back, on the back figure.
        compose.tapBodyMap(BodyInsightSamples.BODY_MAP_FIGURES, front = false, gridX = 35.8f, gridY = 56.4f)

        compose.onNodeWithText("Upper back: 0.3 sets a week (0.3 direct)").assertIsDisplayed()
    }

    @Test
    fun `tapping empty space hides the number`() {
        showCard(report)
        compose.tapBodyMap(BodyInsightSamples.BODY_MAP_FIGURES, front = true, gridX = 60.2f, gridY = 49.85f)

        compose.tapBodyMap(BodyInsightSamples.BODY_MAP_FIGURES, front = true, gridX = 1f, gridY = 1f)

        compose.onNodeWithText("Chest: 8.0 sets a week (8.0 direct)").assertDoesNotExist()
    }

    @Test
    fun `tapping a piece without a muscle, the head, shows no number`() {
        showCard(report)

        // The middle of the head, which is never shaded.
        compose.tapBodyMap(BodyInsightSamples.BODY_MAP_FIGURES, front = true, gridX = 49.9f, gridY = 13.7f)

        compose.onAllNodesWithText("sets a week (", substring = true).assertCountEquals(0)
    }

    // The page

    @Test
    fun `the body map is in the analysis below the muscle groups card`() {
        showPage(report)

        val week = compose.onNodeWithText("Muscle groups").fetchSemanticsNode().positionInRoot.y
        val body = compose.onNodeWithText("Body map").fetchSemanticsNode().positionInRoot.y
        assertTrue(week < body)
    }

    @Test
    fun `the muscles card is in the analysis right after the body map`() {
        // "Muscles" is also the name of its group heading, so the order is checked on the card list.
        val cards = analysisCards(BodyInsightSamples.state(report))

        assertEquals(cards.indexOf(AnalysisCard.BODY_MAP) + 1, cards.indexOf(AnalysisCard.MUSCLES))
    }

    @Test
    fun `the body map is left out of the page when the period has no sets`() {
        showPage(report.copy(muscleWeeks = emptyList(), detailWeeks = emptyList()))

        compose.onNodeWithText("Body map").assertDoesNotExist()
    }
}

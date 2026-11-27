package com.boratreino.feature.progress

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Muscle
import com.boratreino.core.model.MuscleDetailWeek
import com.boratreino.core.model.StatsReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Muscles card: one bar per specific muscle, highest average first. The muscles with no sets are hidden behind
 * "Show all 14 muscles"; the card is left out when no muscle is trained.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h20000dp")
class MusclesCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val report = BodyInsightSamples.bodyMapReport

    private val showAllButton = "Show all 14 muscles"

    private fun showCard(report: StatsReport) {
        compose.setContent { StatsCardHost { MusclesCard(report) } }
    }

    private fun yOf(text: String): Float = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    private fun showAll() = compose.onNodeWithText(showAllButton).performClick()

    @Test
    fun `the card is titled Muscles and says the sets are averages per week`() {
        showCard(report)

        compose.onNodeWithText("Muscles").assertIsDisplayed()
        compose.onNodeWithText("Average sets per week in this period (indirect sets count as half)").assertIsDisplayed()
    }

    @Test
    fun `the rows are the trained muscles highest average first`() {
        showCard(report)

        // Chest 8.0, the 2.0 legs, upper back 0.3, shoulders 0.2.
        assertTrue(yOf("Chest") < yOf("Quadriceps"))
        assertTrue(yOf("Quadriceps") < yOf("Upper back"))
        assertTrue(yOf("Upper back") < yOf("Shoulders"))
    }

    @Test
    fun `the muscles with no sets are hidden until show all is tapped`() {
        showCard(report)

        listOf("Biceps", "Triceps", "Forearms", "Abs").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist()
        }

        showAll()

        listOf("Biceps", "Triceps", "Forearms", "Abs").forEach {
            assertTrue("$it is shown", compose.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `the untrained muscles are the last rows once shown`() {
        showCard(report)

        showAll()

        listOf("Biceps", "Triceps", "Forearms", "Abs").forEach {
            assertTrue("$it is below shoulders", yOf(it) > yOf("Shoulders"))
        }
    }

    @Test
    fun `the rows say the average with one decimal`() {
        showCard(report)

        compose.onNodeWithText("8.0", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("2.0", useUnmergedTree = true).assertCountEquals(5)
        compose.onAllNodesWithText("0.3", useUnmergedTree = true).assertCountEquals(3)
        compose.onNodeWithText("0.2", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("0.0", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `the untrained rows say zero once shown`() {
        showCard(report)

        showAll()

        compose.onAllNodesWithText("0.0", useUnmergedTree = true).assertCountEquals(4)
    }

    @Test
    fun `the card describes the muscles it shows in the same order`() {
        showCard(report)

        compose.onNodeWithContentDescription(
            "Chest: 8.0. Quadriceps: 2.0. Hamstrings: 2.0. Glutes: 2.0. Calves: 2.0. Hips: 2.0. " +
                "Upper back: 0.3. Traps and neck: 0.3. Lower back: 0.3. Shoulders: 0.2.",
        ).assertIsDisplayed()
    }

    @Test
    fun `the description lists every muscle once show all is tapped`() {
        showCard(report)

        showAll()

        compose.onNodeWithContentDescription(
            "Chest: 8.0. Quadriceps: 2.0. Hamstrings: 2.0. Glutes: 2.0. Calves: 2.0. Hips: 2.0. " +
                "Upper back: 0.3. Traps and neck: 0.3. Lower back: 0.3. Shoulders: 0.2. " +
                "Biceps: 0.0. Triceps: 0.0. Forearms: 0.0. Abs: 0.0",
        ).assertIsDisplayed()
    }

    @Test
    fun `the show all button is there while a muscle is hidden`() {
        showCard(report)

        compose.onNodeWithText(showAllButton).assertIsDisplayed()
    }

    @Test
    fun `show all hides the button, since nothing is hidden any more`() {
        showCard(report)

        showAll()

        compose.onNodeWithText(showAllButton).assertDoesNotExist()
    }

    @Test
    fun `the button is left out when every muscle is trained`() {
        // One direct set a week for each of the 14 muscles: no row is hidden.
        val everyMuscle = report.copy(
            detailWeeks = Muscle.entries.map { MuscleDetailWeek(BodyInsightSamples.weekStart(0), it, 1.0, 0.0) },
        )
        showCard(everyMuscle)

        compose.onNodeWithText(showAllButton).assertDoesNotExist()
        compose.onAllNodesWithText("0.1", useUnmergedTree = true).assertCountEquals(14)
    }

    @Test
    fun `the Muscles card is left out of the analysis when no muscle is trained`() {
        val state = BodyInsightSamples.state(report.copy(muscleWeeks = emptyList(), detailWeeks = emptyList()))

        assertFalse(AnalysisCard.MUSCLES in analysisCards(state))
    }

    @Test
    fun `the Muscles card is in the analysis when a muscle is trained`() {
        assertTrue(AnalysisCard.MUSCLES in analysisCards(BodyInsightSamples.state(report)))
    }
}

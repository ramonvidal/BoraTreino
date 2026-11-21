package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.StatsReport
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Muscles card: one bar per specific muscle, highest average first, untrained last; left out when none is trained. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h3000dp")
class MusclesCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val report = BodyInsightSamples.bodyMapReport

    private fun showCard(report: StatsReport) {
        compose.setContent { StatsCardHost { MusclesCard(report) } }
    }

    private fun showPage(report: StatsReport) {
        compose.setContent {
            StatsScreenHost { StatsContent(BodyInsightSamples.state(report), StatsActions(), Modifier.fillMaxSize()) }
        }
    }

    private fun yOf(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    @Test
    fun `the card is titled Muscles and says the sets are averages per week`() {
        showCard(report)

        compose.onNodeWithText("Muscles").assertIsDisplayed()
        compose.onNodeWithText("Average sets per week in this period (indirect sets count as half)").assertIsDisplayed()
    }

    @Test
    fun `the rows are the muscles highest average first`() {
        showCard(report)

        // Chest 8.0, the 2.0 legs, upper back 0.3, shoulders 0.2, the arms 0.0 and abs 0.0 last.
        assertTrue(yOf("Chest") < yOf("Quadriceps"))
        assertTrue(yOf("Quadriceps") < yOf("Upper back"))
        assertTrue(yOf("Upper back") < yOf("Shoulders"))
        assertTrue(yOf("Shoulders") < yOf("Biceps"))
        assertTrue(yOf("Biceps") < yOf("Abs"))
    }

    @Test
    fun `the muscle with no sets is the last row`() {
        showCard(report)

        listOf("Chest", "Quadriceps", "Upper back", "Traps and neck", "Lower back", "Shoulders", "Biceps").forEach {
            assertTrue("$it is below abs", yOf(it) < yOf("Abs"))
        }
    }

    @Test
    fun `the rows say the average with one decimal`() {
        showCard(report)

        compose.onNodeWithText("8.0").assertIsDisplayed()
        compose.onAllNodesWithText("2.0").assertCountEquals(5)
        compose.onAllNodesWithText("0.3").assertCountEquals(3)
        compose.onNodeWithText("0.2").assertIsDisplayed()
        compose.onAllNodesWithText("0.0").assertCountEquals(4)
    }

    @Test
    fun `the card describes every muscle in the same order`() {
        showCard(report)

        compose.onNodeWithContentDescription(
            "Chest: 8.0. Quadriceps: 2.0. Hamstrings: 2.0. Glutes: 2.0. Calves: 2.0. Hips: 2.0. " +
                "Upper back: 0.3. Traps and neck: 0.3. Lower back: 0.3. Shoulders: 0.2. " +
                "Biceps: 0.0. Triceps: 0.0. Forearms: 0.0. Abs: 0.0",
        ).assertIsDisplayed()
    }

    @Test
    fun `the Muscles card is left out of the page when no muscle is trained`() {
        showPage(report.copy(muscleWeeks = emptyList(), detailWeeks = emptyList()))

        compose.onNodeWithText("Muscles").assertDoesNotExist()
    }

    @Test
    fun `the Muscles card is on the page when a muscle is trained`() {
        showPage(report)

        compose.onNodeWithText("Muscles").assertIsDisplayed()
    }
}

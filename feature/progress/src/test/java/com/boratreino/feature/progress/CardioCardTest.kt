package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioBlock
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The Habit page's Cardio card: the period's totals, the averages, and nothing at all without cardio. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CardioCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val report: StatsReport = StatsSamples.full

    /** Two blocks: 135 min and 240 min (6 h 15 min), 40 km and 52.4 km (92.4 km), intensity 12, speed 24.1 km/h. */
    private val blocks = listOf(
        CardioBlock(LocalDate.of(2026, 10, 19), 135, 40_000, 2, 12.0, 24.1),
        CardioBlock(LocalDate.of(2026, 10, 26), 240, 52_400, 3, 12.0, 24.1),
    )

    private fun show(state: StatsReport) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CardioCard(state)
                }
            }
        }
    }

    @Test
    fun `the card has its title and its note`() {
        show(report.copy(cardioBlocks = blocks))

        compose.onNodeWithText("Cardio").assertExists()
        compose.onNodeWithText("Per 7-day block in this period").assertExists()
    }

    @Test
    fun `the three numbers are the period's time, distance and average intensity`() {
        show(report.copy(cardioBlocks = blocks))

        compose.onNodeWithText("6 h 15 min").assertExists()
        compose.onNodeWithText("92.4 km").assertExists()
        compose.onNodeWithText("12").assertExists()
        compose.onNodeWithText("Time").assertExists()
        compose.onNodeWithText("Distance").assertExists()
        compose.onNodeWithText("Intensity").assertExists()
    }

    @Test
    fun `the two lines give the average speed and the average intensity`() {
        show(report.copy(cardioBlocks = blocks))

        compose.onNodeWithText("Average speed 24.1 km/h").assertExists()
        compose.onNodeWithText("Average intensity 12").assertExists()
    }

    @Test
    fun `a block without distance or intensity shows dashes for them`() {
        show(report.copy(cardioBlocks = listOf(CardioBlock(LocalDate.of(2026, 10, 19), 45, 0, 1, null, null))))

        compose.onNodeWithText("45 min").assertExists()
        compose.onAllNodesWithText("-").assertCountEquals(2)
    }

    @Test
    fun `a period with no cardio draws no card`() {
        show(report.copy(cardioBlocks = emptyList()))

        compose.onNodeWithText("Cardio").assertDoesNotExist()
        compose.onNodeWithText("Per 7-day block in this period").assertDoesNotExist()
    }
}

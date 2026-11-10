package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BalanceStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The balance card: the two sides of each pair, the ratio, the footnote, and when a pair or the card is left out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h3000dp")
class BalanceCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showCard(content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost { content() } }
    }

    private fun showPage(balance: BalanceStats) {
        compose.setContent {
            StatsScreenHost {
                StatsContent(
                    BodyInsightSamples.state(StatsSamples.full.copy(balance = balance)),
                    StatsActions(),
                    Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Test
    fun `each pair shows the count of both sides and the ratio between them`() {
        showCard { BalanceCard(BodyInsightSamples.balance) }

        compose.onNodeWithText("Balance between opposites").assertIsDisplayed()
        compose.onNodeWithText("Push 15").assertIsDisplayed()
        compose.onNodeWithText("10 Pull").assertIsDisplayed()
        compose.onNodeWithText("1.5 : 1").assertIsDisplayed()
        compose.onNodeWithText("Upper body 24").assertIsDisplayed()
        compose.onNodeWithText("20 Lower body").assertIsDisplayed()
        compose.onNodeWithText("1.2 : 1").assertIsDisplayed()
        compose.onNodeWithText("Quadriceps 6").assertIsDisplayed()
        compose.onNodeWithText("3 Hamstrings").assertIsDisplayed()
        compose.onNodeWithText("2.0 : 1").assertIsDisplayed()
    }

    @Test
    fun `the neutral note is shown under the bars`() {
        showCard { BalanceCard(BodyInsightSamples.balance) }

        compose.onNodeWithText(
            "Working sets in the last 4 weeks. Many programs aim for similar push and pull volume.",
        ).assertIsDisplayed()
    }

    @Test
    fun `the unclassified sets are counted in a footnote`() {
        showCard { BalanceCard(BodyInsightSamples.balance) }

        compose.onNodeWithText("3 sets could not be classified").assertIsDisplayed()
    }

    @Test
    fun `one unclassified set is singular and none is no footnote`() {
        showCard { BalanceCard(BodyInsightSamples.balance.copy(unclassifiedSets = 1)) }
        compose.onNodeWithText("1 set could not be classified").assertIsDisplayed()
    }

    @Test
    fun `no footnote when every set is classified`() {
        showCard { BalanceCard(BodyInsightSamples.balance.copy(unclassifiedSets = 0)) }
        compose.onAllNodesWithText("could not be classified", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a pair with no sets on either side is left out`() {
        showCard { BalanceCard(BalanceStats(0, 0, 4, 2, 0, 0, 0)) }

        compose.onNodeWithText("Upper body 4").assertIsDisplayed()
        compose.onNodeWithText("2 Lower body").assertIsDisplayed()
        compose.onAllNodesWithText("Push ", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Quadriceps", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a side with no sets gives no ratio`() {
        showCard { BalanceCard(BalanceStats(4, 0, 0, 0, 0, 0, 0)) }

        compose.onNodeWithText("Push 4").assertIsDisplayed()
        compose.onNodeWithText("0 Pull").assertIsDisplayed()
        compose.onAllNodesWithText(" : 1", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the card is left out when no pair has sets`() {
        showCard { BalanceCard(BalanceStats(0, 0, 0, 0, 0, 0, 0)) }

        compose.onNodeWithText("Balance between opposites").assertDoesNotExist()
    }

    @Test
    fun `the card is left out of the page when no pair has sets`() {
        showPage(BalanceStats(0, 0, 0, 0, 0, 0, 0))

        compose.onNodeWithText("Balance between opposites").assertDoesNotExist()
    }

    @Test
    fun `the card is on the page when a pair has sets`() {
        showPage(BodyInsightSamples.balance)

        compose.onNodeWithText("Balance between opposites").assertIsDisplayed()
    }
}

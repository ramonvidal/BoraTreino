package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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

/** The Habit page's Cardio card with the bike console values: average power and total TSS lines (phase 42). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CardioCardBikeValuesTest {
    @get:Rule
    val compose = createComposeRule()

    private val report: StatsReport = StatsSamples.full

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
    fun `the card gives the period's average power, weighted by each block's minutes`() {
        // (150 W x 135 min + 170 W x 240 min) / 375 min = 162.8 W
        val blocks = listOf(
            CardioBlock(LocalDate.of(2026, 10, 19), 135, 40_000, 2, 12.0, 24.1, powerAvg = 150.0),
            CardioBlock(LocalDate.of(2026, 10, 26), 240, 52_400, 3, 12.0, 24.1, powerAvg = 170.0),
        )

        show(report.copy(cardioBlocks = blocks))

        compose.onNodeWithText("Average power 163 W").assertExists()
    }

    @Test
    fun `the card gives the period's total TSS`() {
        val blocks = listOf(
            CardioBlock(LocalDate.of(2026, 10, 19), 135, 40_000, 2, 12.0, 24.1, tssTotal = 20),
            CardioBlock(LocalDate.of(2026, 10, 26), 240, 52_400, 3, 12.0, 24.1, tssTotal = 28),
        )

        show(report.copy(cardioBlocks = blocks))

        compose.onNodeWithText("Total TSS 48").assertExists()
    }

    @Test
    fun `the power and TSS lines are left out when no block has the values`() {
        val blocks = listOf(CardioBlock(LocalDate.of(2026, 10, 19), 135, 40_000, 2, 12.0, 24.1))

        show(report.copy(cardioBlocks = blocks))

        compose.onNodeWithText("Average speed 24.1 km/h").assertExists()
        compose.onNodeWithText("Total TSS 48").assertDoesNotExist()
        compose.onNodeWithText("Average power 163 W").assertDoesNotExist()
    }
}

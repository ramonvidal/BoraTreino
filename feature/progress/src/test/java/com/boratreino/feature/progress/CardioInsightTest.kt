package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioInsight
import com.boratreino.core.model.StatsReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Cardio card's whole-history insight lines: efficiency, the speed trend and the efficiency trend. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CardioInsightTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(report: StatsReport) {
        compose.setContent { StatsCardHost { CardioCard(report) } }
    }

    private fun report(insight: CardioInsight?) =
        StatsSamples.full.copy(cardioBlocks = Phase46Samples.cardioBlocks, cardioInsight = insight)

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    @Test
    fun `the insight shows the session count, the efficiency with its best and both trends`() {
        show(report(Phase46Samples.cardioInsight))

        shown("Whole history, 12 sessions")
        shown("Efficiency: 1.85 km/h per level of intensity (best 2.1)")
        shown("Speed: Rising 0.25 km/h per week")
        shown("Efficiency: No clear change")
        shown("Efficiency is your speed divided by the intensity level: a higher number means more speed for the same effort.")
    }

    @Test
    fun `without a best efficiency the efficiency line has no best part`() {
        show(report(Phase46Samples.cardioInsight.copy(efficiencyBest = null)))

        shown("Efficiency: 1.85 km/h per level of intensity")
        compose.onNodeWithText("Efficiency: 1.85 km/h per level of intensity (best 2.1)").assertDoesNotExist()
    }

    @Test
    fun `without any efficiency the efficiency lines are left out and the speed trend stays`() {
        show(report(Phase46Samples.cardioInsight.copy(efficiencyLatest = null, efficiencyBest = null)))

        shown("Speed: Rising 0.25 km/h per week")
        compose.onNodeWithText("Efficiency:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `without an insight the insight lines are not drawn and the card still is`() {
        show(report(null))

        shown("Cardio")
        compose.onNodeWithText("Whole history", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Speed:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Efficiency:", substring = true).assertDoesNotExist()
    }
}

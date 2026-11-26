package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The waist to hip card: the latest ratio, its trend sentence, and nothing when there is no ratio. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ProportionsCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today

    private fun show(waistHip: List<Pair<LocalDate, Double>>) {
        compose.setContent { StatsCardHost { ProportionsCard(waistHip = waistHip, today = today) } }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    @Test
    fun `the card shows its title, its note and the latest ratio with two decimals`() {
        show(Phase46Samples.waistHipWeekly)

        shown("Waist to hip ratio")
        shown("Waist divided by hips, for days with both measurements")
        shown("0.86")
    }

    @Test
    fun `a falling ratio says how much it falls each week`() {
        show(Phase46Samples.waistHipWeekly)

        shown("Falling 0.01 per week")
    }

    @Test
    fun `two ratios are not enough for a trend`() {
        show(Phase46Samples.waistHipWeekly.take(2))

        shown("0.89")
        shown("Not enough data: needs 3 measurements")
    }

    @Test
    fun `no ratio draws no card`() {
        show(emptyList())

        compose.onNodeWithText("Waist to hip ratio").assertDoesNotExist()
    }
}

package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.EnergyHistory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Daily energy card: its averages, what training adds, the chart's description, and what is left out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class EnergyCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val history = StatsSamples.energyHistory

    private fun show(history: EnergyHistory) {
        compose.setContent { StatsCardHost { EnergyCard(history) } }
    }

    @Test
    fun `the card has its title and the note that it is an estimate`() {
        show(history)

        compose.onNodeWithText("Daily energy").assertExists()
        compose.onNodeWithText("Estimate from your body data (Settings) and your logged training; not advice.").assertExists()
    }

    @Test
    fun `the 7 and 28 day averages are about kcal a day`() {
        show(history)

        compose.onNodeWithText("Last 7 days: about 2,200 kcal a day").assertExists()
        compose.onNodeWithText("Last 28 days: about 2,130 kcal a day").assertExists()
    }

    @Test
    fun `without a 28 day average that line is left out`() {
        show(history.copy(last28AverageKcal = null))

        compose.onNodeWithText("Last 7 days: about 2,200 kcal a day").assertExists()
        compose.onNodeWithText("Last 28 days: about 2,130 kcal a day").assertDoesNotExist()
    }

    @Test
    fun `without a 7 day average that line is left out`() {
        show(history.copy(last7AverageKcal = null))

        compose.onNodeWithText("Last 7 days: about 2,200 kcal a day").assertDoesNotExist()
        compose.onNodeWithText("Last 28 days: about 2,130 kcal a day").assertExists()
    }

    @Test
    fun `the training line says what training adds on the days trained`() {
        show(history)

        compose.onNodeWithText("Training adds about 240 kcal on the days you train").assertExists()
    }

    @Test
    fun `without a training average the training line is left out`() {
        show(history.copy(averageBurnKcal = null, trainedDays = 0))

        compose.onNodeWithText("Training adds about 240 kcal on the days you train").assertDoesNotExist()
    }

    @Test
    fun `the chart describes its days and how many were training days`() {
        show(history)

        compose.onNodeWithContentDescription("Daily energy estimate over 28 days, 3 of them training days").assertExists()
    }

    @Test
    fun `without any day the card is not shown`() {
        show(EnergyHistory(points = emptyList(), last7AverageKcal = null, last28AverageKcal = null, averageBurnKcal = null, trainedDays = 0))

        compose.onNodeWithText("Daily energy").assertDoesNotExist()
    }
}

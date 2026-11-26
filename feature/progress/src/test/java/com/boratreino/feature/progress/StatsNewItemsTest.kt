package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.AcwrStatus
import com.boratreino.core.model.StatsPage
import com.boratreino.core.model.StatsReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Which new Stats cards each page shows: when the report has their data, and not when it does not. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h20000dp")
class StatsNewItemsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showPage(report: StatsReport, page: StatsPage) {
        compose.setContent {
            StatsScreenHost {
                StatsContent(
                    state = StatsSamples.state(report),
                    actions = StatsActions(),
                    modifier = Modifier.fillMaxSize(),
                    page = page,
                )
            }
        }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun notShown(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    private val withStrengthData = StatsSamples.full.copy(
        machineTrends = Phase46Samples.machineTrends,
        muscleVolumes = Phase46Samples.volumes,
    )

    private val withHabitData = StatsSamples.full.copy(
        workload = Phase46Samples.workload(AcwrStatus.OPTIMAL),
        patterns = Phase46Samples.patterns(),
        cardioBlocks = Phase46Samples.cardioBlocks,
        cardioInsight = Phase46Samples.cardioInsight,
    )

    @Test
    fun `the strength page shows the trend summary and the muscle volume when the report has them`() {
        showPage(withStrengthData, StatsPage.STRENGTH)

        shown("Trends by exercise")
        shown("Weekly sets per muscle")
    }

    @Test
    fun `the strength page leaves out the trend summary and the muscle volume when the report has none`() {
        showPage(StatsSamples.full, StatsPage.STRENGTH)

        notShown("Trends by exercise")
        notShown("Weekly sets per muscle")
    }

    @Test
    fun `the habit page shows the training load and the patterns when the report has them`() {
        showPage(withHabitData, StatsPage.HABIT)

        shown("Training load")
        shown("When you lift best")
    }

    @Test
    fun `the habit page leaves out the training load and the patterns when the report has none`() {
        showPage(StatsSamples.full, StatsPage.HABIT)

        notShown("Training load")
        notShown("When you lift best")
    }

    @Test
    fun `the cardio card shows its insight when the report has one`() {
        showPage(withHabitData, StatsPage.HABIT)

        shown("Whole history, 12 sessions")
    }

    @Test
    fun `the cardio card shows no insight when the report has none`() {
        showPage(StatsSamples.full.copy(cardioBlocks = Phase46Samples.cardioBlocks), StatsPage.HABIT)

        shown("Cardio")
        notShown("Whole history, 12 sessions")
    }

    @Test
    fun `the body page shows the waist to hip ratio when the report has it`() {
        showPage(StatsSamples.full.copy(waistHip = Phase46Samples.waistHipWeekly), StatsPage.BODY)

        shown("Waist to hip ratio")
    }

    @Test
    fun `the body page leaves out the waist to hip ratio when the report has none`() {
        showPage(StatsSamples.full, StatsPage.BODY)

        notShown("Waist to hip ratio")
    }
}

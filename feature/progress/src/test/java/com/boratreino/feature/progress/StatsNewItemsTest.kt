package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.AcwrStatus
import com.boratreino.core.model.StatsReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Which new Stats cards each section shows: when the report has their data, and not when it does not. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h20000dp")
class StatsNewItemsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showSection(report: StatsReport, section: AnalysisSection) {
        compose.setContent { AnalysisHost(StatsSamples.state(report), section = section) }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().assertIsDisplayed()

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
    fun `the highlights show the trends by exercise when the report has them`() {
        showSection(withStrengthData, AnalysisSection.HIGHLIGHTS)

        shown("Trends by exercise")
    }

    @Test
    fun `the highlights leave out the trends by exercise when the report has none`() {
        showSection(StatsSamples.full, AnalysisSection.HIGHLIGHTS)

        notShown("Trends by exercise")
    }

    @Test
    fun `the muscles show the weekly sets per muscle when the report has them`() {
        showSection(withStrengthData, AnalysisSection.MUSCLES)

        shown("Weekly sets per muscle")
    }

    @Test
    fun `the muscles leave out the weekly sets per muscle when the report has none`() {
        showSection(StatsSamples.full, AnalysisSection.MUSCLES)

        notShown("Weekly sets per muscle")
    }

    @Test
    fun `the highlights show the training load and the patterns when the report has them`() {
        showSection(withHabitData, AnalysisSection.HIGHLIGHTS)

        shown("Recent vs usual load")
        shown("When you lift best")
    }

    @Test
    fun `the highlights leave out the training load and the patterns when the report has none`() {
        showSection(StatsSamples.full, AnalysisSection.HIGHLIGHTS)

        notShown("Recent vs usual load")
        notShown("When you lift best")
    }

    @Test
    fun `the habit section shows the cardio insight when the report has one`() {
        showSection(withHabitData, AnalysisSection.HABIT)

        shown("Whole history, 12 sessions")
    }

    @Test
    fun `the habit section shows no cardio insight when the report has none`() {
        showSection(StatsSamples.full.copy(cardioBlocks = Phase46Samples.cardioBlocks), AnalysisSection.HABIT)

        shown("Cardio")
        notShown("Whole history, 12 sessions")
    }

    @Test
    fun `the body section shows the waist to hip ratio when the report has it`() {
        showSection(StatsSamples.full.copy(waistHip = Phase46Samples.waistHipWeekly), AnalysisSection.BODY)

        shown("Waist to hip ratio")
    }

    @Test
    fun `the body section leaves out the waist to hip ratio when the report has none`() {
        showSection(StatsSamples.full, AnalysisSection.BODY)

        notShown("Waist to hip ratio")
    }
}

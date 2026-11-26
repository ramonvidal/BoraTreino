package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.AcwrStatus
import com.boratreino.core.model.WorkloadReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The "Recent vs usual load" card: the acute and chronic sets and the ratio, its status and meaning, the baseline while it
 * builds, the exploratory tag with its hint, and no monotony or strain.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class WorkloadCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(workload: WorkloadReport?) {
        compose.setContent { StatsCardHost { WorkloadCard(workload = workload) } }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().assertIsDisplayed()

    @Test
    fun `the card shows the acute and chronic sets and the ratio`() {
        show(Phase46Samples.workload(AcwrStatus.OPTIMAL))

        shown("Recent vs usual load")
        shown("Last 7 days")
        shown("12 sets")
        shown("Weekly average over 28 days")
        shown("10.5 sets")
        shown("Load ratio")
        shown("1.14")
    }

    @Test
    fun `an optimal load is labelled optimal with its meaning`() {
        show(Phase46Samples.workload(AcwrStatus.OPTIMAL))

        shown("Optimal")
        shown("This week is in line with your recent load.")
    }

    @Test
    fun `a low load is labelled low with its meaning`() {
        show(Phase46Samples.workload(AcwrStatus.LOW, acwr = 0.6))

        shown("Low")
        shown("This week is well below your usual load.")
    }

    @Test
    fun `a high load is labelled high with its meaning`() {
        show(Phase46Samples.workload(AcwrStatus.HIGH, acwr = 1.4))

        shown("High")
        shown("This week is clearly above your usual load. Watch your recovery.")
    }

    @Test
    fun `a very high load is labelled very high with its meaning`() {
        show(Phase46Samples.workload(AcwrStatus.VERY_HIGH, acwr = 1.8))

        shown("Very high")
        shown("A big jump over your usual load. An easier week may help.")
    }

    @Test
    fun `while the baseline builds the card counts the days of history out of 28`() {
        show(Phase46Samples.workload(AcwrStatus.BUILDING_BASELINE, acwr = null, historyDays = 10))

        shown("Building your baseline: 10 of 28 days")
        shown("Not enough history yet for a reliable ratio.")
    }

    @Test
    fun `the card is marked exploratory with its hint`() {
        show(Phase46Samples.workload(AcwrStatus.OPTIMAL))

        shown("Exploratory")
        shown("Exploratory: compares your last 7 days with your average week over 28 days. A rough guide, not a rule.")
    }

    @Test
    fun `there is no monotony or strain row`() {
        show(Phase46Samples.workload(AcwrStatus.OPTIMAL))

        compose.onNodeWithText("Monotony", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Strain", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a card with no load is not drawn`() {
        show(null)

        compose.onNodeWithText("Recent vs usual load").assertDoesNotExist()
    }

    @Test
    fun `a report without any load status is not drawn`() {
        show(Phase46Samples.workload(AcwrStatus.NO_DATA, acwr = null, historyDays = 0))

        compose.onNodeWithText("Recent vs usual load").assertDoesNotExist()
    }
}

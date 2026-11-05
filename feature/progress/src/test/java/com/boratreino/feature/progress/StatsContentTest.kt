package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.StatsPeriod
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the Stats page says and does, checked on the text it shows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h3000dp")
class StatsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val full = StatsSamples.full

    private fun showPage(state: StatsUiState = StatsSamples.state(), actions: StatsActions = StatsActions()) {
        compose.setContent {
            StatsScreenHost { StatsContent(state = state, actions = actions, modifier = Modifier.fillMaxSize()) }
        }
    }

    private fun showCard(content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost { content() } }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    // The page

    @Test
    fun `the period chips are shown`() {
        showPage()
        shown("4 weeks")
        shown("3 months")
        shown("1 year")
        shown("All")
    }

    @Test
    fun `the cards come with their titles`() {
        showPage()
        listOf(
            "Summary",
            "Weekly volume",
            "Hard sets per muscle group",
            "Days since each muscle group was trained",
            "Strength trend",
            "Records",
            "Rep ranges",
            "Session length",
        ).forEach { shown(it) }
    }

    @Test
    fun `the summary has the sessions a week, the consistency and the best week`() {
        showPage()
        shown("2.8 sessions a week")
        shown("Met your goal in 9 of 12 weeks")
        shown("Best week: 4 training days, week of 12 Oct")
    }

    @Test
    fun `the overall strength change is in the summary`() {
        showPage()
        shown("Strength: +6.4 % in this period")
    }

    @Test
    fun `without an overall strength change the summary leaves the line out`() {
        showPage(StatsSamples.state(full.copy(overallStrengthPercent = null)))
        compose.onNodeWithText("Strength: +6.4 % in this period").assertDoesNotExist()
    }

    @Test
    fun `the volume legend shows tonnes with one decimal and kilograms below a tonne`() {
        showPage()
        shown("12.4 t")
        shown("8.2 t")
        shown("850 kg")
    }

    @Test
    fun `a group that was never trained says Never`() {
        showPage()
        shown("Never")
    }

    @Test
    fun `the recency rows say today and days ago`() {
        showPage()
        shown("Today")
        shown("1 day ago")
        shown("2 days ago")
        shown("30 days ago")
    }

    @Test
    fun `the hard sets note is neutral`() {
        showPage()
        shown("Many programs use roughly 10 to 20 hard sets per muscle group per week.")
    }

    @Test
    fun `the strength list shows the estimate and the change`() {
        showPage()
        shown("95 kg → 100 kg est. 1RM")
        shown("+5.3 %")
        shown("+10.0 %")
    }

    @Test
    fun `a plateau is flagged under the strength list`() {
        showPage()
        shown("Lat pulldown: no progress in 5 sessions")
    }

    @Test
    fun `the records show what was beaten and when`() {
        showPage()
        shown("Heaviest: 100 kg")
        shown("Most reps: 12 at 40 kg")
        shown("30 Oct")
    }

    @Test
    fun `a machine in both lists appears twice`() {
        showPage()
        compose.onAllNodesWithText("Leg press").assertCountEquals(2)
    }

    @Test
    fun `the rep ranges show their percentages`() {
        // The card alone: on the full page it sits far down, and this test is about its texts.
        showCard { RepRangesCard(StatsSamples.full) }
        // The bar and its legend are one accessibility element: a screen reader gets one sentence.
        compose.onNodeWithContentDescription(
            "1 to 5 reps: 10 %. 6 to 8 reps: 30 %. 9 to 12 reps: 45 %. 13 or more reps: 15 %",
        ).assertIsDisplayed()
        shown("Rep ranges")
    }

    @Test
    fun `the session length shows the average and the time between sets`() {
        showPage()
        shown("Average session: 62 min")
        shown("About 3.1 min between sets")
    }

    // Empty and special periods

    @Test
    fun `an empty period shows the friendly card and nothing else`() {
        showPage(StatsSamples.state(StatsSamples.empty))
        shown("4 weeks")
        shown("No training in this period yet")
        compose.onNodeWithText("Summary").assertDoesNotExist()
        compose.onNodeWithText("Records").assertDoesNotExist()
    }

    @Test
    fun `a cardio-only period has a summary but no strength cards`() {
        showPage(StatsSamples.state(StatsSamples.cardioOnly))
        shown("Summary")
        shown("2 sessions a week")
        compose.onNodeWithText("No training in this period yet").assertDoesNotExist()
        compose.onNodeWithText("Weekly volume").assertDoesNotExist()
        compose.onNodeWithText("Hard sets per muscle group").assertDoesNotExist()
        compose.onNodeWithText("Strength trend").assertDoesNotExist()
        compose.onNodeWithText("Rep ranges").assertDoesNotExist()
        shown("Average session: 40 min")
    }

    @Test
    fun `a period without time data leaves the session length out`() {
        showPage(StatsSamples.state(full.copy(times = full.times.copy(averageMinutes = null, averageMinutesBetweenSets = null))))
        compose.onNodeWithText("Session length").assertDoesNotExist()
    }

    @Test
    fun `before the first report only the period chips are drawn`() {
        showPage(StatsUiState())
        shown("3 months")
        compose.onNodeWithText("Summary").assertDoesNotExist()
        compose.onNodeWithText("No training in this period yet").assertDoesNotExist()
    }

    // Taps

    @Test
    fun `tapping a period chip asks for that period`() {
        val asked = mutableListOf<StatsPeriod>()
        showPage(actions = StatsActions(onPeriod = { asked += it }))

        compose.onNodeWithText("1 year").performClick()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("4 weeks").performClick()

        assertEquals(listOf(StatsPeriod.ONE_YEAR, StatsPeriod.ALL, StatsPeriod.FOUR_WEEKS), asked)
    }

    @Test
    fun `the chips alone report taps`() {
        var asked: StatsPeriod? = null
        showCard { PeriodChips(StatsPeriod.THREE_MONTHS, { asked = it }) }
        compose.onNodeWithText("1 year").performClick()
        assertEquals(StatsPeriod.ONE_YEAR, asked)
    }

    @Test
    fun `tapping a machine in the strength list opens it`() {
        var opened: Long? = null
        showCard { StrengthCard(full, { opened = it }) }
        compose.onNodeWithText("Bench press").performClick()
        assertEquals(2L, opened)
    }

    @Test
    fun `tapping a machine in the strength list on the page opens it`() {
        var opened: Long? = null
        showPage(actions = StatsActions(onOpenMachine = { opened = it }))
        compose.onAllNodesWithText("Lat pulldown")[0].performClick()
        assertEquals(3L, opened)
    }

    @Test
    fun `tapping a machine in the records list opens it`() {
        var opened: Long? = null
        showCard { RecordsCard(full, { opened = it }) }
        compose.onNodeWithText("Shoulder press").performClick()
        assertEquals(4L, opened)
    }

    // Cards on their own

    @Test
    fun `at most ten records are listed`() {
        val many = full.copy(records = List(12) { full.records[it % full.records.size] })
        showCard { RecordsCard(many, {}) }
        compose.onAllNodesWithText("Heaviest: 100 kg").assertCountEquals(2)
        compose.onAllNodesWithText("Most reps: 12 at 40 kg").assertCountEquals(2)
    }

    @Test
    fun `a single plateau session is singular`() {
        val one = full.copy(plateaus = listOf(full.plateaus.first().copy(sessionsWithoutImprovement = 1)))
        showCard { StrengthCard(one, {}) }
        shown("Lat pulldown: no progress in 1 session")
    }

    @Test
    fun `the empty card says there is no training yet`() {
        showCard { StatsEmptyCard() }
        shown("No training in this period yet")
    }
}

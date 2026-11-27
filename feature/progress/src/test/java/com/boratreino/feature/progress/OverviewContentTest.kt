package com.boratreino.feature.progress

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BodySnapshot
import com.boratreino.core.model.StrengthSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Overview tab: what it says and where its taps go, checked on the text it shows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h6000dp")
class OverviewContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = OverviewSamples.today

    private fun show(state: StatsUiState, actions: StatsActions = StatsActions()) {
        compose.setContent { OverviewHost(state = state, actions = actions) }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun notShown(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    // What matters now

    @Test
    fun `the findings are under What matters now, in capitals`() {
        show(OverviewSamples.state())

        shown("WHAT MATTERS NOW")
    }

    @Test
    fun `the low volume finding says the muscle and its sets a week, with the usual`() {
        show(OverviewSamples.state())

        shown("Shoulders: only 4 sets a week")
        shown("The usual is 10 or more.")
    }

    @Test
    fun `the stalled exercise says how long it is stuck and suggests more reps`() {
        show(OverviewSamples.state())

        shown("Lateral raise stuck at 12 kg for 4 trainings")
        shown("Try 14 reps before going up in weight.")
    }

    @Test
    fun `the record names the exercise and weight, and the other records of the week`() {
        show(OverviewSamples.state())

        shown("Record: Calf raise 60 kg")
        shown("${formatWeekdayDate(today.minusDays(1))}. 10 more records in the last 7 days.")
    }

    @Test
    fun `a frequency drop says the new average and the usual one with the goal`() {
        show(OverviewSamples.state(findings = listOf(OverviewSamples.frequencyDrop)))

        shown("Training fell to 1.5 days a week")
        shown("It was 3.5 in the 4 weeks before. Goal: 4.")
    }

    @Test
    fun `a falling exercise says its estimate went down`() {
        show(OverviewSamples.state(findings = listOf(OverviewSamples.stalledFalling)))

        shown("Leg extension down for 3 trainings")
        shown("Estimated 1RM 60 → 54 kg.")
    }

    @Test
    fun `at most three findings are shown`() {
        show(OverviewSamples.state(findings = listOf(OverviewSamples.frequencyDrop) + OverviewSamples.ownerLike))

        shown("Training fell to 1.5 days a week")
        shown("Shoulders: only 4 sets a week")
        shown("Lateral raise stuck at 12 kg for 4 trainings")
        notShown("Record: Calf raise 60 kg")
    }

    @Test
    fun `with no findings the section says nothing needs attention`() {
        show(OverviewSamples.state(findings = emptyList()))

        shown("WHAT MATTERS NOW")
        shown("Nothing needs your attention right now.")
    }

    // Taps on the findings

    @Test
    fun `tapping the low volume finding opens the hard sets card of Analysis`() {
        var opened: AnalysisCard? = null
        show(OverviewSamples.state(), StatsActions(onOpenAnalysis = { opened = it }))

        compose.onNodeWithText("Shoulders: only 4 sets a week").performClick()

        assertEquals(AnalysisCard.HARD_SETS, opened)
    }

    @Test
    fun `tapping the record finding opens the records card of Analysis`() {
        var opened: AnalysisCard? = null
        show(OverviewSamples.state(), StatsActions(onOpenAnalysis = { opened = it }))

        compose.onNodeWithText("Record: Calf raise 60 kg").performClick()

        assertEquals(AnalysisCard.RECORDS, opened)
    }

    @Test
    fun `tapping the stalled finding opens its exercise`() {
        var opened: Long? = null
        show(OverviewSamples.state(), StatsActions(onOpenMachine = { opened = it }))

        compose.onNodeWithText("Lateral raise stuck at 12 kg for 4 trainings").performClick()

        assertEquals(7L, opened)
    }

    // Frequency

    @Test
    fun `the frequency section shows the week, its goal, the streak and the goal`() {
        show(OverviewSamples.state())

        shown("Frequency")
        shown("of 4 trainings")
        shown("this week")
        shown("2 weeks in a row on goal")
        shown("goal: 4 a week")
    }

    @Test
    fun `the frequency bars are described day by day with their day and month`() {
        show(OverviewSamples.state())

        compose.onNodeWithContentDescription(
            "Training days in the last 4 weeks: 12/10: 3, 19/10: 4, 26/10: 1, 1/11: 2",
        ).assertIsDisplayed()
    }

    @Test
    fun `the Calendar link opens the calendar`() {
        var calendar = false
        show(OverviewSamples.state(), StatsActions(onOpenCalendar = { calendar = true }))

        compose.onNodeWithText("Calendar").performClick()

        assertEquals(true, calendar)
    }

    @Test
    fun `without a frequency summary the section is left out`() {
        show(OverviewSamples.state(frequency = null))

        notShown("Frequency")
    }

    // Strength

    @Test
    fun `the strength section has its period and its three groups`() {
        show(OverviewSamples.state())

        shown("Strength")
        shown("Estimated 1RM, 4 weeks")
        shown("GOING UP")
        shown("FLAT OR GOING DOWN")
        shown("CHECK THIS VALUE")
    }

    @Test
    fun `the rising row shows its estimate and a plus percent, the flat row its top set and zero percent`() {
        show(OverviewSamples.state())

        shown("Leg press")
        shown("95 → 100 kg")
        shown("+5%")
        shown("Lat pulldown")
        shown("70 kg × 10 · 2 trainings")
        shown("0%")
    }

    @Test
    fun `a flagged jump is under Check this value and is never a riser`() {
        show(OverviewSamples.state())

        shown("Check this value")
        shown("Biceps curl")
        shown("20 → 35 kg in 3 days")
        compose.onAllNodesWithText("Biceps curl").assertCountEquals(1)
    }

    @Test
    fun `tapping a rising row opens its exercise`() {
        var opened: Long? = null
        show(OverviewSamples.state(), StatsActions(onOpenMachine = { opened = it }))

        compose.onNodeWithText("Leg press").performClick()

        assertEquals(1L, opened)
    }

    @Test
    fun `tapping a flagged jump opens its exercise`() {
        var opened: Long? = null
        show(OverviewSamples.state(), StatsActions(onOpenMachine = { opened = it }))

        compose.onNodeWithText("Biceps curl").performClick()

        assertEquals(5L, opened)
    }

    @Test
    fun `without any training to compare the strength section says so`() {
        show(OverviewSamples.state(strength = StrengthSummary.EMPTY))

        shown("Train an exercise in two different weeks of the period to compare.")
    }

    // Body

    @Test
    fun `the body tile shows the value and the note gives the date of the measurement`() {
        show(OverviewSamples.state())

        shown("Body")
        shown("82.5")
        shown("In cm, measured on 31 Oct. Measure again to see the trend.")
    }

    @Test
    fun `tapping the body tile opens the measurements card of Analysis`() {
        var opened: AnalysisCard? = null
        show(OverviewSamples.state(), StatsActions(onOpenAnalysis = { opened = it }))

        compose.onNodeWithText("82.5").performClick()

        assertEquals(AnalysisCard.MEASUREMENTS, opened)
    }

    @Test
    fun `without any measurement the body section asks for one`() {
        show(OverviewSamples.state(body = BodySnapshot(emptyList(), null, false), measurements = emptyList()))

        shown("Log your waist, hips and more to follow your body.")
    }

    // Habit

    @Test
    fun `the habit section shows the cardio, the session length and the time between sets since its start`() {
        show(OverviewSamples.state())

        shown("Habit")
        shown("since 6 Oct")
        shown("3 × cardio")
        shown("60 min a training")
        shown("3 min between sets")
    }

    @Test
    fun `without habit data the habit section is left out`() {
        show(OverviewSamples.state(habit = null))

        notShown("Habit")
    }

    // Analysis row

    @Test
    fun `the analysis row opens Analysis at the top, with no card`() {
        val opened = mutableListOf<AnalysisCard?>()
        show(OverviewSamples.state(), StatsActions(onOpenAnalysis = { opened += it }))

        shown("Detailed analysis")
        shown("Trend, recent load, patterns, sets per muscle, proportions")
        compose.onNodeWithText("Detailed analysis").performClick()

        assertEquals(listOf<AnalysisCard?>(null), opened)
    }

    // Empty periods and the blank page

    @Test
    fun `an empty period shows the empty card with Frequency, Body and the analysis row only`() {
        show(OverviewSamples.state(report = StatsSamples.empty, findings = OverviewSamples.ownerLike))

        shown("No training in this period yet")
        shown("Frequency")
        shown("Body")
        shown("Detailed analysis")
        notShown("WHAT MATTERS NOW")
        notShown("Estimated 1RM, 4 weeks")
        notShown("Habit")
    }

    @Test
    fun `before the first report the Overview draws nothing`() {
        show(StatsUiState())

        notShown("WHAT MATTERS NOW")
        notShown("Detailed analysis")
    }
}

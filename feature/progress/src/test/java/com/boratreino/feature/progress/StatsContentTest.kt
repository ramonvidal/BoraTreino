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
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.MuscleRecency
import com.boratreino.core.model.MuscleWeek
import com.boratreino.core.model.StatsPage
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.StrengthTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the Stats page says and does, checked on the text it shows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h6000dp")
class StatsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val full = StatsSamples.full
    private val today = StatsSamples.today

    /** Shows one page of the stats (the Body page by default). */
    private fun showPage(
        state: StatsUiState = StatsSamples.state(),
        actions: StatsActions = StatsActions(),
        page: StatsPage = StatsPage.BODY,
    ) {
        compose.setContent {
            StatsScreenHost {
                StatsContent(state = state, actions = actions, modifier = Modifier.fillMaxSize(), page = page)
            }
        }
    }

    private fun showCard(content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost { content() } }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun notShown(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    private fun yOf(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    /** The report with no highlight at all: no strength gain, no group trained a lot, nothing behind, no goal streak. */
    private fun withoutHighlights(report: StatsReport = full): StatsReport = report.copy(
        strength = report.strength.filter { it.changePercent <= 0.0 },
        muscleWeeks = emptyList(),
        recency = MuscleGroup.entries.map { MuscleRecency(it, null, null) },
        consistency = report.consistency.copy(weeksMetGoal = 0),
    )

    private fun trend(machineName: String, id: Long, percent: Double) = StrengthTrend(
        machine = ProgressSamples.machine(id, machineName, MuscleGroup.LEGS),
        before = 100.0,
        after = 100.0 + percent,
        changeKg = percent,
        changePercent = percent,
    )

    // The page

    @Test
    fun `the body page is shown by default`() {
        showPage()

        shown("Muscle groups")
        notShown("Summary")
        notShown("Strength trend")
        notShown("Session length")
    }

    @Test
    fun `the muscle groups card comes before the body map`() {
        showPage()

        assertTrue(yOf("Muscle groups") < yOf("Body map"))
    }

    @Test
    fun `the muscle groups card is left out of an empty period`() {
        showPage(StatsSamples.state(StatsSamples.empty))

        notShown("Muscle groups")
    }

    @Test
    fun `the body page cards come with their titles`() {
        showPage()
        shown("Muscle groups")
        shown("Body map")
        shown("Hard sets per muscle group")
    }

    @Test
    fun `the strength page cards come with their titles`() {
        showPage(page = StatsPage.STRENGTH)
        shown("Strength trend")
        shown("Records")
        shown("Rep ranges")
    }

    @Test
    fun `the habit page cards come with their titles`() {
        showPage(page = StatsPage.HABIT)
        shown("Summary")
        shown("Weekly volume")
        shown("Days since each muscle group was trained")
        shown("Session length")
    }

    @Test
    fun `the page passed is the one shown`() {
        showPage(page = StatsPage.STRENGTH)

        shown("Strength trend")
        notShown("Muscle groups")
    }

    @Test
    fun `the summary has the sessions a week, the consistency and the best week`() {
        showPage(page = StatsPage.HABIT)
        shown("2.8 sessions a week")
        shown("Met your goal in 9 of 13 weeks")
        shown("Best week: 4 training days, week of 13 Oct")
    }

    @Test
    fun `the overall strength change is in the summary`() {
        showPage(page = StatsPage.HABIT)
        shown("Strength: +6.4 % in this period")
    }

    @Test
    fun `without an overall strength change the summary leaves the line out`() {
        showPage(StatsSamples.state(full.copy(overallStrengthPercent = null)), page = StatsPage.HABIT)
        notShown("Strength: +6.4 % in this period")
    }

    @Test
    fun `the volume chart description says one week in the singular`() {
        showPage(StatsSamples.state(StatsSamples.oneWeek), page = StatsPage.HABIT)

        compose.onNodeWithContentDescription("Weekly volume over 1 week: 7.8 t in total, best week 7.8 t").assertIsDisplayed()
    }

    @Test
    fun `the volume chart description says the weeks in the plural`() {
        showPage(page = StatsPage.HABIT)

        compose.onNodeWithContentDescription("Weekly volume over 13 weeks:", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Weekly volume over 1 week:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the volume legend shows tonnes with one decimal and kilograms below a tonne`() {
        showPage(page = StatsPage.HABIT)
        shown("12.4 t")
        shown("8.2 t")
        shown("850 kg")
    }

    @Test
    fun `a group that was never trained says Never`() {
        showPage(page = StatsPage.HABIT)
        shown("Never")
    }

    @Test
    fun `the recency rows say today and days ago`() {
        showPage(page = StatsPage.HABIT)
        // Two Today labels: the legs row, and the last block of the weekly volume chart.
        compose.onAllNodesWithText("Today").assertCountEquals(2)
        shown("1 day ago")
        shown("2 days ago")
        shown("30 days ago")
    }

    @Test
    fun `the weekly volume chart names its first block by date and its last block Today, never This week`() {
        showPage(page = StatsPage.HABIT)

        shown("4 Aug")
        compose.onAllNodesWithText("Today").assertCountEquals(2)
        notShown("This week")
    }

    @Test
    fun `the hard sets note is neutral`() {
        showPage()
        shown("Many programs use roughly 10 to 20 hard sets per muscle group per week.")
    }

    @Test
    fun `the strength list shows the estimate and the change`() {
        showPage(page = StatsPage.STRENGTH)
        shown("95 kg → 100 kg est. 1RM")
        shown("+5.3 %")
        shown("+10.0 %")
    }

    @Test
    fun `a plateau is flagged under the strength list`() {
        showPage(page = StatsPage.STRENGTH)
        shown("Lat pulldown: no progress in 5 sessions")
    }

    @Test
    fun `the records show what was beaten and when`() {
        showPage(page = StatsPage.STRENGTH)
        shown("Heaviest: 100 kg")
        shown("Most reps: 12 at 40 kg")
        shown("30 Oct")
    }

    @Test
    fun `a machine in both lists appears twice`() {
        showPage(page = StatsPage.STRENGTH)
        compose.onAllNodesWithText("Leg press").assertCountEquals(2)
    }

    @Test
    fun `the rep ranges show their percentages`() {
        // The card alone: on the page it sits far down, and this test is about its texts.
        showCard { RepRangesCard(StatsSamples.full) }
        // The bar and its legend are one accessibility element: a screen reader gets one sentence.
        compose.onNodeWithContentDescription(
            "1 to 5 reps: 10 %. 6 to 8 reps: 30 %. 9 to 12 reps: 45 %. 13 or more reps: 15 %",
        ).assertIsDisplayed()
        shown("Rep ranges")
    }

    @Test
    fun `the session length shows the average and the time between sets`() {
        showPage(page = StatsPage.HABIT)
        shown("Average session: 62 min")
        shown("About 3.1 min between sets")
    }

    // Highlights

    @Test
    fun `the highlights are the first item of the body page`() {
        showPage()

        assertTrue(yOf("Bench press +10.0 % in the period") < yOf("Muscle groups"))
    }

    @Test
    fun `the highlights are left out of the strength page`() {
        showPage(page = StatsPage.STRENGTH)

        notShown("Bench press +10.0 % in the period")
        notShown("Legs: 10.0 sets a week")
    }

    @Test
    fun `the highlights are left out of the habit page`() {
        val report = withoutHighlights().copy(consistency = full.consistency.copy(weeksMetGoal = 5))
        showPage(StatsSamples.state(report), page = StatsPage.HABIT)

        notShown("5 weeks meeting the goal")
    }

    @Test
    fun `at most three highlights are shown`() {
        showPage()

        compose.onNodeWithText("Bench press +10.0 % in the period").assertExists()
        compose.onNodeWithText("Legs: 10.0 sets a week").assertExists()
        compose.onNodeWithText("Core: 30 days since last time").assertExists()
        notShown("9 weeks meeting the goal")
    }

    @Test
    fun `the highlight sentences name the group, the machine and the count`() {
        val report = withoutHighlights().copy(
            muscleWeeks = listOf(MuscleWeek(today, MuscleGroup.LEGS, 156, 0.0)),
            recency = listOf(MuscleRecency(MuscleGroup.BACK, today.minusDays(9), 9)) +
                MuscleGroup.entries.filter { it != MuscleGroup.BACK }.map { MuscleRecency(it, null, null) },
            strength = listOf(trend("Leg press", 1, 8.0)),
        )
        showPage(StatsSamples.state(report))

        compose.onNodeWithText("Leg press +8.0 % in the period").assertExists()
        compose.onNodeWithText("Legs: 12.0 sets a week").assertExists()
        compose.onNodeWithText("Back: 9 days since last time").assertExists()
    }

    @Test
    fun `the goal highlight counts the weeks meeting the goal`() {
        val report = withoutHighlights().copy(consistency = full.consistency.copy(weeksMetGoal = 5))
        showPage(StatsSamples.state(report))

        compose.onNodeWithText("5 weeks meeting the goal").assertExists()
    }

    @Test
    fun `each highlight card names its kind`() {
        showPage()

        compose.onNodeWithText("Most trained").assertExists()
        compose.onNodeWithText("Longest rest").assertExists()
    }

    @Test
    fun `tapping a strength highlight reports the strength page`() {
        val report = withoutHighlights().copy(strength = full.strength.take(1))
        val opened = mutableListOf<StatsPage>()
        showPage(StatsSamples.state(report), StatsActions(onOpenPage = { opened += it }))

        compose.onNodeWithText("Bench press +10.0 % in the period").performClick()

        assertEquals(listOf(StatsPage.STRENGTH), opened)
    }

    @Test
    fun `tapping a body highlight reports the body page`() {
        val report = withoutHighlights().copy(
            muscleWeeks = listOf(MuscleWeek(today, MuscleGroup.LEGS, 156, 0.0)),
        )
        val opened = mutableListOf<StatsPage>()
        showPage(StatsSamples.state(report), StatsActions(onOpenPage = { opened += it }))

        compose.onNodeWithText("Legs: 12.0 sets a week").performClick()

        assertEquals(listOf(StatsPage.BODY), opened)
    }

    @Test
    fun `tapping a habit highlight reports the habit page`() {
        val report = withoutHighlights().copy(consistency = full.consistency.copy(weeksMetGoal = 5))
        val opened = mutableListOf<StatsPage>()
        showPage(StatsSamples.state(report), StatsActions(onOpenPage = { opened += it }))

        compose.onNodeWithText("5 weeks meeting the goal").performClick()

        assertEquals(listOf(StatsPage.HABIT), opened)
    }

    // Empty pages

    @Test
    fun `an empty period shows the empty card on the body page`() {
        showPage(StatsSamples.state(StatsSamples.empty))
        shown("No training in this period yet")
        notShown("Muscle groups")
    }

    @Test
    fun `an empty period shows the empty card on the strength page`() {
        showPage(StatsSamples.state(StatsSamples.empty), page = StatsPage.STRENGTH)
        shown("No training in this period yet")
        notShown("Strength trend")
    }

    @Test
    fun `an empty period shows the empty card on the habit page`() {
        showPage(StatsSamples.state(StatsSamples.empty), page = StatsPage.HABIT)
        shown("No training in this period yet")
        notShown("Summary")
    }

    // One week

    @Test
    fun `one week body page has the muscle groups and the strength per body weight, not the strength vs weight chart`() {
        showPage(StatsSamples.state(StatsSamples.oneWeek).copy(bodyWeightKg = 80.0))

        shown("Muscle groups")
        shown("Strength per body weight")
        notShown("Body weight and strength together")
    }

    @Test
    fun `one week strength page has no strength trend but keeps the records and rep ranges`() {
        showPage(StatsSamples.state(StatsSamples.oneWeek), page = StatsPage.STRENGTH)

        notShown("Strength trend")
        shown("Records")
        shown("Rep ranges")
    }

    @Test
    fun `one week habit page has the summary and the single weekly volume bar`() {
        showPage(StatsSamples.state(StatsSamples.oneWeek), page = StatsPage.HABIT)

        shown("Summary")
        shown("Weekly volume")
        // The bar chart's one description: its total and best week are the one week's volume.
        compose.onNodeWithContentDescription("7.8 t in total, best week 7.8 t", substring = true).assertIsDisplayed()
        shown("Days since each muscle group was trained")
        shown("Session length")
    }

    @Test
    fun `one week highlights name the muscle load of this week`() {
        showPage(StatsSamples.state(StatsSamples.oneWeek))

        compose.onNodeWithText("Chest: 6.0 sets a week").assertExists()
    }

    @Test
    fun `an empty one week period shows the empty card on the body page`() {
        showPage(StatsSamples.state(StatsSamples.oneWeekEmpty))
        shown("No training in this period yet")
        notShown("Muscle groups")
    }

    @Test
    fun `an empty one week period shows the empty card on the habit page`() {
        showPage(StatsSamples.state(StatsSamples.oneWeekEmpty), page = StatsPage.HABIT)
        shown("No training in this period yet")
        notShown("Summary")
    }

    // Empty and special periods

    @Test
    fun `an empty period shows the friendly card and nothing else`() {
        showPage(StatsSamples.state(StatsSamples.empty))
        shown("No training in this period yet")
        notShown("Summary")
        notShown("Records")
    }

    @Test
    fun `a cardio-only period has a summary but no strength cards`() {
        showPage(StatsSamples.state(StatsSamples.cardioOnly), page = StatsPage.HABIT)
        shown("Summary")
        shown("2 sessions a week")
        notShown("No training in this period yet")
        notShown("Weekly volume")
        notShown("Hard sets per muscle group")
        notShown("Strength trend")
        notShown("Rep ranges")
        shown("Average session: 40 min")
    }

    @Test
    fun `a period without time data leaves the session length out`() {
        showPage(
            StatsSamples.state(full.copy(times = full.times.copy(averageMinutes = null, averageMinutesBetweenSets = null))),
            page = StatsPage.HABIT,
        )
        notShown("Session length")
    }

    @Test
    fun `before the first report the page is blank`() {
        showPage(StatsUiState())
        notShown("Muscle groups")
        notShown("Summary")
        notShown("No training in this period yet")
    }

    // Taps

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
        showPage(actions = StatsActions(onOpenMachine = { opened = it }), page = StatsPage.STRENGTH)
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

    // Body measurements

    private val measured = listOf(
        BodyMeasurement(id = 2, date = today.minusDays(2), type = MeasurementType.WAIST, valueCm = 82.5),
        BodyMeasurement(id = 1, date = today.minusDays(40), type = MeasurementType.WAIST, valueCm = 84.5),
    )

    @Test
    fun `the measurements card is shown on the body page with its change over the period`() {
        showPage(StatsSamples.state().copy(measurements = measured))

        shown("Measurements")
        shown("-2.0 cm in the period")
    }

    @Test
    fun `the measurements card is the last card of the body page`() {
        showPage(StatsSamples.state().copy(measurements = measured))

        assertTrue(yOf("Measurements") > yOf("Hard sets per muscle group"))
    }

    @Test
    fun `the measurements card is shown after the empty card on the body page of an empty period`() {
        showPage(StatsSamples.state(StatsSamples.empty))

        shown("Measurements")
        shown("No measurements yet")
        assertTrue(yOf("Measurements") > yOf("No training in this period yet"))
    }

    @Test
    fun `the measurements card is not on the strength page`() {
        showPage(StatsSamples.state().copy(measurements = measured), page = StatsPage.STRENGTH)
        notShown("Measurements")
    }

    @Test
    fun `the measurements card is not on the habit page`() {
        showPage(StatsSamples.state().copy(measurements = measured), page = StatsPage.HABIT)
        notShown("Measurements")
    }

    @Test
    fun `the measurements card is not on the strength page of an empty period`() {
        showPage(StatsSamples.state(StatsSamples.empty), page = StatsPage.STRENGTH)
        notShown("Measurements")
    }

    @Test
    fun `the measurements card is not on the habit page of an empty period`() {
        showPage(StatsSamples.state(StatsSamples.empty), page = StatsPage.HABIT)
        notShown("Measurements")
    }
}

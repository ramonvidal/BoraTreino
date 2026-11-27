package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Analysis level: what it says and does, checked on the text it shows. Each section shows its own cards. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w500dp-h20000dp")
class AnalysisContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val full = StatsSamples.full
    private val today = StatsSamples.today

    /** Shows the Analysis list over [state], on [section]. */
    private fun showAnalysis(
        state: StatsUiState = StatsSamples.state(),
        actions: StatsActions = StatsActions(),
        section: AnalysisSection = AnalysisSection.HIGHLIGHTS,
    ) {
        compose.setContent { AnalysisHost(state = state, actions = actions, section = section) }
    }

    private fun showCard(content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost { content() } }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    /** For a text that more than one card may show: at least one node carries it. */
    private fun shownSomewhere(text: String) =
        assertTrue("$text is not shown", compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())

    private fun notShown(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    private fun yOf(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    // The chips and the list

    @Test
    fun `the Analysis opens on Highlights and shows only its cards`() {
        showAnalysis()

        shownSomewhere("Highlights")
        shown("Hard sets per muscle group")
        notShown("Strength trend")
        notShown("Muscle groups")
        notShown("Summary")
        notShown("Measurements")
    }

    @Test
    fun `the chips name the sections of the Analysis`() {
        showAnalysis()

        shownSomewhere("Highlights")
        shownSomewhere("Strength")
        shownSomewhere("Muscles")
        shownSomewhere("Habit")
        shownSomewhere("Body")
    }

    @Test
    fun `an empty period has only the body chip`() {
        showAnalysis(StatsSamples.state(StatsSamples.empty))

        shownSomewhere("Body")
        notShown("Highlights")
        notShown("Strength")
        notShown("Habit")
    }

    @Test
    fun `tapping a chip shows the cards of its section only`() {
        compose.setContent {
            HoistedAnalysis(state = StatsSamples.state(), start = AnalysisSection.HIGHLIGHTS)
        }
        shown("Hard sets per muscle group")

        compose.onNodeWithText("Muscles").performClick()

        shown("Body map")
        shown("Weekly volume")
        notShown("Hard sets per muscle group")
        notShown("Summary")
    }

    @Test
    fun `tapping a chip reports the section it picks`() {
        var picked: AnalysisSection? = null
        compose.setContent {
            HoistedAnalysis(state = StatsSamples.state(), start = AnalysisSection.HIGHLIGHTS, onPicked = { picked = it })
        }

        compose.onNodeWithText("Habit").performClick()

        assertEquals(AnalysisSection.HABIT, picked)
        shown("Summary")
    }

    @Test
    fun `the muscle groups card comes before the body map`() {
        showAnalysis(section = AnalysisSection.MUSCLES)

        assertTrue(yOf("Muscle groups") < yOf("Body map"))
    }

    @Test
    fun `the muscles card comes right after the body map in the analysis order`() {
        val cards = analysisCards(StatsSamples.state())

        assertEquals(cards.indexOf(AnalysisCard.BODY_MAP) + 1, cards.indexOf(AnalysisCard.MUSCLES))
    }

    @Test
    fun `the muscle groups card is left out of an empty period`() {
        showAnalysis(StatsSamples.state(StatsSamples.empty))

        notShown("Muscle groups")
    }

    @Test
    fun `the muscles section has the muscle cards with their titles`() {
        showAnalysis(section = AnalysisSection.MUSCLES)

        shown("Muscle groups")
        shown("Body map")
        shown("Weekly volume")
        notShown("Hard sets per muscle group")
    }

    @Test
    fun `the strength section has the strength cards with their titles`() {
        showAnalysis(section = AnalysisSection.STRENGTH)

        shown("Strength trend")
        shown("Records")
        shown("Rep ranges")
        notShown("Muscle groups")
    }

    @Test
    fun `the habit section has the habit cards with their titles`() {
        showAnalysis(section = AnalysisSection.HABIT)

        shown("Summary")
        shown("Days since each muscle group was trained")
        shown("Session length")
        notShown("Weekly volume")
    }

    @Test
    fun `the summary has the sessions a week, the consistency and the best week`() {
        showAnalysis(section = AnalysisSection.HABIT)

        shown("2.8 sessions a week")
        shown("Met your goal in 9 of 13 weeks")
        shown("Best week: 4 training days, week of 13 Oct")
    }

    @Test
    fun `the overall strength change is in the summary`() {
        showAnalysis(section = AnalysisSection.HABIT)

        shown("Strength: +6.4 % in this period")
    }

    @Test
    fun `without an overall strength change the summary leaves the line out`() {
        showAnalysis(StatsSamples.state(full.copy(overallStrengthPercent = null)), section = AnalysisSection.HABIT)

        notShown("Strength: +6.4 % in this period")
    }

    @Test
    fun `the volume chart description says one week in the singular`() {
        showAnalysis(StatsSamples.state(StatsSamples.oneWeek), section = AnalysisSection.MUSCLES)

        compose.onNodeWithContentDescription("Weekly volume over 1 week: 7.8 t in total, best week 7.8 t").assertIsDisplayed()
    }

    @Test
    fun `the volume chart description says the weeks in the plural`() {
        showAnalysis(section = AnalysisSection.MUSCLES)

        compose.onNodeWithContentDescription("Weekly volume over 13 weeks:", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Weekly volume over 1 week:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the volume legend shows tonnes with one decimal and kilograms below a tonne`() {
        showAnalysis(section = AnalysisSection.MUSCLES)

        shownSomewhere("12.4 t")
        shownSomewhere("8.2 t")
        shownSomewhere("850 kg")
    }

    @Test
    fun `a group that was never trained says Never`() {
        showAnalysis(section = AnalysisSection.HABIT)

        shownSomewhere("Never")
    }

    @Test
    fun `the recency rows say today and days ago`() {
        showAnalysis(section = AnalysisSection.HABIT)

        shownSomewhere("Today")
        shownSomewhere("1 day ago")
        shownSomewhere("2 days ago")
        shownSomewhere("30 days ago")
    }

    @Test
    fun `the weekly volume card labels its last block Today, never This week`() {
        showCard { WeeklyVolumeCard(full) }

        // The block labels are drawn on the chart, not as text nodes: only the absence of "This week" is checkable here.
        notShown("This week")
    }

    @Test
    fun `the hard sets note is neutral`() {
        showAnalysis(section = AnalysisSection.HIGHLIGHTS)

        shown("Many programs use roughly 10 to 20 hard sets per muscle group per week.")
    }

    @Test
    fun `the strength list shows the estimate and the change`() {
        showAnalysis(section = AnalysisSection.STRENGTH)

        shown("95 kg → 100 kg est. 1RM")
        shown("+5.3 %")
        shown("+10.0 %")
    }

    @Test
    fun `a plateau is flagged under the strength list`() {
        showAnalysis(section = AnalysisSection.STRENGTH)

        shown("Lat pulldown: no progress in 5 sessions")
    }

    @Test
    fun `the records show what was beaten and when`() {
        showAnalysis(section = AnalysisSection.STRENGTH)

        shown("Heaviest: 100 kg")
        shown("Most reps: 12 at 40 kg")
        shownSomewhere("30 Oct")
    }

    @Test
    fun `a machine in both the strength list and the records appears twice`() {
        showCard {
            Column {
                StrengthCard(full, {})
                RecordsCard(full, {})
            }
        }

        compose.onAllNodesWithText("Leg press").assertCountEquals(2)
    }

    @Test
    fun `the rep ranges show their percentages`() {
        // The card alone: on the list it sits far down, and this test is about its texts.
        showCard { RepRangesCard(StatsSamples.full) }
        // The bar and its legend are one accessibility element: a screen reader gets one sentence.
        compose.onNodeWithContentDescription(
            "1 to 5 reps: 10 %. 6 to 8 reps: 30 %. 9 to 12 reps: 45 %. 13 or more reps: 15 %",
        ).assertIsDisplayed()
        shown("Rep ranges")
    }

    @Test
    fun `the session length shows the average and the time between sets`() {
        showAnalysis(section = AnalysisSection.HABIT)

        shown("Average session: 62 min")
        shown("About 3.1 min between sets")
    }

    // Empty periods

    @Test
    fun `an empty period shows the empty card and no strength or habit card`() {
        showAnalysis(StatsSamples.state(StatsSamples.empty))

        shown("No training in this period yet")
        notShown("Strength trend")
        notShown("Summary")
        notShown("Muscle groups")
    }

    @Test
    fun `an empty period shows the friendly card and nothing else`() {
        showAnalysis(StatsSamples.state(StatsSamples.empty))

        shown("No training in this period yet")
        notShown("Summary")
        notShown("Records")
    }

    // One week

    @Test
    fun `one week has the muscle groups card in the muscles section`() {
        showAnalysis(StatsSamples.state(StatsSamples.oneWeek), section = AnalysisSection.MUSCLES)

        shown("Muscle groups")
    }

    @Test
    fun `one week has strength per body weight, not the strength vs weight chart`() {
        showAnalysis(StatsSamples.state(StatsSamples.oneWeek).copy(bodyWeightKg = 80.0), section = AnalysisSection.STRENGTH)

        shown("Strength per body weight")
        notShown("Body weight and strength together")
    }

    @Test
    fun `one week has no strength trend but keeps the records and rep ranges`() {
        showAnalysis(StatsSamples.state(StatsSamples.oneWeek), section = AnalysisSection.STRENGTH)

        notShown("Strength trend")
        shown("Records")
        shown("Rep ranges")
    }

    @Test
    fun `one week has the summary and the session length in the habit section`() {
        showAnalysis(StatsSamples.state(StatsSamples.oneWeek), section = AnalysisSection.HABIT)

        shown("Summary")
        shown("Days since each muscle group was trained")
        shown("Session length")
    }

    @Test
    fun `one week has the single weekly volume bar in the muscles section`() {
        showAnalysis(StatsSamples.state(StatsSamples.oneWeek), section = AnalysisSection.MUSCLES)

        shown("Weekly volume")
        // The bar chart's one description: its total and best week are the one week's volume.
        compose.onNodeWithContentDescription("7.8 t in total, best week 7.8 t", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an empty one week period shows the empty card and no summary`() {
        showAnalysis(StatsSamples.state(StatsSamples.oneWeekEmpty))

        shown("No training in this period yet")
        notShown("Summary")
        notShown("Muscle groups")
    }

    // Cardio and time data

    @Test
    fun `a cardio-only period has a summary but no strength cards`() {
        showAnalysis(StatsSamples.state(StatsSamples.cardioOnly), section = AnalysisSection.HABIT)

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
        showAnalysis(
            StatsSamples.state(full.copy(times = full.times.copy(averageMinutes = null, averageMinutesBetweenSets = null))),
            section = AnalysisSection.HABIT,
        )

        notShown("Session length")
    }

    @Test
    fun `before the first report the list is blank`() {
        showAnalysis(StatsUiState())

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
    fun `tapping a machine in the strength list of the analysis opens it`() {
        var opened: Long? = null
        showAnalysis(actions = StatsActions(onOpenMachine = { opened = it }), section = AnalysisSection.STRENGTH)

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
    fun `the measurements card is in the body section with its change over the period`() {
        showAnalysis(StatsSamples.state().copy(measurements = measured), section = AnalysisSection.BODY)

        shown("Measurements")
        shown("-2.0 cm in the period")
    }

    @Test
    fun `the measurements card comes after the hard sets card in the analysis order`() {
        val cards = analysisCards(StatsSamples.state().copy(measurements = measured))

        assertTrue(cards.indexOf(AnalysisCard.HARD_SETS) < cards.indexOf(AnalysisCard.MEASUREMENTS))
    }

    @Test
    fun `the measurements card is shown after the empty card of an empty period`() {
        showAnalysis(StatsSamples.state(StatsSamples.empty))

        shown("Measurements")
        shown("No measurements yet")
        assertTrue(yOf("Measurements") > yOf("No training in this period yet"))
    }

    @Test
    fun `the recency card lists a group trained yesterday as one day ago`() {
        showCard { RecencyCard(full.copy(recency = listOf(MuscleRecency(MuscleGroup.ARMS, today.minusDays(1), 1)))) }

        shown("1 day ago")
    }
}

/** The Analysis list with its section held here, as ProgressShell holds it: the chips switch the section. */
@Composable
private fun HoistedAnalysis(
    state: StatsUiState,
    start: AnalysisSection,
    onPicked: (AnalysisSection) -> Unit = {},
) {
    var section by remember { mutableStateOf(start) }
    AnalysisHost(
        state = state,
        section = section,
        onSection = {
            section = it
            onPicked(it)
        },
    )
}

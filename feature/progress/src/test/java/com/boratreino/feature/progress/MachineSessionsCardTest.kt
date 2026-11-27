package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRange
import com.boratreino.feature.progress.ProgressSamples.date
import com.boratreino.feature.progress.ProgressSamples.machine
import com.boratreino.feature.progress.ProgressSamples.points
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The sessions of the machine detail: five rows and a "Show all N sessions" button when there are more, and every
 * row once it is tapped. A session row is the only text on the weight detail with a separator dot in it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h6000dp")
class MachineSessionsCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)

    /** A weight machine with [count] sessions, one every three days from 1 Sep, all in the range. */
    private fun sessions(count: Int) = MachineProgress(
        machine(1L, "Leg press", MuscleGroup.LEGS),
        ProgressMetric.WEIGHT,
        points(1L, List(count) { date(9, 1).plusDays(3L * it) to 100.0 + 2.5 * it }),
    )

    /** The detail over [count] sessions; [onShowAll] is called on the tap, and the list then shows every session. */
    @Composable
    private fun MachineDetailHost(count: Int, startShowingAll: Boolean, onShowAll: () -> Unit) {
        var showingAll by remember { mutableStateOf(startShowingAll) }
        MachineProgressContent(
            state = buildMachineState(sessions(count), ProgressRange.ALL, today, selected = null, showAll = showingAll),
            actions = MachineProgressActions(onShowAll = {
                showingAll = true
                onShowAll()
            }),
        )
    }

    private fun show(count: Int, startShowingAll: Boolean = false, onShowAll: () -> Unit = {}) {
        compose.setContent {
            StatsScreenHost { MachineDetailHost(count, startShowingAll, onShowAll) }
        }
    }

    /** The session rows: a text of a date, a top value and the sets, so two dots (the trend's "R² · sessions" has one). */
    private fun sessionRows() = compose.onAllNodes(
        SemanticsMatcher("a session row") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.count { it == '·' } == 2 } == true
        },
        useUnmergedTree = true,
    )

    @Test
    fun `seven sessions show five rows before show all`() {
        show(7)

        sessionRows().assertCountEquals(5)
    }

    @Test
    fun `the show all button counts every session`() {
        show(7)

        compose.onNodeWithText("Show all 7 sessions").assertIsDisplayed()
    }

    @Test
    fun `tapping show all lists every session and calls back`() {
        var tapped = false
        show(7, onShowAll = { tapped = true })

        compose.onNodeWithText("Show all 7 sessions").performClick()

        sessionRows().assertCountEquals(7)
        compose.onAllNodesWithText("Show all", substring = true).assertCountEquals(0)
        assertTrue(tapped)
    }

    @Test
    fun `five sessions show all five and no show all button`() {
        show(5)

        sessionRows().assertCountEquals(5)
        compose.onAllNodesWithText("Show all", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a detail that is showing all has every session and no show all button`() {
        show(7, startShowingAll = true)

        sessionRows().assertCountEquals(7)
        compose.onAllNodesWithText("Show all", substring = true).assertCountEquals(0)
    }
}

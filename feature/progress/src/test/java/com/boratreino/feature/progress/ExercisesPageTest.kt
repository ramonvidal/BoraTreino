package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Improvement
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Exercises page of the Progress tab and the exercise screen's not-found text, checked on what they show. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ExercisesPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today

    /** Four exercises: up 20 %, down 5 %, level, and reps up 50 %. Best and last-trained dates are fixed. */
    private val rows = listOf(
        MachineRow(
            machine = ProgressSamples.legPress,
            metric = ProgressMetric.WEIGHT,
            best = 120.0,
            lastTrained = ProgressSamples.date(10, 30),
            trend = listOf(100.0, 110.0, 120.0),
        ),
        MachineRow(
            machine = ProgressSamples.latPulldown,
            metric = ProgressMetric.WEIGHT,
            best = 60.0,
            lastTrained = today,
            trend = listOf(60.0, 57.0),
        ),
        MachineRow(
            machine = ProgressSamples.chestPress,
            metric = ProgressMetric.WEIGHT,
            best = 80.0,
            lastTrained = ProgressSamples.date(10, 31),
            trend = listOf(80.0, 80.0),
        ),
        MachineRow(
            machine = ProgressSamples.pullUps,
            metric = ProgressMetric.REPS,
            best = 12.0,
            lastTrained = ProgressSamples.date(10, 30),
            trend = listOf(8.0, 12.0),
        ),
    )

    private fun state(
        machines: List<MachineRow> = rows,
        improved: List<ImprovedRow> = emptyList(),
    ) = ProgressUiState(
        loaded = true,
        today = today,
        trainingsThisMonth = 9,
        recordsThisMonth = 4,
        setsThisMonth = 37,
        improved = improved,
        machines = machines,
    )

    private fun showPage(state: ProgressUiState, onOpen: (Long) -> Unit = {}) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProgressContent(
                        state = state,
                        actions = ProgressActions(onOpenMachine = onOpen),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    @Test
    fun `the exercises list has the title Exercises`() {
        showPage(state())

        shown("Exercises")
    }

    @Test
    fun `each row shows the best and how long ago it was trained`() {
        showPage(state())

        shown("Best 120 kg · 3 days ago")
        shown("Best 80 kg · 2 days ago")
        shown("Best 12 reps · 3 days ago")
    }

    @Test
    fun `a row trained today says today`() {
        showPage(state())

        shown("Best 60 kg · today")
    }

    @Test
    fun `the change numbers carry their sign`() {
        showPage(state())

        shown("+20 %")
        shown("-5 %")
        shown("+50 %")
    }

    @Test
    fun `a row with no change shows a plain zero`() {
        showPage(state())

        shown("0")
    }

    @Test
    fun `the most improved card shows its change with a sign`() {
        val legPress = ProgressSamples.legPress
        val improved = ImprovedRow(
            machine = legPress,
            metric = ProgressMetric.WEIGHT,
            improvement = Improvement(from = 100.0, to = 108.0),
            trend = listOf(100.0, 108.0),
        )
        showPage(state(improved = listOf(improved)))

        shown("Most improved this month")
        shown("100 → 108 kg")
        shown("+8 %")
    }

    @Test
    fun `tapping a row opens its exercise`() {
        var opened: Long? = null
        showPage(state(), onOpen = { opened = it })

        compose.onNodeWithText("Leg press").performClick()

        assertEquals(ProgressSamples.legPress.id, opened)
    }

    @Test
    fun `tapping a most improved row opens its exercise`() {
        var opened: Long? = null
        val improved = ImprovedRow(
            machine = ProgressSamples.legPress,
            metric = ProgressMetric.WEIGHT,
            improvement = Improvement(from = 100.0, to = 108.0),
            trend = listOf(100.0, 108.0),
        )
        showPage(state(improved = listOf(improved)), onOpen = { opened = it })

        // The most improved card comes first, so its row is the first "Leg press" on the page.
        compose.onAllNodesWithText("Leg press")[0].performClick()

        assertEquals(ProgressSamples.legPress.id, opened)
    }

    @Test
    fun `the exercise screen says when the exercise is not found`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineProgressContent(
                        state = MachineProgressUiState(loaded = true, found = false, name = "Leg press"),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        shown("This exercise was not found.")
    }

    @Test
    fun `the exercises page is captured`() {
        val improved = ImprovedRow(
            machine = ProgressSamples.legPress,
            metric = ProgressMetric.WEIGHT,
            improvement = Improvement(from = 100.0, to = 108.0),
            trend = listOf(100.0, 108.0),
        )
        showPage(state(improved = listOf(improved)))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/progress-exercises-page.png")
    }
}

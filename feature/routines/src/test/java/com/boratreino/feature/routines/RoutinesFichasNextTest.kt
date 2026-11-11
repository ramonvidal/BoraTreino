package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "Next" pill and the "Last trained" line on the Fichas cards. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutinesFichasNextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setTab(state: RoutinesUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = state,
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = RoutinesPage.ROUTINES,
                    )
                }
            }
        }
    }

    /** The sample Fichas with the given per-row answers, in the order A, B, C. */
    private fun fichas(next: Int?, daysAgo: List<Int?>): RoutinesUiState {
        val rows = GymSamples.routines.mapIndexed { index, row ->
            row.copy(isNext = index == next, lastTrainedDaysAgo = daysAgo[index])
        }
        return GymSamples.populated.copy(routines = rows)
    }

    @Test
    fun `the Next pill is shown only on the routine flagged as next`() {
        setTab(fichas(next = 1, daysAgo = listOf(null, null, null)))

        compose.onAllNodesWithText("Next").assertCountEquals(1)
        compose.onNodeWithText("Treino B").assertExists()
    }

    @Test
    fun `no Next pill when no routine is flagged`() {
        setTab(fichas(next = null, daysAgo = listOf(null, null, null)))

        compose.onAllNodesWithText("Next").assertCountEquals(0)
    }

    @Test
    fun `a routine trained today says so`() {
        setTab(fichas(next = null, daysAgo = listOf(0, null, null)))

        compose.onNodeWithText("Last trained today").assertExists()
    }

    @Test
    fun `a routine trained yesterday says so`() {
        setTab(fichas(next = null, daysAgo = listOf(null, 1, null)))

        compose.onNodeWithText("Last trained yesterday").assertExists()
    }

    @Test
    fun `a routine trained three days ago shows the count`() {
        setTab(fichas(next = null, daysAgo = listOf(null, null, 3)))

        compose.onNodeWithText("Last trained 3 days ago").assertExists()
    }

    @Test
    fun `a routine never trained shows no last trained line`() {
        setTab(fichas(next = null, daysAgo = listOf(null, null, null)))

        compose.onAllNodesWithText("Last trained", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the Fichas cards with the next pill and the last trained lines`() {
        setTab(fichas(next = 1, daysAgo = listOf(3, 0, null)))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-fichas-next.png")
    }
}

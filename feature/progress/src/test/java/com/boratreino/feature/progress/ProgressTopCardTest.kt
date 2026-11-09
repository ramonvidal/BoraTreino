package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The top card of the Progress overview: "This month" with three big numbers (trainings, records, sets) and
 * their small labels. The numbers come from the state; the card only shows them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ProgressTopCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today

    /** The overview's state with the three month numbers replaced by [trainings], [records] and [sets]. */
    private fun stateWith(trainings: Int, records: Int, sets: Int): ProgressUiState =
        buildProgressState(ProgressSamples.overview, today).copy(
            trainingsThisMonth = trainings,
            recordsThisMonth = records,
            setsThisMonth = sets,
        )

    private fun showOverview(state: ProgressUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProgressContent(state = state, actions = ProgressActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `the top card shows the three labels under their big numbers`() {
        showOverview(stateWith(trainings = 12, records = 3, sets = 37))

        compose.onNodeWithText("This month").assertExists()
        compose.onNodeWithText("Trainings").assertExists()
        compose.onNodeWithText("Records").assertExists()
        compose.onNodeWithText("Sets").assertExists()
    }

    @Test
    fun `the top card shows the trainings, records and sets of the month`() {
        showOverview(stateWith(trainings = 12, records = 3, sets = 37))

        compose.onNodeWithText("12").assertExists()
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("37").assertExists()
    }

    @Test
    fun `the overview's own month numbers are the ones shown`() {
        val state = buildProgressState(ProgressSamples.overview, today)
        showOverview(state)

        compose.onNodeWithText(state.trainingsThisMonth.toString()).assertExists()
        compose.onNodeWithText(state.setsThisMonth.toString()).assertExists()
    }

    @Test
    fun `the top card is captured`() {
        showOverview(stateWith(trainings = 9, records = 4, sets = 54))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/progress-top-card.png")
    }
}

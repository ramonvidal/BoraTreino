package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The cardio detail's metric chips with the bike console values: only the measures with data get a chip (phase 42). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CardioMetricChipsBikeTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today

    private fun state(progress: com.boratreino.core.model.MachineProgress, cardioMetric: String? = null) =
        buildMachineState(progress, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false, cardioMetric = cardioMetric)

    private fun show(state: MachineProgressUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineProgressContent(state = state, actions = MachineProgressActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun assertShown(text: String) {
        assertTrue("chip or text \"$text\" should show", compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertNotShown(text: String) {
        assertTrue("\"$text\" should not show", compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }

    // The state

    @Test
    fun `the measures with data are the chips, in chip order`() {
        assertEquals(
            listOf(ProgressMetric.POWER, ProgressMetric.INTENSITY_FACTOR, ProgressMetric.TSS, ProgressMetric.MINUTES),
            state(BikeProgressSamples.someChips).cardioMetrics,
        )
    }

    @Test
    fun `the chart starts on power when a bike has power and no distance`() {
        assertEquals(ProgressMetric.POWER, state(BikeProgressSamples.someChips).metric)
    }

    @Test
    fun `the chart starts on speed when a bike has distance`() {
        assertEquals(ProgressMetric.SPEED, state(BikeProgressSamples.allChips).metric)
    }

    @Test
    fun `a saved choice of W per kg shows the chart in W per kg`() {
        assertEquals(ProgressMetric.WATTS_PER_KG, state(BikeProgressSamples.allChips, cardioMetric = "WATTS_PER_KG").metric)
    }

    @Test
    fun `a saved choice without data falls back to the default`() {
        assertEquals(ProgressMetric.POWER, state(BikeProgressSamples.someChips, cardioMetric = "HEART_RATE").metric)
    }

    // What is drawn

    @Test
    fun `a chip shows for each measure with data`() {
        show(state(BikeProgressSamples.someChips))

        assertShown("Power")
        assertShown("IF")
        assertShown("TSS")
        assertShown("Time")
    }

    @Test
    fun `no chip shows for a measure without data`() {
        show(state(BikeProgressSamples.someChips))

        assertNotShown("W/kg")
        assertNotShown("Heart rate")
        assertNotShown("Speed")
        assertNotShown("Distance")
    }

    @Test
    fun `every chip shows when every measure has data`() {
        show(state(BikeProgressSamples.allChips))

        assertShown("Speed")
        assertShown("Power")
        assertShown("IF")
        assertShown("W/kg")
        assertShown("TSS")
        assertShown("Heart rate")
        assertShown("Intensity")
        assertShown("Distance")
        assertShown("Time")
    }

    @Test
    fun `the records give the best power, IF and TSS`() {
        show(state(BikeProgressSamples.allChips))

        compose.onNodeWithText("Best power: 160 W").assertExists()
        compose.onNodeWithText("Best IF: IF 0.79").assertExists()
        compose.onNodeWithText("Best TSS: 48 TSS").assertExists()
    }
}

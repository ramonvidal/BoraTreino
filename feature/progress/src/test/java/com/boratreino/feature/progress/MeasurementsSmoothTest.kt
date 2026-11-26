package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BodyMeasurement
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Body page's measurements card: the Smooth switch under the chart and the trend sentence under it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2000dp")
class MeasurementsSmoothTest {
    @get:Rule
    val compose = createComposeRule()

    private val from = LocalDate.of(2026, 8, 1)
    private val today = LocalDate.of(2026, 11, 2)

    private fun show(measurements: List<BodyMeasurement>) {
        compose.setContent {
            StatsCardHost {
                MeasurementsCard(
                    measurements = measurements,
                    from = from,
                    today = today,
                    selectedType = null,
                    onType = {},
                    onLog = {},
                    onDelete = {},
                    onUndo = {},
                )
            }
        }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    @Test
    fun `the smooth switch shows with two or more entries and starts off`() {
        show(Phase46Samples.waist(80.0, 80.4))

        shown("Smooth")
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun `the smooth switch turns on and off when tapped`() {
        show(Phase46Samples.waist(80.0, 80.4, 80.8))

        compose.onNode(isToggleable()).performClick()
        compose.onNode(isToggleable()).assertIsOn()

        compose.onNode(isToggleable()).performClick()
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun `a single entry has no chart and no smooth switch`() {
        show(Phase46Samples.waist(80.0))

        compose.onNodeWithText("Smooth").assertDoesNotExist()
    }

    @Test
    fun `a rising waist says how much it rises each week`() {
        show(Phase46Samples.waist(80.0, 80.4, 80.8, 81.2))

        shown("Rising 0.4 cm per week")
    }

    @Test
    fun `a falling waist says how much it falls each week`() {
        show(Phase46Samples.waist(90.0, 89.6, 89.2, 88.8))

        shown("Falling 0.4 cm per week")
    }

    @Test
    fun `two measurements are not enough for a trend`() {
        show(Phase46Samples.waist(80.0, 80.4))

        shown("Not enough data: needs 3 measurements")
    }
}

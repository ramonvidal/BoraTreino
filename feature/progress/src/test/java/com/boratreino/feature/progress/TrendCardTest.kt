package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Forecast
import com.boratreino.core.model.TrendResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The machine trend card: the verdict, the weekly change with its range, the fit, the forecast and the explanation. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TrendCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(result: TrendResult, forecast: Forecast? = null) {
        compose.setContent {
            StatsCardHost {
                TrendCard(result = result, forecast = forecast, valueFormatter = Phase46Samples.oneDecimal, unit = "kg")
            }
        }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    @Test
    fun `a rising trend shows the verdict, the weekly change with its range and the fit`() {
        show(Phase46Samples.rising, Phase46Samples.risingForecast)

        shown("Rising")
        shown("+1.2 kg per week (95%: +0.4 to +2.0)")
        shown("R² 0.82 · 6 sessions")
    }

    @Test
    fun `a rising trend shows the four-week forecast with its range`() {
        show(Phase46Samples.rising, Phase46Samples.risingForecast)

        shown("In 4 weeks: about 92.0 kg (range 88.0-96.0)")
    }

    @Test
    fun `a plateau shows no clear change and a range that includes zero`() {
        show(Phase46Samples.plateau, Phase46Samples.risingForecast)

        shown("Plateau")
        shown("0.0 kg per week (95%: -0.3 to +0.3)")
    }

    @Test
    fun `a falling trend shows the negative weekly change and its forecast`() {
        show(Phase46Samples.falling, Phase46Samples.fallingForecast)

        shown("Falling")
        shown("-0.8 kg per week (95%: -1.1 to -0.5)")
        shown("In 4 weeks: about 84.0 kg (range 80.0-88.0)")
    }

    @Test
    fun `not enough data says what is needed and shows no change or forecast`() {
        show(Phase46Samples.notEnough, forecast = null)

        shown("Not enough data")
        shown("Needs 5 sessions over at least 2 weeks (you have 3)")
        compose.onNodeWithText("In 4 weeks", substring = true).assertDoesNotExist()
        compose.onNodeWithText("95%", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the explanation is hidden until its question is tapped and hides again`() {
        val explanation = "The range is where the true weekly change probably is. If it includes 0, there is no clear change yet."
        show(Phase46Samples.rising, Phase46Samples.risingForecast)
        compose.onNodeWithText(explanation).assertDoesNotExist()

        compose.onNodeWithText("What does this mean?").performClick()
        shown(explanation)
        shown("Hide explanation")

        compose.onNodeWithText("Hide explanation").performClick()
        compose.onNodeWithText(explanation).assertDoesNotExist()
        shown("What does this mean?")
    }
}

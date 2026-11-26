package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Forecast
import com.boratreino.core.model.TrendResult
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Screenshots of the machine trend card for each verdict, the not-enough-data state and the open explanation. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TrendCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(
        name: String,
        result: TrendResult,
        forecast: Forecast?,
        dark: Boolean = false,
        explain: Boolean = false,
    ) {
        compose.setContent {
            StatsCardHost(dark = dark) {
                TrendCard(result = result, forecast = forecast, valueFormatter = Phase46Samples.oneDecimal, unit = "kg")
            }
        }
        if (explain) compose.onNodeWithText("What does this mean?").performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/phase46-trend-$name.png")
    }

    @Test
    fun `trend rising with forecast`() = shoot("rising", Phase46Samples.rising, Phase46Samples.risingForecast)

    @Test
    fun `trend rising with forecast dark`() =
        shoot("rising-dark", Phase46Samples.rising, Phase46Samples.risingForecast, dark = true)

    @Test
    fun `trend plateau`() = shoot("plateau", Phase46Samples.plateau, Phase46Samples.risingForecast)

    @Test
    fun `trend falling with forecast`() = shoot("falling", Phase46Samples.falling, Phase46Samples.fallingForecast)

    @Test
    fun `trend not enough data`() = shoot("not-enough", Phase46Samples.notEnough, forecast = null)

    @Test
    fun `trend not enough data dark`() = shoot("not-enough-dark", Phase46Samples.notEnough, forecast = null, dark = true)

    @Test
    fun `trend explanation open`() =
        shoot("explain", Phase46Samples.rising, Phase46Samples.risingForecast, explain = true)

    @Test
    fun `trend explanation open dark`() =
        shoot("explain-dark", Phase46Samples.falling, Phase46Samples.fallingForecast, dark = true, explain = true)
}

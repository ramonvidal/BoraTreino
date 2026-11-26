package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.AcwrStatus
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Screenshots of the phase 46 Habit and Body cards: recent vs usual load, patterns (with and without a clear pattern), cardio insight, waist to hip and smoothed measurements. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class Phase46ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost(dark = dark) { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/phase46-$name.png")
    }

    @Test
    fun `training load optimal`() = shoot("workload-optimal") {
        WorkloadCard(Phase46Samples.workload(AcwrStatus.OPTIMAL))
    }

    @Test
    fun `training load very high dark`() = shoot("workload-very-high-dark", dark = true) {
        WorkloadCard(Phase46Samples.workload(AcwrStatus.VERY_HIGH, acwr = 1.8))
    }

    @Test
    fun `training load building the baseline`() = shoot("workload-baseline") {
        WorkloadCard(Phase46Samples.workload(AcwrStatus.BUILDING_BASELINE, acwr = null, historyDays = 10))
    }

    @Test
    fun `patterns with enough sessions`() = shoot("patterns") {
        PatternsCard(Phase46Samples.patterns(sessionsUsed = 18, enough = true))
    }

    @Test
    fun `patterns with enough sessions dark`() = shoot("patterns-dark", dark = true) {
        PatternsCard(Phase46Samples.patterns(sessionsUsed = 18, enough = true))
    }

    @Test
    fun `patterns not enough sessions`() = shoot("patterns-needs") {
        PatternsCard(Phase46Samples.patterns(sessionsUsed = 9, enough = false))
    }

    @Test
    fun `patterns no clear pattern`() = shoot("patterns-none") {
        PatternsCard(Phase46Samples.patterns(sessionsUsed = 18, enough = true, anySignificant = false))
    }

    @Test
    fun `patterns no clear pattern dark`() = shoot("patterns-none-dark", dark = true) {
        PatternsCard(Phase46Samples.patterns(sessionsUsed = 18, enough = true, anySignificant = false))
    }

    @Test
    fun `waist to hip ratio with a falling trend`() = shoot("proportions") {
        ProportionsCard(waistHip = Phase46Samples.waistHipWeekly, today = ProgressSamples.today)
    }

    @Test
    fun `waist to hip ratio with a falling trend dark`() = shoot("proportions-dark", dark = true) {
        ProportionsCard(waistHip = Phase46Samples.waistHipWeekly, today = ProgressSamples.today)
    }

    @Test
    fun `cardio insight lines`() = shoot("cardio-insight") {
        CardioCard(StatsSamples.full.copy(cardioBlocks = Phase46Samples.cardioBlocks, cardioInsight = Phase46Samples.cardioInsight))
    }

    @Test
    fun `cardio insight lines dark`() = shoot("cardio-insight-dark", dark = true) {
        CardioCard(StatsSamples.full.copy(cardioBlocks = Phase46Samples.cardioBlocks, cardioInsight = Phase46Samples.cardioInsight))
    }

    @Test
    fun `measurements with the smooth line on`() {
        compose.setContent {
            StatsCardHost {
                MeasurementsCard(
                    measurements = Phase46Samples.waist(90.0, 89.6, 89.2, 88.8, 89.0),
                    from = ProgressSamples.date(8, 1),
                    today = ProgressSamples.today,
                    selectedType = null,
                    onType = {},
                    onLog = {},
                    onDelete = {},
                    onUndo = {},
                )
            }
        }
        compose.onNode(isToggleable()).performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/phase46-measurements-smooth.png")
    }
}

package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.TrendVerdict
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Screenshots of the Strength page's trend summary and weekly muscle volume cards, light and dark. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class StrengthCardsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost(dark = dark) { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/phase46-$name.png")
    }

    @Test
    fun `trend summary groups`() = shoot("strength-trends") {
        TrendSummaryCard(trends = Phase46Samples.machineTrends, onOpenMachine = {})
    }

    @Test
    fun `trend summary groups dark`() = shoot("strength-trends-dark", dark = true) {
        TrendSummaryCard(trends = Phase46Samples.machineTrends, onOpenMachine = {})
    }

    @Test
    fun `trend summary with only machines that need data`() = shoot("strength-trends-needs-data") {
        TrendSummaryCard(
            trends = listOf(Phase46Samples.machineTrend(1, "Cable row", TrendVerdict.NOT_ENOUGH_DATA)),
            onOpenMachine = {},
        )
    }

    @Test
    fun `muscle volume with all four bands`() = shoot("strength-muscle-volume") {
        MuscleVolumeCard(volumes = Phase46Samples.volumes)
    }

    @Test
    fun `muscle volume with all four bands dark`() = shoot("strength-muscle-volume-dark", dark = true) {
        MuscleVolumeCard(volumes = Phase46Samples.volumes)
    }
}

package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Overview in full, each kind of finding card and the strength section with a flagged jump; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w360dp-h3000dp")
class OverviewScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun overview(name: String, state: StatsUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent { OverviewHost(state = state, dark = dark, fontScale = fontScale) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun card(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost(dark) { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // The Overview

    @Test
    fun `overview full`() = overview("overview-full", OverviewSamples.state())

    @Test
    fun `overview full dark`() = overview("overview-full-dark", OverviewSamples.state(), dark = true)

    @Test
    fun `overview full large text`() = overview("overview-full-large", OverviewSamples.state(), fontScale = 2f)

    @Test
    fun `overview with no findings`() = overview("overview-no-findings", OverviewSamples.state(findings = emptyList()))

    @Test
    fun `overview of an empty period`() = overview("overview-empty", OverviewSamples.state(report = StatsSamples.empty))

    // Each kind of finding card

    @Test
    fun `finding frequency drop`() = card("overview-finding-frequency-drop") {
        FindingCard(OverviewSamples.frequencyDrop, onClick = {})
    }

    @Test
    fun `finding low volume`() = card("overview-finding-low-volume") {
        FindingCard(OverviewSamples.lowVolume, onClick = {})
    }

    @Test
    fun `finding stalled flat`() = card("overview-finding-stalled-flat") {
        FindingCard(OverviewSamples.stalledFlat, onClick = {})
    }

    @Test
    fun `finding stalled falling`() = card("overview-finding-stalled-falling") {
        FindingCard(OverviewSamples.stalledFalling, onClick = {})
    }

    @Test
    fun `finding new record`() = card("overview-finding-new-record") {
        FindingCard(OverviewSamples.newRecord, onClick = {})
    }

    @Test
    fun `finding card dark`() = card("overview-finding-low-volume-dark", dark = true) {
        FindingCard(OverviewSamples.lowVolume, onClick = {})
    }

    // The strength section with a flagged jump

    @Test
    fun `strength section with a flagged jump`() = card("overview-strength-check") {
        StrengthSection(OverviewSamples.strength, StatsPeriod.FOUR_WEEKS, OverviewSamples.today, onOpenMachine = {})
    }

    @Test
    fun `strength section with a flagged jump dark`() = card("overview-strength-check-dark", dark = true) {
        StrengthSection(OverviewSamples.strength, StatsPeriod.FOUR_WEEKS, OverviewSamples.today, onOpenMachine = {})
    }
}

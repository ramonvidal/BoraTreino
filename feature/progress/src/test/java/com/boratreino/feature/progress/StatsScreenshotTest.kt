package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.StatsPeriod
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Stats page and each of its cards; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h3000dp")
class StatsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val full = StatsSamples.full

    private fun page(name: String, state: StatsUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            StatsScreenHost(dark, fontScale) {
                StatsContent(state = state, actions = StatsActions(), modifier = Modifier.fillMaxSize())
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun card(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent { StatsCardHost(dark, fontScale) { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // The page

    @Test
    fun `stats full`() = page("stats-full", StatsSamples.state())

    @Test
    fun `stats full dark`() = page("stats-full-dark", StatsSamples.state(), dark = true)

    @Test
    fun `stats full large text`() = page("stats-full-large", StatsSamples.state(), fontScale = 2f)

    @Test
    fun `stats empty period`() = page("stats-empty", StatsSamples.state(StatsSamples.empty))

    @Test
    fun `stats empty period dark`() = page("stats-empty-dark", StatsSamples.state(StatsSamples.empty), dark = true)

    @Test
    fun `stats cardio only`() = page("stats-cardio-only", StatsSamples.state(StatsSamples.cardioOnly))

    @Test
    fun `stats not loaded`() = page("stats-loading", StatsUiState())

    @Test
    fun `stats one year selected`() =
        page("stats-one-year", StatsSamples.state(full.copy(period = StatsPeriod.ONE_YEAR)))

    // The cards, light

    @Test
    fun `period chips`() = card("stats-period-chips") { PeriodChips(StatsPeriod.THREE_MONTHS, {}) }

    @Test
    fun `summary card`() = card("stats-summary") { SummaryCard(full) }

    @Test
    fun `weekly volume card`() = card("stats-weekly-volume") { WeeklyVolumeCard(full) }

    @Test
    fun `hard sets card`() = card("stats-hard-sets") { HardSetsCard(full) }

    @Test
    fun `recency card`() = card("stats-recency") { RecencyCard(full) }

    @Test
    fun `strength card`() = card("stats-strength") { StrengthCard(full, {}) }

    @Test
    fun `records card`() = card("stats-records") { RecordsCard(full, {}) }

    @Test
    fun `rep ranges card`() = card("stats-rep-ranges") { RepRangesCard(full) }

    @Test
    fun `session length card`() = card("stats-session-length") { SessionLengthCard(full) }

    @Test
    fun `empty card`() = card("stats-empty-card") { StatsEmptyCard() }

    // The cards, dark

    @Test
    fun `weekly volume card dark`() = card("stats-weekly-volume-dark", dark = true) { WeeklyVolumeCard(full) }

    @Test
    fun `hard sets card dark`() = card("stats-hard-sets-dark", dark = true) { HardSetsCard(full) }

    @Test
    fun `recency card dark`() = card("stats-recency-dark", dark = true) { RecencyCard(full) }

    @Test
    fun `strength card dark`() = card("stats-strength-dark", dark = true) { StrengthCard(full, {}) }

    @Test
    fun `rep ranges card dark`() = card("stats-rep-ranges-dark", dark = true) { RepRangesCard(full) }

    // The cards, largest font

    @Test
    fun `summary card large text`() = card("stats-summary-large", fontScale = 2f) { SummaryCard(full) }

    @Test
    fun `strength card large text`() = card("stats-strength-large", fontScale = 2f) { StrengthCard(full, {}) }

    @Test
    fun `records card large text`() = card("stats-records-large", fontScale = 2f) { RecordsCard(full, {}) }

    @Test
    fun `recency card large text`() = card("stats-recency-large", fontScale = 2f) { RecencyCard(full) }

    @Test
    fun `period chips large text`() = card("stats-period-chips-large", fontScale = 2f) { PeriodChips(StatsPeriod.ALL, {}) }
}

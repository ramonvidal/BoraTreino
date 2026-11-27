package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * The Progress header in Baianês at 360 dp, the narrowest phone width, so the two page chips
 * (Resumo, Exercícios) can be looked at; saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w360dp-h3000dp")
class ProgressHeaderPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun header(
        name: String,
        page: ProgressPage,
        period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
        showPeriod: Boolean = page != ProgressPage.EXERCISES,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            StatsScreenHost(dark, fontScale) {
                ProgressHeader(
                    page = page,
                    onPage = {},
                    period = period,
                    onPeriod = {},
                    showPeriod = showPeriod,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-pt.png")
    }

    private fun sheet(name: String, selected: StatsPeriod = StatsPeriod.FOUR_WEEKS, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent { StatsCardHost(dark, fontScale) { PeriodSheetContent(selected, {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-pt.png")
    }

    @Test
    fun `header exercises page`() = header("progress-header-exercises", ProgressPage.EXERCISES)

    @Test
    fun `header overview page`() = header("progress-header-overview", ProgressPage.OVERVIEW)

    @Test
    fun `header overview page dark`() = header("progress-header-overview-dark", ProgressPage.OVERVIEW, dark = true)

    @Test
    fun `header overview page large text`() = header("progress-header-overview-large", ProgressPage.OVERVIEW, fontScale = 2f)

    @Test
    fun `period sheet content`() = sheet("progress-period-sheet")

    @Test
    fun `period sheet content large text`() = sheet("progress-period-sheet-large", fontScale = 2f)
}

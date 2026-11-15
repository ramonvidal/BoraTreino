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

/** The Progress header on each page, the period sheet and the header at large text; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h3000dp")
class ProgressHeaderScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun header(
        name: String,
        page: ProgressPage,
        period: StatsPeriod = StatsPeriod.THREE_MONTHS,
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
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun sheet(name: String, selected: StatsPeriod = StatsPeriod.THREE_MONTHS, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent { StatsCardHost(dark, fontScale) { PeriodSheetContent(selected, {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // Each page selected

    @Test
    fun `header exercises page`() = header("progress-header-exercises", ProgressPage.EXERCISES)

    @Test
    fun `header body page`() = header("progress-header-body", ProgressPage.BODY)

    @Test
    fun `header strength page`() = header("progress-header-strength", ProgressPage.STRENGTH)

    @Test
    fun `header habit page`() = header("progress-header-habit", ProgressPage.HABIT)

    @Test
    fun `header one year selected`() =
        header("progress-header-one-year", ProgressPage.BODY, period = StatsPeriod.ONE_YEAR)

    // Dark and large text

    @Test
    fun `header body page dark`() = header("progress-header-body-dark", ProgressPage.BODY, dark = true)

    @Test
    fun `header exercises page dark`() =
        header("progress-header-exercises-dark", ProgressPage.EXERCISES, dark = true)

    @Test
    fun `header body page large text`() =
        header("progress-header-body-large", ProgressPage.BODY, fontScale = 2f)

    @Test
    fun `header habit page large text`() =
        header("progress-header-habit-large", ProgressPage.HABIT, fontScale = 2f)

    // The period sheet

    @Test
    fun `period sheet content`() = sheet("progress-period-sheet")

    @Test
    fun `period sheet content one week selected`() = sheet("progress-period-sheet-one-week", selected = StatsPeriod.ONE_WEEK)

    @Test
    fun `period sheet content dark`() = sheet("progress-period-sheet-dark", dark = true)

    @Test
    fun `period sheet content large text`() = sheet("progress-period-sheet-large", fontScale = 2f)
}

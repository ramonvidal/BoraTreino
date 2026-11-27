package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
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

/** The Progress tab's first screen: the header over the Overview, at a phone's height; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w360dp-h800dp")
class OverviewFirstScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `overview first screen`() {
        compose.setContent {
            StatsScreenHost {
                Column(Modifier.fillMaxSize()) {
                    ProgressHeader(
                        page = ProgressPage.OVERVIEW,
                        onPage = {},
                        period = StatsPeriod.FOUR_WEEKS,
                        onPeriod = {},
                        showPeriod = true,
                    )
                    OverviewContent(
                        state = OverviewSamples.state(),
                        actions = StatsActions(),
                        listState = rememberLazyListState(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/overview-first-screen.png")
    }
}

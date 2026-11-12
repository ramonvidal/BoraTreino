package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.model.StatsPage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Play listing screenshots in English: Leg press over three months (5) and the Stats body page (6). Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xxhdpi")
class ProgressStoreScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** Light theme on the app background at normal text size, the way the listing shows it. */
    @Composable
    private fun StoreFrame(content: @Composable () -> Unit) {
        BoraTreinoTheme(darkTheme = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { StoreFrame(content) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `store 5 machine progress`() {
        val machine = buildMachineState(
            ProgressSamples.legPressHistory,
            ProgressRange.THREE_MONTHS,
            ProgressSamples.today,
            selected = null,
            showAll = false,
        )
        shoot("store-en-5-progress-machine") {
            MachineProgressContent(state = machine, actions = MachineProgressActions(), modifier = Modifier.fillMaxSize())
        }
    }

    /**
     * The Stats body page: the highlights, then the muscle groups card at the top after one scroll step, so the
     * body map (with every shade and the body weight) shows under the pinned period and page chips.
     */
    @Test
    fun `store 6 stats body`() {
        val state = BodyInsightSamples.state(
            report = BodyInsightSamples.bodyMapReport,
            bodyWeightKg = 76.0,
            weights = BodyInsightSamples.weights,
        )
        compose.setContent {
            StoreFrame {
                StatsContent(
                    state = state,
                    actions = StatsActions(),
                    modifier = Modifier.fillMaxSize(),
                    initialPage = StatsPage.BODY,
                )
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/store-en-6-stats-body.png")
    }
}

package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The routine sheet with the "Extra workout" switch on, and the hero's pill that says extra. Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayExtraScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `routine sheet with the extra workout switch on`() {
        val state = TodaySamples.routineBNotStarted.let { s ->
            val routines = requireNotNull(s.routines)
            s.copy(routines = routines.copy(extra = true))
        }
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = TodayActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        // The first clickable thing that names Routine B is the switcher pill; its exact label depends on the chips.
        compose.onAllNodes(hasClickAction() and hasText("Routine B", substring = true)).onFirst().performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-routine-sheet-extra.png")
    }
}

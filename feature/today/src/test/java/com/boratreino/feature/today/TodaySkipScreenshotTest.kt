package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Skipping or removing a ficha machine during a training; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodaySkipScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Routine B in progress; the machines with no sets yet carry canSkip. The row no longer shows a skip icon:
     * skip and remove are in the long-press menu (see TodayRowMenuTest).
     */
    private val withCloseIcons: TodayUiState = TodaySamples.routineBInProgress.let { state ->
        val routine = requireNotNull(state.routines)
        state.copy(routines = routine.copy(machines = routine.machines.map { it.copy(canSkip = it.setCount == 0) }))
    }

    private fun setToday(state: TodayUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = TodayActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `routine rows without sets have no skip icon any more`() {
        setToday(withCloseIcons)

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-routine-skip.png")
    }

    @Test
    fun `skip dialog for a machine with no sets`() {
        val routine = requireNotNull(withCloseIcons.routines)
        val pulldown = routine.machines.single { it.machine.name == "Lat pulldown" }
        setToday(withCloseIcons.copy(skipDialog = SkipDialog(pulldown.machine, routine.activeName.orEmpty())))

        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/today-skip-dialog.png")
    }
}

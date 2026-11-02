package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The machine detail in a few states; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineProgressScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today

    private fun shoot(
        name: String,
        state: MachineProgressUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        MachineProgressContent(state = state, actions = MachineProgressActions(), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun threeMonths(selected: Int? = null) = buildMachineState(
        ProgressSamples.legPressHistory, ProgressRange.THREE_MONTHS, today, selected = selected, showAll = false,
    )

    @Test
    fun `machine progress`() = shoot("machine-progress", threeMonths())

    @Test
    fun `machine progress dark`() = shoot("machine-progress-dark", threeMonths(), dark = true)

    @Test
    fun `machine progress with a point selected`() = shoot("machine-progress-selected", threeMonths(selected = 9))

    @Test
    fun `machine progress over one year`() = shoot(
        "machine-progress-1year",
        buildMachineState(ProgressSamples.legPressHistory, ProgressRange.ONE_YEAR, today, selected = null, showAll = false),
    )

    @Test
    fun `machine progress with one session in range`() = shoot(
        "machine-progress-sparse",
        buildMachineState(ProgressSamples.legExtensionHistory, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false),
    )

    @Test
    fun `machine progress in reps`() = shoot(
        "machine-progress-reps",
        buildMachineState(ProgressSamples.pullUpsHistory, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false),
    )

    @Test
    fun `machine progress large text`() = shoot("machine-progress-large", threeMonths(), fontScale = 2f)
}

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
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The machine detail with the Top weight and Est. 1RM chips and the Best estimated 1RM card; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineProgressOneRmScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today

    private fun shoot(
        name: String,
        progress: MachineProgress,
        showOneRm: Boolean,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        val state = buildMachineState(
            progress, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false, showOneRm = showOneRm,
        )
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

    private val progress = OneRmSamples.withOneRm

    @Test
    fun `top weight selected`() = shoot("machine-progress-1rm-top-weight", progress, showOneRm = false)

    @Test
    fun `est 1rm selected`() = shoot("machine-progress-1rm", progress, showOneRm = true)

    @Test
    fun `top weight selected dark`() = shoot("machine-progress-1rm-top-weight-dark", progress, showOneRm = false, dark = true)

    @Test
    fun `est 1rm selected dark`() = shoot("machine-progress-1rm-dark", progress, showOneRm = true, dark = true)

    @Test
    fun `top weight selected large font`() =
        shoot("machine-progress-1rm-top-weight-large", progress, showOneRm = false, fontScale = 2f)

    @Test
    fun `est 1rm selected large font`() = shoot("machine-progress-1rm-large", progress, showOneRm = true, fontScale = 2f)

    @Test
    fun `no eligible sets has no chips and no card`() =
        shoot("machine-progress-1rm-none", OneRmSamples.withoutEligibleSets, showOneRm = false)

    @Test
    fun `cardio machine is unchanged`() =
        shoot("machine-progress-1rm-cardio", ProgressCardioSamples.treadmillHistory, showOneRm = false)
}

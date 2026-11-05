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

/** The same in Baianês, to check the translated text fits; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class MachineProgressOneRmPtScreenshotTest {
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
    fun `top weight selected pt`() = shoot("machine-progress-1rm-top-weight-pt", progress, showOneRm = false)

    @Test
    fun `est 1rm selected pt`() = shoot("machine-progress-1rm-pt", progress, showOneRm = true)

    @Test
    fun `est 1rm selected pt large font`() = shoot("machine-progress-1rm-pt-large", progress, showOneRm = true, fontScale = 2f)
}

package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** Phase 43: the cardio panel after a photo was read (bike values filled, Scan bike screen below them). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineScanScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `cardio panel with the scan button after a read`() {
        val bike = Machine(
            id = 23L,
            name = "Bike",
            muscleGroup = MuscleGroup.CARDIO,
            kind = MachineKind.CARDIO,
            stepKg = 1.0,
            setupNote = null,
            restSeconds = null,
            archived = false,
        )
        val state = MachineUiState(
            loaded = true,
            machine = bike,
            lastTime = null,
            lastTimeAt = Instant.parse("2026-10-24T18:00:00Z"),
            sets = emptyList(),
            cardio = CardioDraft(46, 397, 17300, watts = null, wattsPerKg = 1.2, tss = 48, intensityFactor = 0.79),
            cardioSameAgain = null,
            cardioDistanceShown = true,
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-cardio-scan.png")
    }
}

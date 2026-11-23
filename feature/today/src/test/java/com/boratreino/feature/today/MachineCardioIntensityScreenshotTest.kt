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
import com.boratreino.core.model.SetSummary
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** The cardio panel with the Intensity stepper set, and unset (a dash); saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineCardioIntensityScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val bike = Machine(
        id = 21L,
        name = "Bike",
        muscleGroup = MuscleGroup.CARDIO,
        kind = MachineKind.CARDIO,
        stepKg = 1.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun state(draft: CardioDraft) = MachineUiState(
        loaded = true,
        machine = bike,
        lastTime = SetSummary.Cardio(count = 1, minutes = 30, calories = 250, distanceM = 0),
        lastTimeAt = Instant.parse("2026-10-24T18:00:00Z"),
        sets = emptyList(),
        cardio = draft,
        cardioSameAgain = null,
        cardioDistanceShown = draft.distanceM > 0,
    )

    private fun shoot(name: String, state: MachineUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `intensity set`() = shoot("machine-cardio-intensity", state(CardioDraft(45, 520, 18_400, 0.79)))

    @Test
    fun `intensity not set shows a dash`() = shoot("machine-cardio-intensity-unset", state(CardioDraft(30, 250, 0, null)))

    @Test
    fun `intensity set in the dark`() = shoot("machine-cardio-intensity-dark", state(CardioDraft(45, 520, 18_400, 0.79)), dark = true)
}

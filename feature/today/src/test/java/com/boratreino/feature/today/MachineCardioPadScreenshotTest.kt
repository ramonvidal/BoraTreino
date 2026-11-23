package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioField
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

/** The number pad open on the cardio Distance field, in km; saved to build/outputs/roborazzi (phase 44). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineCardioPadScreenshotTest {
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

    @Test
    fun `the number pad on the distance field`() {
        val state = MachineUiState(
            loaded = true,
            machine = bike,
            lastTime = SetSummary.Cardio(count = 1, minutes = 30, calories = 250, distanceM = 17_300),
            lastTimeAt = Instant.parse("2026-10-24T18:00:00Z"),
            sets = emptyList(),
            cardio = CardioDraft(minutes = 30, calories = 250, distanceM = 17_300),
            cardioSameAgain = null,
            cardioDistanceShown = true,
            pad = NumberPad(PadTarget.CARDIO, "17.3", CardioField.DISTANCE_KM),
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }

        compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/today-cardio-pad-distance.png")
    }
}

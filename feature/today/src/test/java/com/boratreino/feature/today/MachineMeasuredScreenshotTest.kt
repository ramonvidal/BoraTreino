package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.SetType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** The exercise screen for the measured kinds, with the Log panel for each; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineMeasuredScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val farmersWalk = Machine(
        id = 21L,
        name = "Farmer's walk",
        muscleGroup = MuscleGroup.ARMS,
        kind = MachineKind.WEIGHT_TIME,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val sledPush = Machine(
        id = 22L,
        name = "Sled push",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_DISTANCE,
        stepKg = 5.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val plank = Machine(
        id = 23L,
        name = "Plank",
        muscleGroup = MuscleGroup.CORE,
        kind = MachineKind.TIME,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val at = Instant.parse("2026-11-09T18:12:00Z")

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
    fun `weight and time, with the set from last time`() = shoot(
        "machine-weight-time",
        MachineUiState(
            loaded = true,
            machine = farmersWalk,
            lastTimeSets = listOf(
                WorkoutSet(1L, 1L, farmersWalk.id, 0, 47.5, null, 30, null, null, at, SetType.NORMAL),
            ),
            weightKg = 47.5,
            reps = 0,
            seconds = 30,
        ),
    )

    @Test
    fun `weight and time dark`() = shoot(
        "machine-weight-time-dark",
        MachineUiState(loaded = true, machine = farmersWalk, weightKg = 47.5, reps = 0, seconds = 45),
        dark = true,
    )

    @Test
    fun `weight and distance`() = shoot(
        "machine-weight-distance",
        MachineUiState(loaded = true, machine = sledPush, weightKg = 40.0, reps = 0, distanceM = 25),
    )

    @Test
    fun `time with no weight`() = shoot(
        "machine-time",
        MachineUiState(loaded = true, machine = plank, weightKg = 0.0, reps = 0, seconds = 45),
    )
}

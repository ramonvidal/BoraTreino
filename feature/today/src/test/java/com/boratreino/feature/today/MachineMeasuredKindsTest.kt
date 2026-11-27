package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SetDraft
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The exercise screen for the measured kinds: weight + time, weight + distance and time. The steppers
 * shown, the one Log button's text and whether it can be tapped, and the warm-up offer. Checked through
 * what is on screen and what each tap reports.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineMeasuredKindsTest {
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

    private val legPress = Machine(
        id = 1L,
        name = "Leg press",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 5.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun setContent(state: MachineUiState, actions: MachineActions = MachineActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** The steppers of a measured kind: the load, the amount (seconds or metres) and no reps. */
    private fun measured(
        machine: Machine,
        weightKg: Double,
        seconds: Int = 30,
        distanceM: Int = 20,
    ) = MachineUiState(
        loaded = true,
        machine = machine,
        weightKg = weightKg,
        reps = 0,
        seconds = seconds,
        distanceM = distanceM,
    )

    @Test
    fun `weight and time shows a weight stepper and a seconds stepper, and no reps`() {
        setContent(measured(farmersWalk, weightKg = 47.5, seconds = 30))

        compose.onNodeWithText("Weight (kg)").assertExists()
        compose.onNodeWithText("Time (s)").assertExists()
        compose.onNodeWithText("47.5", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("30", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Reps").assertDoesNotExist()
        compose.onNodeWithText("Distance (m)").assertDoesNotExist()
    }

    @Test
    fun `weight and time Log says the load and the seconds, and one tap reports it`() {
        var logged = 0
        setContent(measured(farmersWalk, weightKg = 47.5, seconds = 30), MachineActions(onLogSet = { logged++ }))

        compose.onNodeWithText("Log 47.5 kg · 30 s").assertIsEnabled()
        compose.onNodeWithText("Log 47.5 kg · 30 s").performClick()

        assertEquals(1, logged)
    }

    @Test
    fun `the seconds stepper reports a step up and a step down`() {
        val steps = mutableListOf<Boolean>()
        setContent(measured(farmersWalk, weightKg = 47.5, seconds = 30), MachineActions(onStepSeconds = { steps += it }))

        compose.onNodeWithContentDescription("Increase Time (s)").performClick()
        compose.onNodeWithContentDescription("Decrease Time (s)").performClick()

        assertEquals(listOf(true, false), steps)
    }

    @Test
    fun `weight and distance shows a weight stepper and a metres stepper, and no seconds`() {
        setContent(measured(sledPush, weightKg = 40.0, distanceM = 20))

        compose.onNodeWithText("Weight (kg)").assertExists()
        compose.onNodeWithText("Distance (m)").assertExists()
        compose.onNodeWithText("20", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Time (s)").assertDoesNotExist()
        compose.onNodeWithText("Reps").assertDoesNotExist()
        compose.onNodeWithText("Log 40 kg · 20 m").assertIsEnabled()
    }

    @Test
    fun `the metres plus button reports a step up`() {
        val steps = mutableListOf<Boolean>()
        setContent(measured(sledPush, weightKg = 40.0, distanceM = 20), MachineActions(onStepMetres = { steps += it }))

        compose.onNodeWithContentDescription("Increase Distance (m)").performClick()

        assertEquals(listOf(true), steps)
    }

    @Test
    fun `time shows only the seconds stepper, with no weight stepper`() {
        setContent(measured(plank, weightKg = 0.0, seconds = 45))

        compose.onNodeWithText("Time (s)").assertExists()
        compose.onNodeWithText("45", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Weight (kg)").assertDoesNotExist()
        compose.onNodeWithText("Added weight (kg)").assertDoesNotExist()
        compose.onNodeWithText("Distance (m)").assertDoesNotExist()
        compose.onNodeWithText("Log 45 s").assertIsEnabled()
    }

    @Test
    fun `Log is disabled when the seconds are zero`() {
        setContent(measured(farmersWalk, weightKg = 47.5, seconds = 0))

        compose.onNodeWithText("Log 47.5 kg · 0 s").assertIsNotEnabled()
    }

    @Test
    fun `Log is disabled when the metres are zero`() {
        setContent(measured(sledPush, weightKg = 40.0, distanceM = 0))

        compose.onNodeWithText("Log 40 kg · 0 m").assertIsNotEnabled()
    }

    @Test
    fun `Log is disabled for a time with zero seconds and a tap reports nothing`() {
        var logged = 0
        setContent(measured(plank, weightKg = 0.0, seconds = 0), MachineActions(onLogSet = { logged++ }))

        compose.onNodeWithText("Log 0 s").assertIsNotEnabled()
        compose.onNodeWithText("Log 0 s").performClick()

        assertEquals(0, logged)
    }

    @Test
    fun `a time or distance kind has no warm-up sets offer`() {
        setContent(measured(farmersWalk, weightKg = 47.5, seconds = 30))

        compose.onNodeWithText("Warm-up sets").assertDoesNotExist()
    }

    @Test
    fun `a reps kind with a warm-up plan shows the warm-up sets offer`() {
        setContent(
            MachineUiState(
                loaded = true,
                machine = legPress,
                weightKg = 120.0,
                reps = 10,
                warmupPlan = listOf(SetDraft(60.0, 10)),
            ),
        )

        compose.onNodeWithText("Warm-up sets").assertExists()
    }
}

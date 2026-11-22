package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SetSummary
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** The cardio panel's Intensity stepper and the Log button's text, checked through what is shown and what a tap reports. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineCardioIntensityTest {
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

    private val lastVisit = Instant.parse("2026-10-24T18:00:00Z")
    private val lastTime = SetSummary.Cardio(count = 1, minutes = 30, calories = 250, distanceM = 0)

    private fun state(draft: CardioDraft, distanceShown: Boolean = draft.distanceM > 0, sets: List<com.boratreino.core.model.WorkoutSet> = emptyList()) =
        MachineUiState(
            loaded = true,
            machine = bike,
            lastTime = lastTime,
            lastTimeAt = lastVisit,
            sets = sets,
            cardio = draft,
            cardioSameAgain = null,
            cardioDistanceShown = distanceShown,
        )

    private fun show(state: MachineUiState, actions: MachineActions = MachineActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    // Stepper

    @Test
    fun `the intensity stepper is labelled and has its own plus and minus`() {
        show(state(CardioDraft(15, 100, 0, intensity = null)))

        compose.onNodeWithText("Intensity").assertExists()
        compose.onNodeWithContentDescription("Increase Intensity").assertExists()
        compose.onNodeWithContentDescription("Decrease Intensity").assertExists()
    }

    @Test
    fun `the intensity stepper shows a dash until an intensity is logged`() {
        show(state(CardioDraft(15, 100, 0, intensity = null)))

        compose.onAllNodesWithText("-").assertCountEquals(1)
    }

    @Test
    fun `the intensity stepper shows its number once it is set`() {
        show(state(CardioDraft(15, 100, 0, intensity = 12)))

        compose.onNodeWithText("12").assertExists()
        compose.onAllNodesWithText("-").assertCountEquals(0)
    }

    @Test
    fun `tapping plus and minus on intensity reports the step`() {
        val steps = mutableListOf<Boolean>()
        show(state(CardioDraft(15, 100, 0, intensity = null)), MachineActions(onStepIntensity = { steps += it }))

        compose.onNodeWithContentDescription("Increase Intensity").performClick()
        compose.onNodeWithContentDescription("Decrease Intensity").performClick()

        assertEquals(listOf(true, false), steps)
    }

    // Log button text

    @Test
    fun `Log names minutes, distance, intensity and calories in that order`() {
        show(state(CardioDraft(45, 520, 18_400, 12)))

        compose.onNodeWithText("Log 45 min · 18.4 km · intensity 12 · 520 kcal").assertExists()
    }

    @Test
    fun `Log leaves calories out when there are none`() {
        show(state(CardioDraft(45, 0, 18_400, 12)))

        compose.onNodeWithText("Log 45 min · 18.4 km · intensity 12").assertExists()
    }

    @Test
    fun `Log leaves the intensity out when it is not set`() {
        show(state(CardioDraft(45, 0, 18_400, intensity = null)))

        compose.onNodeWithText("Log 45 min · 18.4 km").assertExists()
    }

    @Test
    fun `Log is disabled when only an intensity is set`() {
        show(state(CardioDraft(0, 0, 0, intensity = 12)))

        compose.onNodeWithText("Log intensity 12").assertIsNotEnabled()
    }

    // The logged set's row

    @Test
    fun `a logged cardio set row shows its intensity after the distance`() {
        val set = CardioRules.toSet(
            id = 7L,
            sessionId = 1L,
            machineId = bike.id,
            position = 0,
            draft = CardioDraft(45, 0, 18_400, 12),
            loggedAt = Instant.parse("2026-11-02T18:12:00Z"),
        )
        show(state(CardioDraft(15, 0, 0, null), sets = listOf(set)))

        compose.onNodeWithText("45 min · 18.4 km · int. 12").assertExists()
    }
}

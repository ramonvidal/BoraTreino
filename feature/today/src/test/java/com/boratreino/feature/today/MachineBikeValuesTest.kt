package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** The cardio panel's Bike values: collapsed by default, the five steppers, the Log text and the set rows (phase 42). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineBikeValuesTest {
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

    private fun state(draft: CardioDraft, sets: List<WorkoutSet> = emptyList()) = MachineUiState(
        loaded = true,
        machine = bike,
        lastTime = lastTime,
        lastTimeAt = lastVisit,
        sets = sets,
        cardio = draft,
        cardioSameAgain = null,
        cardioDistanceShown = draft.distanceM > 0,
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

    private fun tapBikeValues() {
        compose.onNodeWithText("Bike values").performClick()
    }

    // Collapsed and expanded

    @Test
    fun `the bike values section is collapsed by default`() {
        show(state(CardioDraft(20, 500, 0)))

        compose.onNodeWithText("Bike values").assertExists()
        compose.onNodeWithContentDescription("Increase Watts").assertDoesNotExist()
        compose.onNodeWithText("Watts").assertDoesNotExist()
    }

    @Test
    fun `tapping Bike values shows the five console steppers`() {
        show(state(CardioDraft(20, 500, 0)))

        tapBikeValues()

        compose.onNodeWithContentDescription("Increase Watts").assertExists()
        compose.onNodeWithContentDescription("Increase Heart rate").assertExists()
        compose.onNodeWithContentDescription("Increase Watts/kg").assertExists()
        compose.onNodeWithContentDescription("Increase TSS").assertExists()
        compose.onNodeWithContentDescription("Increase IF").assertExists()
    }

    @Test
    fun `tapping Bike values again collapses the steppers`() {
        show(state(CardioDraft(20, 500, 0)))

        tapBikeValues()
        tapBikeValues()

        compose.onNodeWithContentDescription("Increase Watts").assertDoesNotExist()
    }

    @Test
    fun `the section opens by itself when the draft has watts`() {
        show(state(CardioDraft(20, 500, 0, watts = 160)))

        compose.onNodeWithContentDescription("Increase Watts").assertExists()
    }

    @Test
    fun `the section opens by itself when the draft has a heart rate`() {
        show(state(CardioDraft(20, 500, 0, heartRate = 142)))

        compose.onNodeWithContentDescription("Increase Heart rate").assertExists()
    }

    @Test
    fun `the section opens by itself when the draft has a TSS`() {
        show(state(CardioDraft(20, 500, 0, tss = 48)))

        compose.onNodeWithContentDescription("Increase TSS").assertExists()
    }

    @Test
    fun `the section opens by itself when the draft has an intensity factor`() {
        show(state(CardioDraft(20, 500, 0, intensityFactor = 0.79)))

        compose.onNodeWithContentDescription("Increase IF").assertExists()
    }

    // Steppers

    @Test
    fun `the stepper values show the logged numbers`() {
        show(state(CardioDraft(20, 500, 0, watts = 160, heartRate = 142, wattsPerKg = 2.4, tss = 48, intensityFactor = 0.79)))

        compose.onNodeWithText("160", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("142", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("2.4", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("48", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("0.79", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a stepper that is not logged shows a dash`() {
        show(state(CardioDraft(20, 500, 0, watts = 160)))

        // Heart rate, W/kg, TSS and IF are not logged; other places may show dashes too.
        val dashes = compose.onAllNodesWithText("-", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected at least four dashes, found ${dashes.size}", dashes.size >= 4)
    }

    @Test
    fun `the bike steppers report each step`() {
        val watts = mutableListOf<Boolean>()
        val heartRate = mutableListOf<Boolean>()
        val wattsPerKg = mutableListOf<Boolean>()
        val tss = mutableListOf<Boolean>()
        val intensityFactor = mutableListOf<Boolean>()
        show(
            state(CardioDraft(20, 500, 0)),
            MachineActions(
                onStepWatts = { watts += it },
                onStepHeartRate = { heartRate += it },
                onStepWattsPerKg = { wattsPerKg += it },
                onStepTss = { tss += it },
                onStepIntensityFactor = { intensityFactor += it },
            ),
        )
        tapBikeValues()

        compose.onNodeWithContentDescription("Increase Watts").performClick()
        compose.onNodeWithContentDescription("Decrease Watts").performClick()
        compose.onNodeWithContentDescription("Increase Heart rate").performClick()
        compose.onNodeWithContentDescription("Decrease Watts/kg").performClick()
        compose.onNodeWithContentDescription("Increase TSS").performClick()
        compose.onNodeWithContentDescription("Decrease IF").performClick()

        assertEquals(listOf(true, false), watts)
        assertEquals(listOf(true), heartRate)
        assertEquals(listOf(false), wattsPerKg)
        assertEquals(listOf(true), tss)
        assertEquals(listOf(false), intensityFactor)
    }

    // Log button text

    @Test
    fun `Log names the watts and the IF after the intensity and calories`() {
        show(state(CardioDraft(45, 520, 18_400, 12, watts = 160, intensityFactor = 0.79)))

        compose.onNodeWithText("Log 45 min · 18.4 km · intensity 12 · 520 kcal · 160 W · IF 0.79").assertExists()
    }

    @Test
    fun `Log adds the watts and the IF to the minutes`() {
        show(state(CardioDraft(20, 0, 0, watts = 160, intensityFactor = 0.79)))

        compose.onNodeWithText("Log 20 min · 160 W · IF 0.79").assertExists()
    }

    @Test
    fun `Log leaves the console values out when they are not logged`() {
        show(state(CardioDraft(20, 0, 0)))

        compose.onNodeWithText("Log 20 min").assertExists()
    }

    @Test
    fun `Log is disabled when only the console values are set`() {
        show(state(CardioDraft(0, 0, 0, watts = 160, intensityFactor = 0.79)))

        compose.onNodeWithText("Log 160 W · IF 0.79").assertIsNotEnabled()
    }

    // The logged set's row

    @Test
    fun `a logged cardio set row shows its watts, bpm and IF after the intensity`() {
        val set = CardioRules.toSet(
            id = 7L,
            sessionId = 1L,
            machineId = bike.id,
            position = 0,
            draft = CardioDraft(minutes = 20, calories = 0, distanceM = 0, watts = 160, heartRate = 142, intensityFactor = 0.79),
            loggedAt = Instant.parse("2026-11-02T18:12:00Z"),
        )
        show(state(CardioDraft(15, 0, 0), sets = listOf(set)))

        compose.onNodeWithText("20 min · 160 W · 142 bpm · IF 0.79").assertExists()
    }
}

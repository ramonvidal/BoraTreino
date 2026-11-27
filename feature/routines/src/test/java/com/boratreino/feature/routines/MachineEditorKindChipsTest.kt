package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ConversionFactor
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.NotOfferedReason
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The editor's kind chips: the six kinds, the ones that would lose logged sets are locked with the
 * caption that says why, and the weight step is only offered for kinds with a load.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineEditorKindChipsTest {
    @get:Rule
    val compose = createComposeRule()

    private val sample = WorkoutSet(
        id = 1L,
        sessionId = 20L,
        machineId = 5L,
        position = 0,
        weightKg = 47.5,
        reps = 10,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = Instant.parse("2026-11-02T18:20:00Z"),
    )

    /** A saved weight + reps machine with three logged sets: Time and Cardio can't keep their load and numbers. */
    private fun withSets(setCount: Int = 3, locked: Boolean = true) = MachineEditorUiState(
        loading = false,
        machineId = 5L,
        name = "Leg press",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 2.5,
        conversions = if (locked) {
            mapOf(
                MachineKind.BODYWEIGHT_REPS to KindConversion.Unchanged(setCount),
                MachineKind.WEIGHT_TIME to KindConversion.NeedsFactor(setCount, ConversionFactor.SECONDS_PER_REP, 30, sample),
                MachineKind.WEIGHT_DISTANCE to KindConversion.NeedsFactor(setCount, ConversionFactor.METRES_PER_REP, null, sample),
                MachineKind.TIME to KindConversion.NotOffered(NotOfferedReason.LOAD_WOULD_BE_LOST),
                MachineKind.CARDIO to KindConversion.NotOffered(NotOfferedReason.CARDIO),
            )
        } else {
            emptyMap()
        },
        setCount = setCount,
    )

    private fun setContent(state: MachineEditorUiState, callbacks: MachineEditorCallbacks = MachineEditorCallbacks()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineEditorContent(state = state, callbacks = callbacks, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `the editor offers the six kinds`() {
        setContent(withSets(locked = false))

        compose.onNodeWithText("Weight + reps").assertExists()
        compose.onNodeWithText("Bodyweight").assertExists()
        compose.onNodeWithText("Weight + time").assertExists()
        compose.onNodeWithText("Weight + distance").assertExists()
        compose.onNodeWithText("Time").assertExists()
        // The muscle group Cardio is a chip too, so the kind chip is the second one.
        compose.onAllNodesWithText("Cardio").assertCountEquals(2)
    }

    @Test
    fun `Time and Cardio are locked for logged sets, and the kinds that keep them are not`() {
        setContent(withSets())

        compose.onNodeWithText("Time").assertIsNotEnabled()
        compose.onAllNodesWithText("Cardio")[0].assertIsNotEnabled()
        compose.onAllNodesWithText("Cardio")[1].assertIsNotEnabled()
        compose.onNodeWithText("Weight + time").assertIsEnabled()
        compose.onNodeWithText("Weight + distance").assertIsEnabled()
        compose.onNodeWithText("Bodyweight").assertIsEnabled()
        compose.onNodeWithText("Weight + reps").assertIsEnabled()
    }

    @Test
    fun `the locked caption counts the sets and says why each kind is off`() {
        setContent(withSets(setCount = 3))

        compose.onNodeWithText("Kinds that can't keep these 3 sets are off.").assertExists()
        compose.onNodeWithText("Time only has no weight, so it would lose the weight of your sets.").assertExists()
        compose.onNodeWithText(
            "Cardio and strength sets measure different things, so they can't be converted into each other.",
        ).assertExists()
        compose.onNodeWithText(
            "Time and distance sets can't be converted into each other. Pick weight + reps first, then change again.",
        ).assertDoesNotExist()
    }

    @Test
    fun `with one logged set the caption is singular`() {
        setContent(withSets(setCount = 1))

        compose.onNodeWithText("Kinds that can't keep this 1 set are off.").assertExists()
    }

    @Test
    fun `no caption and no locked chip when every kind keeps the sets`() {
        setContent(withSets(locked = false))

        compose.onNodeWithText("Kinds that can't keep these 3 sets are off.").assertDoesNotExist()
        compose.onNodeWithText("Time").assertIsEnabled()
        compose.onAllNodesWithText("Cardio")[1].assertIsEnabled()
    }

    @Test
    fun `tapping a locked kind changes nothing`() {
        val picked = mutableListOf<MachineKind>()
        setContent(withSets(), MachineEditorCallbacks(onKindChange = { picked += it }))

        compose.onNodeWithText("Time").performClick()

        assertTrue(picked.isEmpty())
    }

    @Test
    fun `tapping an offered kind reports it`() {
        val picked = mutableListOf<MachineKind>()
        setContent(withSets(), MachineEditorCallbacks(onKindChange = { picked += it }))

        compose.onNodeWithText("Weight + time").performClick()

        assertEquals(listOf(MachineKind.WEIGHT_TIME), picked)
    }

    @Test
    fun `the weight step is offered for a kind with a load and hidden for time`() {
        setContent(
            MachineEditorUiState(loading = false, kind = MachineKind.WEIGHT_TIME, stepKg = 2.5),
        )
        compose.onNodeWithText("Weight step").assertExists()
        compose.onNodeWithText("2.5 kg").assertExists()
    }

    @Test
    fun `time has no weight step`() {
        setContent(MachineEditorUiState(loading = false, kind = MachineKind.TIME, stepKg = 2.5))

        compose.onNodeWithText("Weight step").assertDoesNotExist()
        compose.onNodeWithText("2.5 kg").assertDoesNotExist()
    }
}

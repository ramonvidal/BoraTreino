package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import com.boratreino.core.model.Suggestion
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The exercise screen's header, the "Try" chip, the one Log button that says what it logs, the quiet
 * "Set type" row and the Warm-up sets offer. Checked through what is shown and what each tap reports.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineLogPanelTest {
    @get:Rule
    val compose = createComposeRule()

    private val legPress = TodaySamples.legPress
    private val at = Instant.parse("2026-11-09T18:12:00Z")
    private val legPressTarget = RoutineItem(7L, legPress.id, 0, 3, 8, 12)

    private val pullUps = Machine(
        id = 30L,
        name = "Pull-ups",
        muscleGroup = MuscleGroup.BACK,
        kind = MachineKind.BODYWEIGHT_REPS,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val treadmill = Machine(
        id = 20L,
        name = "Treadmill",
        muscleGroup = MuscleGroup.CARDIO,
        kind = MachineKind.CARDIO,
        stepKg = 1.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    /** Leg press, two of its three programmed sets done; the steppers are at 125 kg x 8. */
    private fun legPressInProgress(
        suggestion: Suggestion? = null,
        warmupPlan: List<SetDraft> = emptyList(),
    ) = MachineUiState(
        loaded = true,
        machine = legPress,
        sets = listOf(
            TodaySamples.set(1L, legPress, 0, 120.0, 12, at),
            TodaySamples.set(2L, legPress, 1, 120.0, 12, at),
        ),
        weightKg = 125.0,
        reps = 8,
        sameAgain = SetDraft(120.0, 12),
        target = legPressTarget,
        programmedSets = 3,
        doneSets = 2,
        suggestion = suggestion,
        warmupPlan = warmupPlan,
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

    @Test
    fun `the header says which set is next out of the programmed ones`() {
        setContent(legPressInProgress())

        compose.onNodeWithText("Set 3 of 3").assertExists()
    }

    @Test
    fun `without a routine target the header says only the set number`() {
        setContent(legPressInProgress().copy(programmedSets = null, target = null, doneSets = 0, sets = emptyList()))

        compose.onNodeWithText("Set 1").assertExists()
    }

    @Test
    fun `the Try chip applies the suggestion and nothing else`() {
        var applied = 0
        var logged = 0
        setContent(
            legPressInProgress(suggestion = Suggestion.Up(weightKg = 125.0, reps = 8)),
            MachineActions(onApplySuggestion = { applied++ }, onLogSet = { logged++ }),
        )

        compose.onNodeWithText("Try 125 kg").performClick()

        assertEquals(1, applied)
        assertEquals(0, logged)
    }

    @Test
    fun `no Try chip without a suggestion`() {
        setContent(legPressInProgress(suggestion = null))

        compose.onNodeWithText("Try 125 kg").assertDoesNotExist()
    }

    @Test
    fun `the one Log button says what it logs and one tap reports it`() {
        var logged = 0
        setContent(legPressInProgress(), MachineActions(onLogSet = { logged++ }))

        compose.onNodeWithText("Log 125 kg x 8").performClick()

        assertEquals(1, logged)
    }

    @Test
    fun `a bodyweight exercise without added weight says Log and the reps`() {
        setContent(
            MachineUiState(
                loaded = true,
                machine = pullUps,
                weightKg = 0.0,
                reps = 8,
            ),
        )

        compose.onNodeWithText("Log 8 reps").assertExists()
    }

    @Test
    fun `Same again is not shown even when last time offers it`() {
        setContent(legPressInProgress())

        compose.onNodeWithText("Same again").assertDoesNotExist()
        compose.onNodeWithText("Log set").assertDoesNotExist()
    }

    @Test
    fun `Set type Normal opens the sheet and picking Warm-up reports the type`() {
        val picked = mutableListOf<SetType>()
        setContent(legPressInProgress(), MachineActions(onSetType = { picked += it }))

        compose.onNodeWithText("Set type: Normal").performClick()
        compose.onNodeWithText("Warm-up").performClick()

        assertEquals(listOf(SetType.WARMUP), picked)
    }

    @Test
    fun `the Warm-up sets button is shown only when there is an offer`() {
        setContent(legPressInProgress(warmupPlan = listOf(SetDraft(60.0, 8, SetType.WARMUP))))
        compose.onNodeWithText("Warm-up sets").assertExists()

        setContent(legPressInProgress())
        compose.onNodeWithText("Warm-up sets").assertDoesNotExist()
    }

    @Test
    fun `the sunny exercise screen shot`() {
        setContent(legPressInProgress(suggestion = Suggestion.Up(weightKg = 125.0, reps = 8)))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-sunny.png")
    }

    @Test
    fun `the sunny cardio exercise screen shot`() {
        setContent(
            MachineUiState(
                loaded = true,
                machine = treadmill,
                cardio = CardioDraft(minutes = 20, calories = 100, distanceM = 0),
                cardioDistanceShown = false,
            ),
        )

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-sunny-cardio.png")
    }
}

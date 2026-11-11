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
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The machine screen once its exercise has all its programmed sets: the Exercise complete panel replaces the
 * logging panel and offers the next exercises; "Add an extra set" brings the logging panel back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineExerciseCompleteScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val legPress = TodaySamples.legPress
    private val seatedRow = TodaySamples.seatedRow
    private val pullUps = TodaySamples.pullUps
    private val treadmill = Machine(20L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

    private val at = Instant.parse("2026-11-09T18:12:00Z")

    private fun doneSet(id: Long, position: Int) = TodaySamples.set(id, legPress, position, 120.0, 12, at)

    private val legPressTarget = RoutineItem(7L, legPress.id, 0, 3, 8, 12)

    /** Three of three programmed sets done, with two exercises still to do. */
    private val complete = MachineUiState(
        loaded = true,
        machine = legPress,
        sets = listOf(doneSet(1L, 0), doneSet(2L, 1), doneSet(3L, 2)),
        weightKg = 120.0,
        reps = 12,
        sameAgain = SetDraft(120.0, 12),
        target = legPressTarget,
        programmedSets = 3,
        doneSets = 3,
        nextExercises = listOf(
            NextExerciseUi(seatedRow, RoutineItem(7L, seatedRow.id, 1, 3, 8, 12)),
            NextExerciseUi(pullUps, RoutineItem(7L, pullUps.id, 2, 2, 5, 8)),
        ),
    )

    private val cardioTarget = RoutineItem(7L, treadmill.id, 0, 2, 1, 1)

    /** Two of two programmed cardio sets done; leg press is still to do. */
    private val cardioComplete = MachineUiState(
        loaded = true,
        machine = treadmill,
        sets = listOf(
            CardioRules.toSet(1L, 1L, treadmill.id, 0, CardioDraft(20, 100, 0), at),
            CardioRules.toSet(2L, 1L, treadmill.id, 1, CardioDraft(20, 100, 0), at),
        ),
        cardio = CardioDraft(20, 100, 0),
        cardioSameAgain = CardioDraft(20, 100, 0),
        target = cardioTarget,
        programmedSets = 2,
        doneSets = 2,
        nextExercises = listOf(NextExerciseUi(legPress, RoutineItem(7L, legPress.id, 1, 3, 8, 12))),
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
    fun `a complete exercise shows the complete panel instead of the logging panel`() {
        setContent(complete)

        compose.onNodeWithText("Exercise complete").assertExists()
        compose.onNodeWithText("3 of 3 sets").assertExists()
        compose.onNodeWithText("Next exercises").assertExists()
        compose.onNodeWithText("Seated row").assertExists()
        compose.onNodeWithText("Pull-ups").assertExists()
        compose.onNodeWithText("Back to the list").assertExists()
        compose.onNodeWithText("Add an extra set").assertExists()
        compose.onNodeWithText("Log 120 kg x 12").assertDoesNotExist()
        compose.onNodeWithText("Same again").assertDoesNotExist()
    }

    @Test
    fun `the set count is capped at the programmed sets`() {
        setContent(complete.copy(doneSets = 4))

        compose.onNodeWithText("3 of 3 sets").assertExists()
    }

    @Test
    fun `without next exercises the panel has no Next exercises title`() {
        setContent(complete.copy(nextExercises = emptyList()))

        compose.onNodeWithText("Exercise complete").assertExists()
        compose.onNodeWithText("Next exercises").assertDoesNotExist()
    }

    @Test
    fun `Back to the list calls onBack`() {
        var back = 0
        setContent(complete, MachineActions(onBack = { back++ }))

        compose.onNodeWithText("Back to the list").performClick()

        assertEquals(1, back)
    }

    @Test
    fun `tapping a next exercise opens that machine`() {
        val opened = mutableListOf<Long>()
        setContent(complete, MachineActions(onOpenNext = { opened += it }))

        compose.onNodeWithText("Seated row").performClick()
        compose.onNodeWithText("Pull-ups").performClick()

        assertEquals(listOf(seatedRow.id, pullUps.id), opened)
    }

    @Test
    fun `Add an extra set calls onExtraSet`() {
        var extra = 0
        setContent(complete, MachineActions(onExtraSet = { extra++ }))

        compose.onNodeWithText("Add an extra set").performClick()

        assertEquals(1, extra)
    }

    @Test
    fun `with an extra set the Log button is shown again`() {
        var logged = 0
        setContent(complete.copy(extraSet = true), MachineActions(onLogSet = { logged++ }))

        compose.onNodeWithText("Exercise complete").assertDoesNotExist()
        compose.onNodeWithText("Log 120 kg x 12").performClick()

        assertEquals(1, logged)
    }

    @Test
    fun `the Log button is shown while the exercise has sets to do`() {
        setContent(complete.copy(doneSets = 2))

        compose.onNodeWithText("Log 120 kg x 12").assertExists()
        compose.onNodeWithText("Exercise complete").assertDoesNotExist()
        compose.onNodeWithText("Add an extra set").assertDoesNotExist()
    }

    @Test
    fun `the Log button is shown for an exercise that is not in the routine`() {
        setContent(complete.copy(programmedSets = null, target = null, nextExercises = emptyList()))

        compose.onNodeWithText("Log 120 kg x 12").assertExists()
        compose.onNodeWithText("Exercise complete").assertDoesNotExist()
    }

    @Test
    fun `a complete cardio exercise shows the complete panel instead of the cardio log`() {
        setContent(cardioComplete)

        compose.onNodeWithText("Exercise complete").assertExists()
        compose.onNodeWithText("2 of 2 sets").assertExists()
        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("Back to the list").assertExists()
        compose.onNodeWithText("Log 20 min · 100 kcal").assertDoesNotExist()
        compose.onNodeWithText("Same again").assertDoesNotExist()
    }

    @Test
    fun `a cardio exercise still to do keeps its Log panel`() {
        setContent(cardioComplete.copy(doneSets = 1))

        compose.onNodeWithText("Log 20 min · 100 kcal").assertExists()
        compose.onNodeWithText("Exercise complete").assertDoesNotExist()
    }

    private fun shoot(name: String, state: MachineUiState) {
        setContent(state)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `the exercise complete shot`() = shoot("machine-exercise-complete", complete)

    @Test
    fun `the logging panel shot with one Log button`() = shoot(
        "machine-log-centred",
        MachineUiState(
            loaded = true,
            machine = legPress,
            sets = listOf(doneSet(1L, 0)),
            weightKg = 125.0,
            reps = 8,
            sameAgain = SetDraft(120.0, 12),
            target = legPressTarget,
            programmedSets = 3,
            doneSets = 1,
        ),
    )
}

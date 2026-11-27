package com.boratreino.feature.routines

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ConversionFactor
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The question asked before logged sets change kind. Owner cases: Farmer's walk (reps to seconds, factor
 * 30) and metres per rep (no factor yet: the value reads "-" and Convert waits). Back extension keeps its
 * numbers, so it asks no factor. Checked through the texts on screen and what each tap reports.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ConvertKindDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val farmersWalk = Machine(
        id = 21L,
        name = "Farmer's walk",
        muscleGroup = MuscleGroup.ARMS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val sledPush = farmersWalk.copy(id = 22L, name = "Sled push", muscleGroup = MuscleGroup.LEGS, stepKg = 5.0)

    private val backExtension = farmersWalk.copy(
        id = 23L,
        name = "Back extension",
        muscleGroup = MuscleGroup.BACK,
        kind = MachineKind.BODYWEIGHT_REPS,
    )

    private val at = Instant.parse("2026-11-02T18:20:00Z")

    private fun repSet(machine: Machine, kg: Double, reps: Int) = WorkoutSet(
        id = 1L,
        sessionId = 20L,
        machineId = machine.id,
        position = 0,
        weightKg = kg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = at,
    )

    private class Taps {
        val factorSteps = mutableListOf<Boolean>()
        val factorSets = mutableListOf<Int>()
        var confirmed = 0
        var dismissed = 0
    }

    private fun setContent(dialog: ConvertDialog?, taps: Taps = Taps()) {
        compose.setContent {
            BoraTreinoTheme {
                ConvertKindDialog(
                    dialog = dialog,
                    onFactorStep = { taps.factorSteps += it },
                    onFactorSet = { taps.factorSets += it },
                    onConfirm = { taps.confirmed++ },
                    onDismiss = { taps.dismissed++ },
                )
            }
        }
    }

    /** Farmer's walk: [count] sets of 47.5 kg x 1, converting to a time kind with [factor] seconds per rep. */
    private fun farmersToSeconds(count: Int, factor: Int? = 30) = ConvertDialog(
        before = farmersWalk,
        after = farmersWalk.copy(kind = MachineKind.WEIGHT_TIME),
        conversion = KindConversion.NeedsFactor(
            setCount = count,
            factor = ConversionFactor.SECONDS_PER_REP,
            default = 30,
            sample = repSet(farmersWalk, 47.5, 1),
        ),
        factor = factor,
    )

    @Test
    fun `farmers walk asks how many seconds each rep was, with the factor and a preview`() {
        setContent(farmersToSeconds(count = 1, factor = 30))

        compose.onNodeWithText("Each rep was how many seconds?").assertExists()
        compose.onNodeWithText("Seconds per rep").assertExists()
        compose.onNodeWithText("30 s", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("The 1 set keeps its load; every rep becomes 30 s.").assertExists()
        compose.onNodeWithText("Your latest set").assertExists()
        compose.onNodeWithText("47.5 kg × 1 becomes 47.5 kg · 30 s").assertExists()
        compose.onNodeWithText("This can't be undone.").assertExists()
    }

    @Test
    fun `farmers walk with one set says Convert 1 set and reports the tap`() {
        val taps = Taps()
        setContent(farmersToSeconds(count = 1, factor = 30), taps)

        compose.onNodeWithText("Convert 1 set").performClick()

        assertEquals(1, taps.confirmed)
    }

    @Test
    fun `farmers walk with three sets says Convert 3 sets and the summary counts them`() {
        setContent(farmersToSeconds(count = 3, factor = 30))

        compose.onNodeWithText("Convert 3 sets").assertExists()
        compose.onNodeWithText("Each of the 3 sets keeps its load; every rep becomes 30 s.").assertExists()
    }

    @Test
    fun `the seconds stepper reports a step up and a step down`() {
        val taps = Taps()
        setContent(farmersToSeconds(count = 1, factor = 30), taps)

        compose.onNodeWithContentDescription("Increase Seconds per rep").performClick()
        compose.onNodeWithContentDescription("Decrease Seconds per rep").performClick()

        assertEquals(listOf(true, false), taps.factorSteps)
    }

    @Test
    fun `metres per rep with no number yet shows a dash, a pending preview and Convert waits`() {
        val taps = Taps()
        val dialog = ConvertDialog(
            before = sledPush,
            after = sledPush.copy(kind = MachineKind.WEIGHT_DISTANCE),
            conversion = KindConversion.NeedsFactor(
                setCount = 1,
                factor = ConversionFactor.METRES_PER_REP,
                default = null,
                sample = repSet(sledPush, 40.0, 10),
            ),
            factor = null,
        )
        setContent(dialog, taps)

        compose.onNodeWithText("Each rep was how many metres?").assertExists()
        compose.onNodeWithText("Metres per rep").assertExists()
        compose.onNodeWithText("-", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("40 kg × 10 becomes ... (set the number above)").assertExists()
        compose.onNodeWithText("keeps its load", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Convert 1 set").assertIsNotEnabled()
    }

    @Test
    fun `metres per rep once a number is set shows the converted set and Convert works`() {
        val taps = Taps()
        val dialog = ConvertDialog(
            before = sledPush,
            after = sledPush.copy(kind = MachineKind.WEIGHT_DISTANCE),
            conversion = KindConversion.NeedsFactor(
                setCount = 1,
                factor = ConversionFactor.METRES_PER_REP,
                default = null,
                sample = repSet(sledPush, 40.0, 1),
            ),
            factor = 5,
        )
        setContent(dialog, taps)

        compose.onNodeWithText("5 m", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("40 kg × 1 becomes 40 kg · 5 m").assertExists()
        compose.onNodeWithText("Convert 1 set").performClick()

        assertEquals(1, taps.confirmed)
    }

    @Test
    fun `back extension keeps its numbers, asks no factor and says so`() {
        val taps = Taps()
        val dialog = ConvertDialog(
            before = backExtension,
            after = backExtension.copy(kind = MachineKind.WEIGHT_REPS),
            conversion = KindConversion.Unchanged(setCount = 3),
        )
        setContent(dialog, taps)

        compose.onNodeWithText("Change to Weight + reps?").assertExists()
        compose.onNodeWithText("Your 3 sets keep their numbers.", substring = true).assertExists()
        compose.onNodeWithText("Seconds per rep").assertDoesNotExist()
        compose.onNodeWithText("Metres per rep").assertDoesNotExist()
        compose.onNodeWithText("Convert 3 sets").assertDoesNotExist()

        compose.onNodeWithText("Change").performClick()

        assertEquals(1, taps.confirmed)
    }

    @Test
    fun `Cancel closes the question and changes nothing`() {
        val taps = Taps()
        setContent(farmersToSeconds(count = 1, factor = 30), taps)

        compose.onNodeWithText("Cancel").performClick()

        assertEquals(1, taps.dismissed)
        assertEquals(0, taps.confirmed)
    }
}

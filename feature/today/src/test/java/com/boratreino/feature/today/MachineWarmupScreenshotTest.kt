package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.SetType
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

private fun typedSet(id: Long, position: Int, kg: Double, reps: Int, type: SetType, at: String) =
    TodaySamples.set(id, TodaySamples.legPress, position, kg, reps, Instant.parse(at)).copy(type = type)

private val warmupTreadmill =
    Machine(40L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

private val warmupSet = typedSet(1, 0, 60.0, 12, SetType.WARMUP, "2026-10-31T18:05:00Z")
private val normalSet = typedSet(2, 1, 120.0, 12, SetType.NORMAL, "2026-10-31T18:08:00Z")
private val dropSet = typedSet(3, 2, 100.0, 10, SetType.DROP, "2026-10-31T18:11:00Z")
private val failureSet = typedSet(4, 3, 120.0, 8, SetType.FAILURE, "2026-10-31T18:14:00Z")

private val warmupBase = MachineUiState(
    loaded = true,
    machine = TodaySamples.legPress,
    lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
    lastTimeAt = TodaySamples.lastVisit,
    sets = emptyList(),
    weightKg = 120.0,
    reps = 12,
    sameAgain = SetDraft(120.0, 12),
)

private val withTypes = warmupBase.copy(sets = listOf(warmupSet, normalSet, dropSet, failureSet), reps = 8)

/**
 * The "Set type" row of the log panel (it shows Warm-up while that type is picked), the labelled sets and the
 * edit sheet's type chips; saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineWarmupScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: MachineUiState,
        actions: MachineActions = MachineActions(),
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        MachineContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    // The Set type row

    @Test
    fun `set type row shows Normal when nothing is picked`() {
        setContent(warmupBase)
        compose.onNodeWithText("Set type: Normal").assertExists()
        shoot("machine-warmup-off")
    }

    @Test
    fun `set type row shows Warm-up once it is picked`() {
        setContent(warmupBase.copy(warmupOn = true))
        compose.onNodeWithText("Set type: Warm-up").assertExists()
        shoot("machine-warmup-on")
    }

    @Test
    fun `set type row dark`() {
        setContent(warmupBase, dark = true)
        shoot("machine-warmup-off-dark")
    }

    @Test
    fun `set type row with Warm-up dark`() {
        setContent(warmupBase.copy(warmupOn = true), dark = true)
        shoot("machine-warmup-on-dark")
    }

    @Test
    fun `set type row large font`() {
        setContent(warmupBase, fontScale = 1.8f)
        shoot("machine-warmup-off-large-font")
    }

    @Test
    fun `set type row with Warm-up large font`() {
        setContent(warmupBase.copy(warmupOn = true), fontScale = 1.8f)
        shoot("machine-warmup-on-large-font")
    }

    @Test
    fun `tapping Set type opens the sheet and picking Warm-up reports it`() {
        val picked = mutableListOf<SetType>()
        setContent(warmupBase, MachineActions(onSetType = { picked += it }))

        compose.onNodeWithText("Set type: Normal").performClick()
        compose.onNodeWithText("Warm-up").performClick()

        assertEquals(listOf(SetType.WARMUP), picked)
    }

    @Test
    fun `cardio machine has no Warm-up choice`() {
        setContent(
            MachineUiState(loaded = true, machine = warmupTreadmill),
        )
        compose.onAllNodesWithText("Warm-up").assertCountEquals(0)
        compose.onAllNodesWithText("Set type: Normal").assertCountEquals(0)
    }

    // Labelled sets

    @Test
    fun `sets list labels warm-up, drop and failure and leaves normal bare`() {
        setContent(withTypes)

        // One label each in the list; the Set type row says "Set type: Normal", not "Warm-up".
        compose.onAllNodesWithText("Warm-up").assertCountEquals(1)
        compose.onNodeWithText("Drop set").assertExists()
        compose.onNodeWithText("Failure").assertExists()
        compose.onAllNodesWithText("Normal").assertCountEquals(0)
        shoot("machine-set-types")
    }

    @Test
    fun `sets list with types dark`() {
        setContent(withTypes, dark = true)
        shoot("machine-set-types-dark")
    }

    @Test
    fun `sets list with types large font`() {
        setContent(withTypes, fontScale = 1.8f)
        shoot("machine-set-types-large-font")
    }

    // Edit sheet

    @Test
    fun `edit sheet shows the four type chips with the set's type selected`() {
        setContent(withTypes.copy(editor = SetEditor(dropSet, 3, 100.0, 10, SetType.DROP)))

        compose.onNodeWithText("Type").assertExists()
        compose.onNodeWithText("Normal").assertExists()
        // The failure set's row label and the sheet's chip.
        compose.onAllNodesWithText("Failure").assertCountEquals(2)
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/machine-set-types-sheet.png")
    }

    @Test
    fun `edit sheet dark`() {
        setContent(withTypes.copy(editor = SetEditor(warmupSet, 1, 60.0, 12, SetType.WARMUP)), dark = true)
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/machine-set-types-sheet-dark.png")
    }

    @Test
    fun `edit sheet large font`() {
        setContent(withTypes.copy(editor = SetEditor(failureSet, 4, 120.0, 8, SetType.FAILURE)), fontScale = 1.8f)
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/machine-set-types-sheet-large-font.png")
    }

    @Test
    fun `tapping a type chip in the sheet reports it`() {
        val picked = mutableListOf<SetType>()
        setContent(
            warmupBase.copy(sets = listOf(normalSet), editor = SetEditor(normalSet, 1, 120.0, 12, SetType.NORMAL)),
            MachineActions(onEditorType = { picked += it }),
        )

        compose.onNodeWithText("Failure").performClick()

        assertEquals(listOf(SetType.FAILURE), picked)
    }
}

/** The same screens in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class MachineWarmupPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: MachineUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `chip on pt`() {
        setContent(warmupBase.copy(warmupOn = true))
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-warmup-on-pt.png")
    }

    @Test
    fun `labelled sets pt`() {
        setContent(withTypes)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-set-types-pt.png")
    }

    @Test
    fun `edit sheet pt`() {
        setContent(withTypes.copy(editor = SetEditor(dropSet, 3, 100.0, 10, SetType.DROP)))
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/machine-set-types-sheet-pt.png")
    }
}

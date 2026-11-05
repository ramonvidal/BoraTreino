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

private val calcPlan = listOf(
    SetDraft(60.0, 8, SetType.WARMUP),
    SetDraft(80.0, 5, SetType.WARMUP),
    SetDraft(100.0, 3, SetType.WARMUP),
)

private val calcBase = MachineUiState(
    loaded = true,
    machine = TodaySamples.legPress,
    lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
    lastTimeAt = TodaySamples.lastVisit,
    sets = emptyList(),
    weightKg = 120.0,
    reps = 12,
    sameAgain = SetDraft(120.0, 12),
    warmupPlan = calcPlan,
)

private val calcOffered = calcBase
private val calcNotOffered = calcBase.copy(warmupPlan = emptyList())
private val calcDialog = calcBase.copy(warmupDialog = true)

/** The "Warm-up sets" button next to the Warm-up chip and its dialog; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineWarmupCalcScreenshotTest {
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

    private fun shootDialog(name: String) = compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/$name.png")

    // The button

    @Test
    fun `button next to the chip`() {
        setContent(calcOffered)
        compose.onNodeWithText("Warm-up").assertExists()
        compose.onNodeWithText("Warm-up sets").assertExists()
        shoot("machine-warmup-calc")
    }

    @Test
    fun `button dark`() {
        setContent(calcOffered, dark = true)
        shoot("machine-warmup-calc-dark")
    }

    @Test
    fun `button large font`() {
        setContent(calcOffered, fontScale = 1.8f)
        compose.onNodeWithText("Warm-up sets").assertExists()
        compose.onNodeWithText("Log set").assertExists()
        shoot("machine-warmup-calc-large-font")
    }

    @Test
    fun `no button without a plan`() {
        setContent(calcNotOffered)
        compose.onNodeWithText("Warm-up").assertExists()
        compose.onAllNodesWithText("Warm-up sets").assertCountEquals(0)
        shoot("machine-warmup-calc-none")
    }

    @Test
    fun `no button without a plan dark`() {
        setContent(calcNotOffered, dark = true)
        shoot("machine-warmup-calc-none-dark")
    }

    @Test
    fun `tapping the button asks to open the dialog`() {
        var opened = 0
        setContent(calcOffered, MachineActions(onOpenWarmups = { opened++ }))

        compose.onNodeWithText("Warm-up sets").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `the dialog is not shown until asked`() {
        setContent(calcOffered)

        compose.onAllNodesWithText("Log warm-ups").assertCountEquals(0)
    }

    // The dialog

    @Test
    fun `dialog lists a line per set with Log warm-ups and Close`() {
        setContent(calcDialog)

        compose.onNodeWithText("50 kg × 8").assertDoesNotExist()
        compose.onNodeWithText("60 kg × 8").assertExists()
        compose.onNodeWithText("80 kg × 5").assertExists()
        compose.onNodeWithText("100 kg × 3").assertExists()
        compose.onNodeWithText("Log warm-ups").assertExists()
        compose.onNodeWithText("Close").assertExists()
        shootDialog("machine-warmup-dialog")
    }

    @Test
    fun `dialog dark`() {
        setContent(calcDialog, dark = true)
        shootDialog("machine-warmup-dialog-dark")
    }

    @Test
    fun `dialog large font`() {
        setContent(calcDialog, fontScale = 1.8f)
        compose.onNodeWithText("Log warm-ups").assertExists()
        shootDialog("machine-warmup-dialog-large-font")
    }

    @Test
    fun `Log warm-ups calls back once`() {
        var logged = 0
        var closed = 0
        setContent(calcDialog, MachineActions(onLogWarmups = { logged++ }, onCloseWarmups = { closed++ }))

        compose.onNodeWithText("Log warm-ups").performClick()

        assertEquals(1, logged)
        assertEquals(0, closed)
    }

    @Test
    fun `Close calls back once and logs nothing`() {
        var logged = 0
        var closed = 0
        setContent(calcDialog, MachineActions(onLogWarmups = { logged++ }, onCloseWarmups = { closed++ }))

        compose.onNodeWithText("Close").performClick()

        assertEquals(0, logged)
        assertEquals(1, closed)
    }
}

/** The same screens in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class MachineWarmupCalcPtScreenshotTest {
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
    fun `button pt`() {
        setContent(calcOffered)
        compose.onNodeWithText("Séries de aquecimento").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-warmup-calc-pt.png")
    }

    @Test
    fun `dialog pt`() {
        setContent(calcDialog)
        compose.onNodeWithText("Registrar aquecimentos").assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/machine-warmup-dialog-pt.png")
    }
}

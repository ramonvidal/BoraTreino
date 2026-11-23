package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** Phase 43: the optional "Scan bike screen" button in the cardio panel. It opens a menu; it never logs. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineScanButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private val bike = Machine(
        id = 22L,
        name = "Bike",
        muscleGroup = MuscleGroup.CARDIO,
        kind = MachineKind.CARDIO,
        stepKg = 1.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun state(scanning: Boolean = false) = MachineUiState(
        loaded = true,
        machine = bike,
        lastTime = null,
        lastTimeAt = Instant.parse("2026-10-24T18:00:00Z"),
        sets = emptyList(),
        cardio = CardioDraft(20, 0, 0),
        cardioSameAgain = null,
        cardioDistanceShown = false,
        scanning = scanning,
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

    private fun tapScan() {
        compose.onNodeWithText("Scan bike screen").performClick()
    }

    @Test
    fun `the cardio panel has an enabled Scan bike screen button`() {
        show(state())

        compose.onNodeWithText("Scan bike screen").assertIsEnabled()
    }

    @Test
    fun `tapping Scan bike screen offers Take photo and Choose photo`() {
        show(state())

        tapScan()

        compose.onAllNodesWithText("Take photo").onFirst().assertExists()
        compose.onAllNodesWithText("Choose photo").onFirst().assertExists()
    }

    @Test
    fun `while a photo is being read the button is disabled and offers no menu`() {
        show(state(scanning = true))

        compose.onNodeWithText("Scan bike screen").assertIsNotEnabled()
        tapScan()

        compose.onAllNodesWithText("Take photo").assertCountEquals(0)
        compose.onAllNodesWithText("Choose photo").assertCountEquals(0)
    }

    @Test
    fun `the failure message is the wording from the spec`() {
        val message = RuntimeEnvironment.getApplication().getString(R.string.machine_scan_failed)

        assertEquals("Couldn't read the screen, try again closer", message)
    }
}

package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Long-pressing a logged set row in the machine screen's set table opens Edit and Delete. Edit opens the set
 * editor like a tap; Delete asks to delete that set at once. A tap still opens the editor.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineSetMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val legPress = TodaySamples.legPress
    private val first: WorkoutSet = TodaySamples.set(1L, legPress, 0, 120.0, 12, Instant.parse("2026-11-09T18:12:00Z"))
    private val second: WorkoutSet = TodaySamples.set(2L, legPress, 1, 110.0, 10, Instant.parse("2026-11-09T18:14:00Z"))

    private fun state() = MachineUiState(
        loaded = true,
        machine = legPress,
        lastTime = null,
        lastTimeAt = null,
        lastTimeSets = emptyList(),
        sets = listOf(first, second),
        weightKg = 125.0,
        reps = 8,
        sameAgain = null,
        restSeconds = null,
        restTotalSeconds = 0,
    )

    private fun setMachine(actions: MachineActions) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state(), actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun setRow(text: String) = compose.onNode(hasClickAction() and hasText(text))

    @Test
    fun `long press on a logged set shows Edit and Delete`() {
        setMachine(MachineActions())

        setRow("110 kg × 10").performTouchInput { longClick() }

        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-set-menu.png")
    }

    @Test
    fun `Delete asks to delete that set at once`() {
        val deleted = mutableListOf<WorkoutSet>()
        setMachine(MachineActions(onDeleteSet = { deleted += it }))
        setRow("110 kg × 10").performTouchInput { longClick() }

        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf(second), deleted)
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `Edit in the menu opens the set editor for that set`() {
        val opened = mutableListOf<WorkoutSet>()
        setMachine(MachineActions(onOpenSet = { opened += it }))
        setRow("110 kg × 10").performTouchInput { longClick() }

        compose.onNodeWithText("Edit").performClick()

        assertEquals(listOf(second), opened)
    }

    @Test
    fun `a tap on a logged set still opens the editor`() {
        val opened = mutableListOf<WorkoutSet>()
        val deleted = mutableListOf<WorkoutSet>()
        setMachine(MachineActions(onOpenSet = { opened += it }, onDeleteSet = { deleted += it }))

        setRow("120 kg × 12").performClick()

        assertEquals(listOf(first), opened)
        assertEquals(emptyList<WorkoutSet>(), deleted)
    }
}

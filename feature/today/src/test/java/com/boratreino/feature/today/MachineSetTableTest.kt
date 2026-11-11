package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The machine screen's set table (Set | Previous | Today), the next row, the rest bar and the
 * logging panel, checked through what they show and what their taps report.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineSetTableTest {
    @get:Rule
    val compose = createComposeRule()

    private val legPress = TodaySamples.legPress

    /** Last time: the first set was 100 kg x 10, the second 110 kg x 8. */
    private val lastTime: List<WorkoutSet> = listOf(
        TodaySamples.set(10L, legPress, 0, 100.0, 10, Instant.parse("2026-10-24T18:10:00Z")),
        TodaySamples.set(11L, legPress, 1, 110.0, 8, Instant.parse("2026-10-24T18:12:00Z")),
    )

    /** This session: one set, 120 kg x 12. */
    private val today: List<WorkoutSet> = listOf(
        TodaySamples.set(1L, legPress, 0, 120.0, 12, Instant.parse("2026-11-09T18:12:00Z")),
    )

    private fun state(
        sets: List<WorkoutSet> = today,
        previous: List<WorkoutSet> = lastTime,
        weightKg: Double = 125.0,
        reps: Int = 8,
        restSeconds: Int? = null,
    ) = MachineUiState(
        loaded = true,
        machine = legPress,
        lastTime = null,
        lastTimeAt = null,
        lastTimeSets = previous,
        sets = sets,
        weightKg = weightKg,
        reps = reps,
        sameAgain = null,
        restSeconds = restSeconds,
        restTotalSeconds = if (restSeconds == null) 0 else 90,
    )

    private fun setContent(
        state: MachineUiState,
        actions: MachineActions = MachineActions(),
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            BoraTreinoTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        MachineContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    @Test
    fun `the table has Set, Previous and Today headers`() {
        setContent(state())

        compose.onNodeWithText("Set").assertExists()
        compose.onNodeWithText("Previous").assertExists()
        compose.onNodeWithText("Today").assertExists()
    }

    @Test
    fun `previous shows the set at the same index from last time`() {
        setContent(state())

        // Row 1: its previous is last time's first set, its today is this session's set.
        compose.onNode(hasClickAction() and hasText("1") and hasText("100 kg × 10")).assertExists()
        compose.onNode(hasClickAction() and hasText("1") and hasText("120 kg × 12")).assertExists()
    }

    @Test
    fun `the next row previews last time's set for that number`() {
        setContent(state())

        // Row 2 is the set about to be logged: last time's second set beside the stepper values.
        compose.onNodeWithText("110 kg × 8").assertExists()
        compose.onNodeWithText("125 kg × 8").assertExists()
    }

    @Test
    fun `no last time shows a dash in every previous cell`() {
        setContent(state(previous = emptyList()))

        // One logged set and the next row.
        compose.onAllNodesWithText("-").assertCountEquals(2)
    }

    @Test
    fun `the next row shows the stepper values in a single cell`() {
        setContent(state(weightKg = 125.0, reps = 8))

        compose.onAllNodesWithText("125 kg × 8").assertCountEquals(1)
    }

    @Test
    fun `tapping a logged row opens its editor`() {
        var opened: WorkoutSet? = null
        setContent(state(), MachineActions(onOpenSet = { opened = it }))

        compose.onNode(hasClickAction() and hasText("120 kg × 12")).performClick()

        assertEquals(1L, opened?.id)
    }

    @Test
    fun `tapping the weight number asks for the weight pad`() {
        var pad: PadTarget? = null
        setContent(state(), MachineActions(onOpenPad = { pad = it }))

        compose.onNode(hasClickAction() and hasText("125")).performClick()

        assertEquals(PadTarget.WEIGHT, pad)
    }

    @Test
    fun `tapping the reps number asks for the reps pad`() {
        var pad: PadTarget? = null
        setContent(state(), MachineActions(onOpenPad = { pad = it }))

        compose.onNode(hasClickAction() and hasText("8")).performClick()

        assertEquals(PadTarget.REPS, pad)
    }

    @Test
    fun `the Log button is one tap and reports once`() {
        var logged = 0
        setContent(state(), MachineActions(onLogSet = { logged++ }))

        compose.onNodeWithText("Log 125 kg x 8").performClick()

        assertEquals(1, logged)
    }

    @Test
    fun `a running rest shows its time with +30 s and Skip`() {
        var extended = 0
        var skipped = 0
        setContent(
            state(restSeconds = 72),
            MachineActions(onExtendRest = { extended++ }, onSkipRest = { skipped++ }),
        )

        compose.onNodeWithText("Rest 1:12").assertExists()
        compose.onNodeWithText("+30 s").performClick()
        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, extended)
        assertEquals(1, skipped)
    }

    @Test
    fun `at font scale 2 the Log button and both steppers are still shown`() {
        setContent(state(), fontScale = 2f)

        compose.onNodeWithText("Log 125 kg x 8").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Increase", substring = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Decrease", substring = true).assertCountEquals(2)
    }

    @Test
    fun `the logging table shot`() {
        setContent(state(restSeconds = 72))

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-set-table.png")
    }

    @Test
    fun `the logging table shot at font scale 2`() {
        setContent(state(), fontScale = 2f)

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-set-table-large.png")
    }
}

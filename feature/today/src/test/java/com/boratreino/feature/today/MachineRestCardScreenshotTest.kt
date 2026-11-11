package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetSummary
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

private fun machineState(restSeconds: Int?, total: Int) = MachineUiState(
    loaded = true,
    machine = TodaySamples.legPress,
    lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
    lastTimeAt = TodaySamples.lastVisit,
    sets = listOf(
        TodaySamples.set(1, TodaySamples.legPress, 0, 120.0, 12, Instant.parse("2026-10-31T18:12:00Z")),
    ),
    weightKg = 120.0,
    reps = 11,
    sameAgain = SetDraft(120.0, 12),
    restSeconds = restSeconds,
    restTotalSeconds = total,
)

/** The exercise screen with the rest bar above the steppers in the bottom panel; without a rest there is no bar. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineRestCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: MachineUiState, dark: Boolean = false, actions: MachineActions = MachineActions()) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `a running rest shows the slim bar with the time, +30 s and Skip`() {
        shoot("machine-rest-card", machineState(restSeconds = 72, total = 90))

        compose.onNodeWithText("Rest 1:12").assertExists()
        compose.onNodeWithText("+30 s").assertExists()
        compose.onNodeWithText("Skip").assertExists()
        compose.onNodeWithText("Log 120 kg x 11").assertExists()
        compose.onNodeWithText("Same again").assertDoesNotExist()
    }

    @Test
    fun `a rest that just started`() {
        shoot("machine-rest-card-start", machineState(restSeconds = 90, total = 90))

        compose.onNodeWithText("Rest 1:30").assertExists()
    }

    @Test
    fun `a rest almost over`() {
        shoot("machine-rest-card-end", machineState(restSeconds = 3, total = 90))

        compose.onNodeWithText("Rest 0:03").assertExists()
    }

    @Test
    fun `a running rest dark`() = shoot("machine-rest-card-dark", machineState(restSeconds = 72, total = 90), dark = true)

    @Test
    fun `no rest keeps the old layout`() {
        shoot("machine-rest-card-none", machineState(restSeconds = null, total = 0))

        compose.onNodeWithText("Rest", substring = true).assertDoesNotExist()
        compose.onNodeWithText("+30 s").assertDoesNotExist()
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Log 120 kg x 11").assertExists()
        compose.onNodeWithText("Same again").assertDoesNotExist()
    }

    @Test
    fun `the card buttons extend and skip the rest`() {
        var extended = 0
        var skipped = 0
        shoot(
            "machine-rest-card-actions",
            machineState(restSeconds = 72, total = 90),
            actions = MachineActions(onExtendRest = { extended++ }, onSkipRest = { skipped++ }),
        )

        compose.onNodeWithText("+30 s").performClick()
        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, extended)
        assertEquals(1, skipped)
    }

    @Test
    fun `logging stays one tap with the card showing`() {
        var logged = 0
        shoot(
            "machine-rest-card-log",
            machineState(restSeconds = 72, total = 90),
            actions = MachineActions(onLogSet = { logged++ }),
        )

        compose.onNodeWithText("Log 120 kg x 11").performClick()

        assertEquals(1, logged)
    }
}

/** The machine screen with the rest card in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class MachineRestCardPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `rest card pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(
                        state = machineState(restSeconds = 72, total = 90),
                        actions = MachineActions(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-rest-card-pt.png")
    }
}

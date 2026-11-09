package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.Record
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.Suggestion
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** The machine screen: last time, this session's sets and the logging controls; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: MachineUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val lastVisit: Instant = TodaySamples.lastVisit

    @Test
    fun `logging with last time, two sets and same again`() = shoot(
        "machine-logging",
        MachineUiState(
            loaded = true,
            machine = TodaySamples.legPress,
            lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
            lastTimeAt = lastVisit,
            sets = listOf(
                TodaySamples.set(1, TodaySamples.legPress, 0, 120.0, 12, Instant.parse("2026-10-31T18:12:00Z")),
                TodaySamples.set(2, TodaySamples.legPress, 1, 120.0, 11, Instant.parse("2026-10-31T18:14:00Z")),
            ),
            weightKg = 120.0,
            reps = 11,
            sameAgain = SetDraft(120.0, 12),
        ),
    )

    @Test
    fun `bodyweight machine with no added weight`() = shoot(
        "machine-bodyweight",
        MachineUiState(
            loaded = true,
            machine = TodaySamples.pullUps,
            lastTime = SetSummary.Uniform(count = 3, weightKg = 0.0, reps = 8),
            lastTimeAt = lastVisit,
            sets = listOf(
                TodaySamples.set(3, TodaySamples.pullUps, 0, 0.0, 10, Instant.parse("2026-10-31T18:20:00Z")),
                TodaySamples.set(4, TodaySamples.pullUps, 1, 0.0, 8, Instant.parse("2026-10-31T18:22:00Z")),
            ),
            weightKg = 0.0,
            reps = 8,
            sameAgain = SetDraft(0.0, 8),
        ),
    )

    @Test
    fun `first time on a machine`() = shoot(
        "machine-first-time",
        MachineUiState(
            loaded = true,
            machine = TodaySamples.chestPress,
            lastTime = null,
            lastTimeAt = null,
            sets = emptyList(),
            weightKg = 0.0,
            reps = 10,
            sameAgain = null,
        ),
    )

    private val legPressTarget = RoutineItem(routineId = 2L, machineId = TodaySamples.legPress.id, position = 0, targetSets = 3, repsMin = 8, repsMax = 12)

    @Test
    fun `target and an up suggestion`() = shoot(
        "machine-suggestion",
        MachineUiState(
            loaded = true,
            machine = TodaySamples.legPress,
            lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
            lastTimeAt = lastVisit,
            sets = emptyList(),
            weightKg = 125.0,
            reps = 8,
            sameAgain = SetDraft(120.0, 12),
            target = legPressTarget,
            suggestion = Suggestion.Up(125.0, 8),
        ),
    )

    @Test
    fun `target and a down suggestion after missing the bottom twice`() = shoot(
        "machine-suggestion-down",
        MachineUiState(
            loaded = true,
            machine = TodaySamples.legPress,
            lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 8),
            lastTimeAt = lastVisit,
            sets = emptyList(),
            weightKg = 115.0,
            reps = 8,
            sameAgain = SetDraft(120.0, 8),
            target = legPressTarget,
            suggestion = Suggestion.Down(115.0, 8),
        ),
    )

    @Test
    fun `a heaviest-weight record shown after the set`() = shoot(
        "machine-record",
        MachineUiState(
            loaded = true,
            machine = TodaySamples.legPress,
            lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
            lastTimeAt = lastVisit,
            sets = listOf(
                TodaySamples.set(5, TodaySamples.legPress, 0, 125.0, 8, Instant.parse("2026-10-31T18:12:00Z")),
            ),
            weightKg = 125.0,
            reps = 8,
            sameAgain = SetDraft(120.0, 12),
            target = legPressTarget,
            record = Record.HeaviestWeight(125.0),
        ),
    )

    @Test
    fun `a most-reps record shown after the set`() = shoot(
        "machine-record-reps",
        MachineUiState(
            loaded = true,
            machine = TodaySamples.legPress,
            lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 10),
            lastTimeAt = lastVisit,
            sets = listOf(
                TodaySamples.set(6, TodaySamples.legPress, 0, 120.0, 12, Instant.parse("2026-10-31T18:12:00Z")),
            ),
            weightKg = 120.0,
            reps = 12,
            sameAgain = SetDraft(120.0, 10),
            target = legPressTarget,
            record = Record.MostReps(120.0, 12),
        ),
    )

    /** A set was just logged: the rest card with 1:12 left sits above the steppers. */
    @Test
    fun `rest running after a set`() {
        shoot(
            "machine-rest",
            MachineUiState(
                loaded = true,
                machine = TodaySamples.legPress,
                lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
                lastTimeAt = lastVisit,
                sets = listOf(
                    TodaySamples.set(1, TodaySamples.legPress, 0, 120.0, 12, Instant.parse("2026-10-31T18:12:00Z")),
                ),
                weightKg = 120.0,
                reps = 11,
                sameAgain = SetDraft(120.0, 12),
                restSeconds = 72,
                restTotalSeconds = 90,
            ),
        )
        compose.onNodeWithText("Rest 1:12").assertExists()
    }
}

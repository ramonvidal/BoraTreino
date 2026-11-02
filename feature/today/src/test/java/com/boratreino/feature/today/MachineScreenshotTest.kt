package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetSummary
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
}

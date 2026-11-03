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

/** The machine screen with "same again" and the rest card, in Baianês; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class MachinePtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `machine logging with same again and rest pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineContent(
                        state = MachineUiState(
                            loaded = true,
                            machine = TodaySamples.legPress,
                            lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
                            lastTimeAt = TodaySamples.lastVisit,
                            sets = listOf(
                                TodaySamples.set(1, TodaySamples.legPress, 0, 120.0, 12, Instant.parse("2026-10-31T18:12:00Z")),
                                TodaySamples.set(2, TodaySamples.legPress, 1, 120.0, 11, Instant.parse("2026-10-31T18:14:00Z")),
                            ),
                            weightKg = 120.0,
                            reps = 11,
                            sameAgain = SetDraft(120.0, 12),
                            restSeconds = 72,
                            restTotalSeconds = 90,
                        ),
                        actions = MachineActions(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/machine-logging-pt.png")
    }
}

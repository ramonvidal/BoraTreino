package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
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

/** Play listing screenshots in English: Today (1) and the machine logging screen (2). Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xxhdpi")
class TodayStoreScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** Light theme on the app background at normal text size, the way the listing shows it. */
    @Composable
    private fun StoreFrame(content: @Composable () -> Unit) {
        BoraTreinoTheme(darkTheme = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { StoreFrame(content) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `store 1 today`() {
        shoot("store-en-1-today") {
            TodayContent(state = TodaySamples.routineBInProgress, actions = TodayActions(), modifier = Modifier.fillMaxSize())
        }
    }

    /** Leg press, routine B's target 3 x 8-12, the set just logged was 120 kg x 12, so the next suggestion is up. */
    private val legPressTarget = RoutineItem(
        routineId = 2L,
        machineId = TodaySamples.legPress.id,
        position = 0,
        targetSets = 3,
        repsMin = 8,
        repsMax = 12,
    )

    @Test
    fun `store 2 machine`() {
        val state = MachineUiState(
            loaded = true,
            machine = TodaySamples.legPress,
            lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
            lastTimeAt = Instant.parse("2026-10-24T18:00:00Z"),
            sets = listOf(
                TodaySamples.set(1, TodaySamples.legPress, 0, 120.0, 12, Instant.parse("2026-10-31T18:12:00Z")),
            ),
            weightKg = 125.0,
            reps = 8,
            sameAgain = SetDraft(120.0, 12),
            target = legPressTarget,
            suggestion = Suggestion.Up(125.0, 8),
            restSeconds = 72,
            restTotalSeconds = 90,
        )
        shoot("store-en-2-machine") {
            MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
        }
        compose.onNodeWithText("Rest 1:12").assertExists()
    }
}

package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetSummary
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val cardioTreadmill = Machine(
    id = 20L,
    name = "Treadmill",
    muscleGroup = MuscleGroup.CARDIO,
    kind = MachineKind.CARDIO,
    stepKg = 1.0,
    setupNote = null,
    restSeconds = null,
    archived = false,
)

/** Routine B with a treadmill at the end. The cardio row has no sets x reps target; done ones show their totals. */
private fun routineWithCardio(summary: SetSummary?): TodayUiState {
    val base = TodaySamples.routineBInProgress
    val routines = checkNotNull(base.routines)
    val item = RoutineItem(routineId = 2L, machineId = cardioTreadmill.id, position = 4, targetSets = 1, repsMin = 1, repsMax = 1)
    return base.copy(
        routines = routines.copy(
            machines = routines.machines + RoutineMachineUi(
                machine = cardioTreadmill,
                target = item,
                setCount = if (summary == null) 0 else 1,
                done = summary != null,
                highlighted = false,
                canSkip = summary == null,
                cardioSummary = summary,
            ),
        ),
    )
}

/** The ficha (routine) row of a cardio machine on the Today tab; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayCardioScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: TodayUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = TodayActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `cardio row not done yet`() {
        shoot("today-routine-cardio", routineWithCardio(null))
        compose.onNodeWithText("Treadmill").assertExists()
    }

    @Test
    fun `cardio row after logging shows the totals`() {
        shoot(
            "today-routine-cardio-done",
            routineWithCardio(SetSummary.Cardio(count = 1, minutes = 30, calories = 250, distanceM = 3200)),
        )
        compose.onNodeWithText("30 min · 250 kcal · 3.2 km").assertExists()
    }

    @Test
    fun `cardio row done in dark`() = shoot(
        "today-routine-cardio-dark",
        routineWithCardio(SetSummary.Cardio(count = 1, minutes = 30, calories = 250, distanceM = 0)),
        dark = true,
    )
}

/** The same ficha row in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class TodayCardioPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `cardio row done pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(
                        state = routineWithCardio(SetSummary.Cardio(count = 1, minutes = 30, calories = 250, distanceM = 3200)),
                        actions = TodayActions(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-routine-cardio-pt.png")
    }
}

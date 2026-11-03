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
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.Suggestion
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** Play listing screenshots in Portuguese: Today (1) and the machine logging screen (2). Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w360dp-h720dp-xxhdpi")
class TodayStoreScreenshotPtTest {
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

    // Portuguese gym data: the same machines as the English samples, with their Portuguese names.

    private fun machine(
        id: Long,
        name: String,
        group: MuscleGroup,
        kind: MachineKind = MachineKind.WEIGHT_REPS,
        stepKg: Double = 2.5,
        setupNote: String? = null,
    ) = Machine(
        id = id,
        name = name,
        muscleGroup = group,
        kind = kind,
        stepKg = stepKg,
        setupNote = setupNote,
        restSeconds = null,
        archived = false,
    )

    private val legPress = machine(1L, "Leg press", MuscleGroup.LEGS, stepKg = 5.0, setupNote = "Banco 4")
    private val cadeiraExtensora = machine(2L, "Cadeira extensora", MuscleGroup.LEGS)
    private val puxadaFrontal = machine(3L, "Puxada frontal", MuscleGroup.BACK, setupNote = "Almofada 3")
    private val remadaBaixa = machine(4L, "Remada baixa", MuscleGroup.BACK)
    private val supinoReto = machine(6L, "Supino reto", MuscleGroup.CHEST)

    private fun workoutSet(id: Long, machine: Machine, position: Int, weightKg: Double, reps: Int, at: Instant) = WorkoutSet(
        id = id,
        sessionId = 1L,
        machineId = machine.id,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = at,
    )

    private val treinoA = Routine(id = 1L, planId = 1L, name = "Treino A", shortName = "A", position = 0, weekdays = emptySet())
    private val treinoB = Routine(id = 2L, planId = 1L, name = "Treino B", shortName = "B", position = 1, weekdays = emptySet())
    private val treinoC = Routine(id = 3L, planId = 1L, name = "Treino C", shortName = "C", position = 2, weekdays = emptySet())

    private fun chips(selectedId: Long?) = listOf(treinoA, treinoB, treinoC).map {
        RoutineChipUi(it.id, it.shortName, selected = it.id == selectedId)
    }

    /** Treino B's machines in order: 3 sets each. */
    private val treinoBItems = listOf(
        RoutineItem(routineId = 2L, machineId = legPress.id, position = 0, targetSets = 3, repsMin = 8, repsMax = 12),
        RoutineItem(routineId = 2L, machineId = cadeiraExtensora.id, position = 1, targetSets = 3, repsMin = 10, repsMax = 15),
        RoutineItem(routineId = 2L, machineId = puxadaFrontal.id, position = 2, targetSets = 3, repsMin = 8, repsMax = 12),
        RoutineItem(routineId = 2L, machineId = supinoReto.id, position = 3, targetSets = 3, repsMin = 8, repsMax = 12),
    )

    /** Treino B in progress: Leg press done, Cadeira extensora at 2 of 3 and next, Remada baixa done outside the routine. */
    private val treinoBInProgress = TodayUiState(
        loaded = true,
        session = SessionUi(
            machineCount = 3,
            setCount = 7,
            machines = listOf(
                SessionMachineUi(remadaBaixa, SetSummary.Uniform(2, 40.0, 10)),
                SessionMachineUi(cadeiraExtensora, SetSummary.SameWeight(60.0, listOf(10, 9))),
                SessionMachineUi(legPress, SetSummary.Uniform(3, 120.0, 12)),
            ),
        ),
        recent = emptyList(),
        routines = RoutineUi(
            chips = chips(treinoB.id),
            freeSelected = false,
            activeName = treinoB.name,
            machines = listOf(
                RoutineMachineUi(legPress, treinoBItems[0], setCount = 3, done = true, highlighted = false),
                RoutineMachineUi(cadeiraExtensora, treinoBItems[1], setCount = 2, done = false, highlighted = true),
                RoutineMachineUi(puxadaFrontal, treinoBItems[2], setCount = 0, done = false, highlighted = false),
                RoutineMachineUi(supinoReto, treinoBItems[3], setCount = 0, done = false, highlighted = false),
            ),
            alsoThisSession = listOf(SessionMachineUi(remadaBaixa, SetSummary.Uniform(2, 40.0, 10))),
        ),
    )

    @Test
    fun `store 1 today`() {
        shoot("store-pt-1-today") {
            TodayContent(state = treinoBInProgress, actions = TodayActions(), modifier = Modifier.fillMaxSize())
        }
    }

    /** Leg press, Treino B's target 3 x 8-12, the set just logged was 120 kg x 12, so the next suggestion is up. */
    private val legPressTarget = RoutineItem(
        routineId = 2L,
        machineId = legPress.id,
        position = 0,
        targetSets = 3,
        repsMin = 8,
        repsMax = 12,
    )

    @Test
    fun `store 2 machine`() {
        val state = MachineUiState(
            loaded = true,
            machine = legPress,
            lastTime = SetSummary.Uniform(count = 3, weightKg = 120.0, reps = 12),
            lastTimeAt = Instant.parse("2026-10-24T18:00:00Z"),
            sets = listOf(
                workoutSet(1L, legPress, 0, 120.0, 12, Instant.parse("2026-10-31T18:12:00Z")),
            ),
            weightKg = 125.0,
            reps = 8,
            sameAgain = SetDraft(120.0, 12),
            target = legPressTarget,
            suggestion = Suggestion.Up(125.0, 8),
            restSeconds = 72,
        )
        shoot("store-pt-2-machine") {
            MachineContent(state = state, actions = MachineActions(), modifier = Modifier.fillMaxSize())
        }
        compose.onNodeWithText("Este treino").assertExists()
    }
}

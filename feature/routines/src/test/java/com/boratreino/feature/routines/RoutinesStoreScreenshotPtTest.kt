package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Play listing screenshot in Portuguese: the Routines tab with the gym and the routines below (3). Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w360dp-h720dp-xxhdpi")
class RoutinesStoreScreenshotPtTest {
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

    // Portuguese gym: the machines a Brazilian gym has, with their setup notes.

    private fun machine(
        id: Long,
        name: String,
        group: MuscleGroup,
        stepKg: Double = 2.5,
        setupNote: String? = null,
        bodyweight: Boolean = false,
    ) = Machine(
        id = id,
        name = name,
        muscleGroup = group,
        kind = if (bodyweight) MachineKind.BODYWEIGHT_REPS else MachineKind.WEIGHT_REPS,
        stepKg = stepKg,
        setupNote = setupNote,
        restSeconds = null,
        archived = false,
    )

    private val legPress = machine(1L, "Leg press", MuscleGroup.LEGS, 5.0, "Banco 4")
    private val cadeiraExtensora = machine(2L, "Cadeira extensora", MuscleGroup.LEGS)
    private val puxadaFrontal = machine(3L, "Puxada frontal", MuscleGroup.BACK, setupNote = "Almofada 3")
    private val remadaBaixa = machine(4L, "Remada baixa", MuscleGroup.BACK)
    private val barraFixa = machine(5L, "Barra fixa", MuscleGroup.BACK, bodyweight = true)
    private val supinoReto = machine(6L, "Supino reto", MuscleGroup.CHEST)
    private val desenvolvimento = machine(7L, "Desenvolvimento máquina", MuscleGroup.SHOULDERS)
    private val roscaCabo = machine(8L, "Rosca no cabo (barra reta)", MuscleGroup.ARMS)

    private val machineGroups = listOf(
        MachineGroup(MuscleGroup.LEGS, listOf(legPress, cadeiraExtensora)),
        MachineGroup(MuscleGroup.BACK, listOf(puxadaFrontal, remadaBaixa, barraFixa)),
        MachineGroup(MuscleGroup.CHEST, listOf(supinoReto)),
        MachineGroup(MuscleGroup.SHOULDERS, listOf(desenvolvimento)),
        MachineGroup(MuscleGroup.ARMS, listOf(roscaCabo)),
    )

    /** Starters the gym does not have yet, grouped by muscle group, with their Portuguese names. */
    private val gymNames = machineGroups.flatMap { it.machines }.map { it.name }.toSet()

    private val starters = StarterMachines.all
        .filter { it.namePt !in gymNames }
        .groupBy { it.muscleGroup }
        .toSortedMap()
        .map { (group, list) -> pickerGroupOf(group, list) }

    private fun routine(id: Long, name: String, shortName: String, position: Int) =
        Routine(id = id, planId = 1L, name = name, shortName = shortName, position = position, weekdays = emptySet())

    @Test
    fun `store 3 routines`() {
        val state = RoutinesUiState(
            routines = listOf(
                RoutineRow(routine(1L, "Treino A", "A", 0), machineCount = 4),
                RoutineRow(routine(2L, "Treino B", "B", 1), machineCount = 4),
                RoutineRow(routine(3L, "Treino C", "C", 2), machineCount = 3),
            ),
            scheduleMode = ScheduleMode.IN_TURN,
            machineGroups = machineGroups,
            pickerGroups = starters,
            startersExpanded = false,
            gymCount = 8,
        )
        shoot("store-pt-3-routines") {
            RoutinesContent(state = state, callbacks = RoutinesCallbacks(), modifier = Modifier.fillMaxSize())
        }
        // The tab title and the Routines segment of the switch both read "Fichas".
        compose.onAllNodesWithText("Fichas").assertCountEquals(2)
    }
}

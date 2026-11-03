package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Play listing screenshots in Portuguese: Leg press over three months (5) and the Progress overview (6). Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w360dp-h720dp-xxhdpi")
class ProgressStoreScreenshotPtTest {
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

    // Portuguese gym names on the same history as the English samples.

    private fun machine(id: Long, name: String, group: MuscleGroup, kind: MachineKind = MachineKind.WEIGHT_REPS) = Machine(
        id = id,
        name = name,
        muscleGroup = group,
        kind = kind,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val legPress = machine(1L, "Leg press", MuscleGroup.LEGS)
    private val cadeiraExtensora = machine(2L, "Cadeira extensora", MuscleGroup.LEGS)
    private val puxadaFrontal = machine(3L, "Puxada frontal", MuscleGroup.BACK)
    private val remadaBaixa = machine(4L, "Remada baixa", MuscleGroup.BACK)
    private val barraFixa = machine(5L, "Barra fixa", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS)
    private val supinoReto = machine(6L, "Supino reto", MuscleGroup.CHEST)

    @Test
    fun `store 5 machine progress`() {
        val history = MachineProgress(legPress, ProgressMetric.WEIGHT, ProgressSamples.legPressHistory.points)
        val machine = buildMachineState(
            history,
            ProgressRange.THREE_MONTHS,
            ProgressSamples.today,
            selected = null,
            showAll = false,
        )
        shoot("store-pt-5-progress-machine") {
            MachineProgressContent(state = machine, actions = MachineProgressActions(), modifier = Modifier.fillMaxSize())
        }
    }

    @Test
    fun `store 6 progress overview`() {
        val overview = ProgressOverview(
            machines = listOf(
                MachineProgress(legPress, ProgressMetric.WEIGHT, ProgressSamples.legPressHistory.points),
                MachineProgress(puxadaFrontal, ProgressMetric.WEIGHT, ProgressSamples.latPulldownHistory.points),
                MachineProgress(barraFixa, ProgressMetric.REPS, ProgressSamples.pullUpsHistory.points),
                MachineProgress(remadaBaixa, ProgressMetric.WEIGHT, ProgressSamples.seatedRowHistory.points),
                MachineProgress(supinoReto, ProgressMetric.WEIGHT, ProgressSamples.chestPressHistory.points),
                MachineProgress(cadeiraExtensora, ProgressMetric.WEIGHT, ProgressSamples.legExtensionHistory.points),
            ),
            trainingsThisMonth = 9,
            recordsThisMonth = 4,
        )
        shoot("store-pt-6-progress-overview") {
            ProgressContent(
                state = buildProgressState(overview, ProgressSamples.today),
                actions = ProgressActions(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

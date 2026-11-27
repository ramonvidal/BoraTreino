package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ConversionFactor
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The kind-change question and the time routine target; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ConvertKindScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val farmersWalk = Machine(
        id = 21L,
        name = "Farmer's walk",
        muscleGroup = MuscleGroup.ARMS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val sledPush = farmersWalk.copy(id = 22L, name = "Sled push", muscleGroup = MuscleGroup.LEGS, stepKg = 5.0)

    private val backExtension = farmersWalk.copy(
        id = 23L,
        name = "Back extension",
        muscleGroup = MuscleGroup.BACK,
        kind = MachineKind.BODYWEIGHT_REPS,
    )

    private fun repSet(machine: Machine, kg: Double, reps: Int) = WorkoutSet(
        id = 1L,
        sessionId = 20L,
        machineId = machine.id,
        position = 0,
        weightKg = kg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = Instant.parse("2026-11-02T18:20:00Z"),
    )

    private fun setDialog(dialog: ConvertDialog) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConvertKindDialog(
                        dialog = dialog,
                        onFactorStep = {},
                        onFactorSet = {},
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun `convert dialog with seconds per rep chosen`() {
        setDialog(
            ConvertDialog(
                before = farmersWalk,
                after = farmersWalk.copy(kind = MachineKind.WEIGHT_TIME),
                conversion = KindConversion.NeedsFactor(
                    setCount = 3,
                    factor = ConversionFactor.SECONDS_PER_REP,
                    default = 30,
                    sample = repSet(farmersWalk, 47.5, 1),
                ),
                factor = 30,
            ),
        )
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/convert-kind-seconds.png")
    }

    @Test
    fun `convert dialog for metres per rep, no number yet`() {
        setDialog(
            ConvertDialog(
                before = sledPush,
                after = sledPush.copy(kind = MachineKind.WEIGHT_DISTANCE),
                conversion = KindConversion.NeedsFactor(
                    setCount = 1,
                    factor = ConversionFactor.METRES_PER_REP,
                    default = null,
                    sample = repSet(sledPush, 40.0, 10),
                ),
                factor = null,
            ),
        )
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/convert-kind-metres-empty.png")
    }

    @Test
    fun `convert dialog that keeps the numbers`() {
        setDialog(
            ConvertDialog(
                before = backExtension,
                after = backExtension.copy(kind = MachineKind.WEIGHT_REPS),
                conversion = KindConversion.Unchanged(setCount = 3),
            ),
        )
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/convert-kind-unchanged.png")
    }

    @Test
    fun `routine target of a time item shows only its sets`() {
        val plank = RoutineEditorItem(
            machineName = "Plank",
            item = RoutineItem(routineId = 7L, machineId = 23L, position = 0, targetSets = 3, repsMin = 8, repsMax = 12),
            muscleGroup = MuscleGroup.CORE,
            kind = MachineKind.TIME,
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SetsRepsSheetContent(entry = plank, index = 0, callbacks = RoutineEditorCallbacks())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routine-target-time.png")
    }
}

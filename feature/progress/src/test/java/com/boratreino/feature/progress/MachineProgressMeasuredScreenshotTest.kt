package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRange
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.SetType
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The machine detail with the measure chips, on the seconds and the metres chart; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineProgressMeasuredScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)
    private val loggedAt = Instant.parse("2026-11-02T18:00:00Z")

    private val farmersWalk = Machine(
        id = 21L,
        name = "Farmer's walk",
        muscleGroup = MuscleGroup.ARMS,
        kind = MachineKind.WEIGHT_TIME,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val sledPush = farmersWalk.copy(id = 22L, name = "Sled push", muscleGroup = MuscleGroup.LEGS, kind = MachineKind.WEIGHT_DISTANCE, stepKg = 5.0)

    private fun timed(sessionId: Long, date: LocalDate, seconds: Int) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = seconds.toDouble(),
        topSet = WorkoutSet(sessionId, sessionId, farmersWalk.id, 0, 47.5, null, seconds, null, null, loggedAt, SetType.NORMAL),
        setCount = 2,
    )

    private fun weight(sessionId: Long, date: LocalDate, kg: Double) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = kg,
        topSet = WorkoutSet(sessionId, sessionId, farmersWalk.id, 0, kg, 8, null, null, null, loggedAt, SetType.NORMAL),
        setCount = 2,
    )

    private fun metres(sessionId: Long, date: LocalDate, distance: Int) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = distance.toDouble(),
        topSet = WorkoutSet(sessionId, sessionId, sledPush.id, 0, 40.0, null, null, null, distance, loggedAt, SetType.NORMAL),
        setCount = 2,
    )

    private val farmersProgress = MachineProgress(
        machine = farmersWalk,
        metric = ProgressMetric.WEIGHT,
        points = listOf(weight(1L, LocalDate.of(2026, 10, 1), 45.0), weight(2L, LocalDate.of(2026, 10, 15), 47.5)),
        measurePoints = mapOf(
            ProgressMetric.WEIGHT to listOf(weight(1L, LocalDate.of(2026, 10, 1), 45.0), weight(2L, LocalDate.of(2026, 10, 15), 47.5)),
            ProgressMetric.SECONDS to listOf(
                timed(1L, LocalDate.of(2026, 10, 1), 30),
                timed(2L, LocalDate.of(2026, 10, 15), 40),
                timed(3L, LocalDate.of(2026, 10, 29), 45),
            ),
        ),
    )

    private val sledProgress = MachineProgress(
        machine = sledPush,
        metric = ProgressMetric.WEIGHT,
        points = listOf(metres(1L, LocalDate.of(2026, 10, 1), 20)),
        measurePoints = mapOf(
            ProgressMetric.WEIGHT to listOf(metres(1L, LocalDate.of(2026, 10, 1), 20)),
            ProgressMetric.METRES to listOf(
                metres(1L, LocalDate.of(2026, 10, 1), 20),
                metres(2L, LocalDate.of(2026, 10, 25), 25),
            ),
        ),
    )

    private fun shoot(name: String, state: MachineProgressUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineProgressContent(state = state, actions = MachineProgressActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `weight and time, seconds chart with the measure chips`() = shoot(
        "machine-progress-seconds",
        buildMachineState(
            farmersProgress, ProgressRange.THREE_MONTHS, today,
            selected = null, showAll = false, cardioMetric = "SECONDS",
        ),
    )

    @Test
    fun `weight and distance, metres chart with the measure chips`() = shoot(
        "machine-progress-metres",
        buildMachineState(
            sledProgress, ProgressRange.THREE_MONTHS, today,
            selected = null, showAll = false, cardioMetric = "METRES",
        ),
    )
}

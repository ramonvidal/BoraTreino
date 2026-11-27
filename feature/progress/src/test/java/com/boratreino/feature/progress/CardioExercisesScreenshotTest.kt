package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ChartRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressRules
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** The Exercises row and the cardio detail chips; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CardioExercisesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today
    private val bike: Machine = Machine(31L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

    private fun point(value: Double, sessionId: Long) = SessionPoint(
        sessionId = sessionId,
        date = today.minusDays(7L * sessionId),
        value = value,
        topSet = WorkoutSet(sessionId, sessionId, bike.id, 0, null, null, 2700, null, 18_400, Instant.parse("2026-10-26T10:00:00Z")),
        setCount = 1,
    )

    @Test
    fun `exercises list cardio row`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProgressContent(
                        state = ProgressUiState(
                            loaded = true,
                            today = today,
                            machines = listOf(
                                MachineRow(bike, ProgressMetric.SPEED, 24.5, today.minusDays(3), listOf(21.0, 23.0, 24.5)),
                            ),
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/progress-cardio-exercises.png")
    }

    private val points = listOf(point(22.0, 2L), point(24.5, 1L))

    @Test
    fun `cardio detail with the measure chips`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineProgressContent(
                        state = MachineProgressUiState(
                            loaded = true,
                            found = true,
                            name = bike.name,
                            metric = ProgressMetric.SPEED,
                            today = today,
                            points = points,
                            best = ProgressRules.best(points),
                            change = ProgressRules.change(points),
                            scale = ChartRules.niceScale(points.minOf { it.value }, points.maxOf { it.value }),
                            axisStart = today.minusMonths(3),
                            axisEnd = today,
                            cardioMetrics = listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.DISTANCE, ProgressMetric.MINUTES),
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/progress-cardio-detail-chips.png")
    }
}

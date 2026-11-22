package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** A cardio exercise on the Exercises list and on its detail page: the row's best line, and the measure chips. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CardioExercisesTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = ProgressSamples.today
    private val treadmill: Machine = ProgressSamples.machine(30, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)

    private fun row(metric: ProgressMetric, best: Double, lastTrained: java.time.LocalDate) = MachineRow(
        machine = treadmill,
        metric = metric,
        best = best,
        lastTrained = lastTrained,
        trend = listOf(best * 0.8, best),
    )

    private fun showList(row: MachineRow, onOpen: (Long) -> Unit = {}) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProgressContent(
                        state = ProgressUiState(loaded = true, today = today, machines = listOf(row)),
                        actions = ProgressActions(onOpenMachine = onOpen),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    // The Exercises row: the default measure, its best and how long ago

    @Test
    fun `a speed row says the best speed and how long ago`() {
        showList(row(ProgressMetric.SPEED, 24.5, today.minusDays(3)))

        compose.onNodeWithText("Treadmill").assertExists()
        compose.onNodeWithText("Best 24.5 km/h · 3 days ago").assertExists()
    }

    @Test
    fun `an intensity row says the best intensity and today when trained today`() {
        showList(row(ProgressMetric.INTENSITY, 14.0, today))

        compose.onNodeWithText("Best intensity 14 · today").assertExists()
    }

    @Test
    fun `a time row says the best minutes`() {
        showList(row(ProgressMetric.MINUTES, 45.0, today.minusDays(3)))

        compose.onNodeWithText("Best 45 min · 3 days ago").assertExists()
    }

    @Test
    fun `a distance row says the best distance in km`() {
        showList(row(ProgressMetric.DISTANCE, 5.0, today))

        compose.onNodeWithText("Best 5.0 km · today").assertExists()
    }

    @Test
    fun `tapping a cardio row opens its exercise`() {
        val opened = mutableListOf<Long>()
        showList(row(ProgressMetric.SPEED, 24.5, today), onOpen = { opened += it })

        compose.onNodeWithText("Treadmill").performClick()

        assertEquals(listOf(30L), opened)
    }

    // The detail page: the measure chips

    private fun point(value: Double, sessionId: Long) = SessionPoint(
        sessionId = sessionId,
        date = today.minusDays(7L * sessionId),
        value = value,
        topSet = WorkoutSet(sessionId, sessionId, treadmill.id, 0, null, null, 1200, null, 4000, Instant.parse("2026-10-26T10:00:00Z")),
        setCount = 1,
    )

    private fun detail(metric: ProgressMetric) = MachineProgressUiState(
        loaded = true,
        found = true,
        name = treadmill.name,
        metric = metric,
        today = today,
        points = listOf(point(22.0, 2L), point(24.5, 1L)),
        cardioMetrics = listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.DISTANCE, ProgressMetric.MINUTES),
    )

    private fun showDetail(state: MachineProgressUiState, onMetric: (ProgressMetric) -> Unit = {}) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineProgressContent(
                        state = state,
                        actions = MachineProgressActions(onCardioMetric = onMetric),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun `the detail offers speed, intensity, distance and time as chips`() {
        showDetail(detail(ProgressMetric.SPEED))

        compose.onNodeWithText("Speed").assertExists()
        compose.onNodeWithText("Intensity").assertExists()
        compose.onNodeWithText("Distance").assertExists()
        compose.onNodeWithText("Time").assertExists()
    }

    @Test
    fun `tapping a chip picks its measure`() {
        val picked = mutableListOf<ProgressMetric>()
        showDetail(detail(ProgressMetric.SPEED), onMetric = { picked += it })

        compose.onNodeWithText("Intensity").performClick()
        compose.onNodeWithText("Time").performClick()

        assertEquals(listOf(ProgressMetric.INTENSITY, ProgressMetric.MINUTES), picked)
    }
}

package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The machine detail for the measured kinds: the metric chips (Weight with Time, or Weight with Distance),
 * the values read in seconds or metres, and no estimated 1RM card when no set is eligible.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class MachineProgressMeasuredTest {
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

    private val sledPush = Machine(
        id = 22L,
        name = "Sled push",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_DISTANCE,
        stepKg = 5.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    /** A session whose top set is [kg] with [seconds] (reps null): the seconds metric reads [seconds]. */
    private fun timedPoint(sessionId: Long, date: LocalDate, seconds: Int, kg: Double = 47.5) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = seconds.toDouble(),
        topSet = WorkoutSet(sessionId, sessionId, farmersWalk.id, 0, kg, null, seconds, null, null, loggedAt, SetType.NORMAL),
        setCount = 2,
    )

    /** A session whose top set is [kg] for the weight metric (reps 8). */
    private fun weightPoint(sessionId: Long, date: LocalDate, kg: Double) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = kg,
        topSet = WorkoutSet(sessionId, sessionId, farmersWalk.id, 0, kg, 8, null, null, null, loggedAt, SetType.NORMAL),
        setCount = 2,
    )

    /** A session of Sled push whose top set covers [metres] (reps null). */
    private fun distancePoint(sessionId: Long, date: LocalDate, metres: Int, kg: Double = 40.0) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = metres.toDouble(),
        topSet = WorkoutSet(sessionId, sessionId, sledPush.id, 0, kg, null, null, null, metres, loggedAt, SetType.NORMAL),
        setCount = 2,
    )

    /** Farmer's walk: the weight chart by default, with the seconds of the same sessions as the other metric. */
    private val farmersProgress = MachineProgress(
        machine = farmersWalk,
        metric = ProgressMetric.WEIGHT,
        points = listOf(
            weightPoint(1L, LocalDate.of(2026, 10, 1), 45.0),
            weightPoint(2L, LocalDate.of(2026, 10, 15), 47.5),
        ),
        measurePoints = mapOf(
            ProgressMetric.WEIGHT to listOf(
                weightPoint(1L, LocalDate.of(2026, 10, 1), 45.0),
                weightPoint(2L, LocalDate.of(2026, 10, 15), 47.5),
            ),
            ProgressMetric.SECONDS to listOf(
                timedPoint(1L, LocalDate.of(2026, 10, 1), 30),
                timedPoint(2L, LocalDate.of(2026, 10, 15), 40),
                timedPoint(3L, LocalDate.of(2026, 10, 29), 45),
            ),
        ),
    )

    /** Sled push: the weight chart by default, with the metres of the sessions as the other metric. */
    private val sledProgress = MachineProgress(
        machine = sledPush,
        metric = ProgressMetric.WEIGHT,
        points = listOf(distancePoint(1L, LocalDate.of(2026, 10, 1), 20)),
        measurePoints = mapOf(
            ProgressMetric.WEIGHT to listOf(distancePoint(1L, LocalDate.of(2026, 10, 1), 20)),
            ProgressMetric.METRES to listOf(
                distancePoint(1L, LocalDate.of(2026, 10, 1), 20),
                distancePoint(2L, LocalDate.of(2026, 10, 25), 25),
            ),
        ),
    )

    private fun setContent(state: MachineProgressUiState, actions: MachineProgressActions = MachineProgressActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    MachineProgressContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `a weight and time machine offers the Weight and Time chips, and no Distance`() {
        setContent(buildMachineState(farmersProgress, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false))

        compose.onNodeWithText("Weight").assertExists()
        compose.onNodeWithText("Time").assertExists()
        compose.onNodeWithText("Distance").assertDoesNotExist()
    }

    @Test
    fun `tapping the Time chip asks for the seconds metric`() {
        var picked: ProgressMetric? = null
        setContent(
            buildMachineState(farmersProgress, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false),
            MachineProgressActions(onCardioMetric = { picked = it }),
        )

        compose.onNodeWithText("Time").performClick()

        assertEquals(ProgressMetric.SECONDS, picked)
    }

    @Test
    fun `the seconds chart is titled in seconds and the best session reads as seconds`() {
        setContent(
            buildMachineState(
                farmersProgress, ProgressRange.THREE_MONTHS, today,
                selected = null, showAll = false, cardioMetric = "SECONDS",
            ),
        )

        compose.onNodeWithText("Time (s)").assertExists()
        compose.onAllNodesWithText("45 s")[0].assertExists()
    }

    @Test
    fun `a weight and distance machine offers the Weight and Distance chips, and no Time`() {
        setContent(buildMachineState(sledProgress, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false))

        compose.onNodeWithText("Weight").assertExists()
        compose.onNodeWithText("Distance").assertExists()
        compose.onNodeWithText("Time").assertDoesNotExist()
    }

    @Test
    fun `the distance chart is titled in metres and its values read as metres`() {
        setContent(
            buildMachineState(
                sledProgress, ProgressRange.THREE_MONTHS, today,
                selected = null, showAll = false, cardioMetric = "METRES",
            ),
        )

        compose.onNodeWithText("Distance (m)").assertExists()
        compose.onAllNodesWithText("25 m")[0].assertExists()
    }

    @Test
    fun `no estimated 1RM chips or card without eligible sets`() {
        setContent(buildMachineState(farmersProgress, ProgressRange.THREE_MONTHS, today, selected = null, showAll = false))

        compose.onNodeWithText("Est. 1RM").assertDoesNotExist()
        compose.onNodeWithText("Best estimated 1RM").assertDoesNotExist()
        compose.onNodeWithText("Top weight").assertDoesNotExist()
    }
}

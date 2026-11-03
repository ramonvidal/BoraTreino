package com.boratreino.feature.calendar

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
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Play listing screenshot in Portuguese: the Calendar month with the week goal (4). Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w360dp-h720dp-xxhdpi")
class CalendarStoreScreenshotPtTest {
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

    // Portuguese gym data for November 2026, Monday first, in Sao Paulo time.

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 11, 4)
    private val monday = LocalDate.of(2026, 11, 2)

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    private fun routine(id: Long, letter: String, position: Int) =
        Routine(id = id, planId = 1L, name = "Treino $letter", shortName = letter, position = position, weekdays = emptySet())

    private val treinoA = routine(1L, "A", 0)
    private val treinoB = routine(2L, "B", 1)
    private val treinoC = routine(3L, "C", 2)
    private val routines = listOf(treinoA, treinoB, treinoC)

    private fun machine(id: Long, name: String, group: MuscleGroup, step: Double = 2.5) =
        Machine(id, name, group, MachineKind.WEIGHT_REPS, step, null, null, archived = false)

    private val legPress = machine(1L, "Leg press", MuscleGroup.LEGS, step = 5.0)
    private val remadaBaixa = machine(2L, "Remada baixa", MuscleGroup.BACK)
    private val supinoReto = machine(3L, "Supino reto", MuscleGroup.CHEST)

    private val sessionOnMonday = Session(
        id = 20L,
        startedAt = at(monday, 18, 5),
        endedAt = at(monday, 19, 10),
        routineId = treinoB.id,
        note = null,
    )

    private fun sets(sessionId: Long, machineId: Long, startMinute: Int, vararg rows: Pair<Double, Int>): List<WorkoutSet> =
        rows.mapIndexed { index, (weight, reps) ->
            WorkoutSet(
                id = sessionId * 100 + machineId * 10 + index,
                sessionId = sessionId,
                machineId = machineId,
                position = index,
                weightKg = weight,
                reps = reps,
                seconds = null,
                calories = null,
                distanceM = null,
                loggedAt = at(monday, 18, startMinute + index * 3),
            )
        }

    private val mondaySession = SessionDetail(
        session = sessionOnMonday,
        routine = treinoB,
        machines = listOf(
            SessionMachine(legPress, sets(20L, 1L, 5, 120.0 to 12, 120.0 to 11, 120.0 to 10)),
            SessionMachine(remadaBaixa, sets(20L, 2L, 20, 45.0 to 10, 45.0 to 10, 45.0 to 9)),
            SessionMachine(supinoReto, sets(20L, 3L, 40, 60.0 to 12, 60.0 to 12)),
        ),
    )

    /** November's badges: C on Sunday 1, B on Monday 2, A on Tuesday 3. */
    private val labels = mapOf(
        LocalDate.of(2026, 11, 1) to "C",
        monday to "B",
        LocalDate.of(2026, 11, 3) to "A",
    )

    private val monthWithTraining = CalendarUiState(
        loaded = true,
        today = today,
        zone = zone,
        firstDayOfWeek = DayOfWeek.MONDAY,
        month = YearMonth.of(2026, 11),
        selected = monday,
        weeklyGoal = 4,
        daysTrained = 2,
        streak = 5,
        dayLabels = labels,
        sessions = listOf(mondaySession),
    )

    @Test
    fun `store 4 calendar`() {
        shoot("store-pt-4-calendar") {
            CalendarContent(state = monthWithTraining, actions = CalendarActions(), modifier = Modifier.fillMaxSize())
        }
    }
}

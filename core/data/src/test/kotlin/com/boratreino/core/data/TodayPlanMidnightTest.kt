package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.TodayPlan
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Today follows the date: the plan and the choice change at midnight, and a clock that does not move does not spin. */
class TodayPlanMidnightTest {
    // Monday, 2 November 2026, one minute before midnight (UTC).
    private val lastMinute: Instant = Instant.parse("2026-11-02T23:59:00Z")
    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = LocalDate.of(2026, 11, 3)

    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var routines: RoutineRepository
    private lateinit var training: TrainingRepository
    private lateinit var machines: MachineRepository

    /** Every plan Today emitted, in order. */
    private val plans = mutableListOf<TodayPlan>()

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(lastMinute)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, false))

    /** Saves a routine with the given machines; returns its id. */
    private suspend fun routine(shortName: String, vararg machineIds: Long): Long = routines.saveRoutine(
        Routine(id = 0, planId = 0, name = "Routine $shortName", shortName = shortName, position = 0, weekdays = emptySet()),
        machineIds.mapIndexed { i, id -> RoutineItem(0, id, i, 3, 8, 12) },
    )

    /** Starts collecting Today in the background, as a screen does. */
    private fun TestScope.collectToday() {
        backgroundScope.launch { training.observeTodayPlan().collect { plans += it } }
        runCurrent()
    }

    /** Sets the clock to [newNow] and lets the wake-up after midnight run. */
    private fun TestScope.passMidnight(newNow: String) {
        clock.now = Instant.parse(newNow)
        advanceTimeBy(Duration.ofMinutes(2).toMillis())
        runCurrent()
    }

    private val latest: TodayPlan get() = plans.last()

    @Test
    fun `after midnight the plan of the new date is the suggestion`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        routines.planWorkout(monday, a)
        routines.planWorkout(tuesday, b)
        collectToday()
        assertEquals(a, latest.suggested?.id)

        passMidnight("2026-11-03T00:00:05Z")

        assertEquals(b, latest.suggested?.id)
        assertEquals(b, latest.active?.routine?.id)
        assertTrue(latest.plannedToday)
    }

    @Test
    fun `a routine finished yesterday is planned again today`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        routines.planWorkout(monday, a)
        routines.planWorkout(tuesday, a)
        collectToday()
        training.logSet(press, SetDraft(100.0, 10))
        training.finishSession() // Monday, at the last minute: A is done for Monday
        runCurrent()
        assertEquals(b, latest.suggested?.id)

        passMidnight("2026-11-03T00:00:05Z")

        assertEquals(a, latest.suggested?.id)
    }

    @Test
    fun `a routine chosen yesterday is back to the suggestion after midnight`() = runTest {
        val a = routine("A", machine("Leg press"))
        val b = routine("B", machine("Lat pulldown"))
        collectToday()
        training.chooseRoutine(RoutineChoice.Chosen(b))
        runCurrent()
        assertEquals(RoutineChoice.Chosen(b), latest.choice)

        passMidnight("2026-11-03T00:00:05Z")

        assertEquals(RoutineChoice.Suggested, latest.choice)
        assertEquals(a, latest.active?.routine?.id)
    }

    @Test
    fun `a clock that does not move keeps the choice and does not spin`() = runTest {
        routine("A", machine("Leg press"))
        val b = routine("B", machine("Lat pulldown"))
        clock.now = Instant.parse("2026-11-02T12:00:00Z")
        collectToday()
        training.chooseRoutine(RoutineChoice.Chosen(b))
        runCurrent()

        advanceUntilIdle()

        assertEquals(RoutineChoice.Chosen(b), latest.choice)
        assertEquals(b, latest.active?.routine?.id)
    }
}

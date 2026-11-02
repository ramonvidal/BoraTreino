package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Record
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.Suggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Records from logSet, the target and suggestion on a machine, and the record count at finish. */
class TrainingProgressTest {
    // Monday, 2 November 2026, evening.
    private val monday: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var routines: RoutineRepository
    private lateinit var training: TrainingRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(monday)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String, stepKg: Double = 5.0): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, stepKg, null, null, false))

    /** A routine with the machines, each 3 x 8-12 (the default target). Returns its id. */
    private suspend fun routine(shortName: String, vararg machineIds: Long): Long = routines.saveRoutine(
        Routine(id = 0, planId = 0, name = "Routine $shortName", shortName = shortName, position = 0, weekdays = emptySet()),
        machineIds.mapIndexed { i, id -> RoutineItem(0, id, i, 3, 8, 12) },
    )

    private suspend fun log(machineId: Long, weightKg: Double, reps: Int) =
        training.logSet(machineId, SetDraft(weightKg, reps))

    private suspend fun machineLog(machineId: Long) = requireNotNull(training.observeMachineLog(machineId).first())

    /** Finishes 40 minutes after the clock's time, so every set of the visit is logged before the finish. */
    private suspend fun finishLater(): SessionSummary? {
        clock.advance(Duration.ofMinutes(40))
        return training.finishSession()
    }

    /** Moves the clock to the given day at 18:00 UTC. */
    private fun onDay(daysAfterMonday: Long) {
        clock.now = monday.plus(Duration.ofDays(daysAfterMonday))
    }

    // Records

    @Test
    fun `the first set on a machine is not a record`() = runTest {
        val press = machine("Leg press")

        assertNull(log(press, 120.0, 12).record)
    }

    @Test
    fun `a heavier weight than any before is a heaviest-weight record`() = runTest {
        val press = machine("Leg press")
        log(press, 120.0, 12)
        finishLater()
        onDay(1)

        assertEquals(Record.HeaviestWeight(125.0), log(press, 125.0, 8).record)
    }

    @Test
    fun `more reps at the same weight is a most-reps record, even within one session`() = runTest {
        val press = machine("Leg press")
        log(press, 120.0, 10)

        assertEquals(Record.MostReps(120.0, 12), log(press, 120.0, 12).record)
    }

    @Test
    fun `a set equal to the best so far is not a record`() = runTest {
        val press = machine("Leg press")
        log(press, 120.0, 12)

        assertNull(log(press, 120.0, 12).record)
    }

    @Test
    fun `a record on one machine is not counted for another`() = runTest {
        val press = machine("Leg press")
        val row = machine("Seated row")
        log(press, 120.0, 12)

        assertNull(log(row, 40.0, 10).record)
    }

    @Test
    fun `the logged set is in the store with its id when the record comes back`() = runTest {
        val press = machine("Leg press")

        val logged = training.logSet(press, SetDraft(120.0, 12))

        assertTrue(logged.set.id > 0)
        assertTrue(store.allSets().any { it.id == logged.set.id && it.weightKg == 120.0 && it.reps == 12 })
    }

    // Target and suggestion

    @Test
    fun `a machine in the active routine has its target`() = runTest {
        val press = machine("Leg press")
        val row = machine("Seated row")
        val a = routine("A", press)

        val log = machineLog(press)

        assertEquals(RoutineItem(a, press, 0, 3, 8, 12), log.target)
        assertNull(machineLog(row).target)
    }

    @Test
    fun `with Free chosen no machine has a target`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        training.chooseRoutine(RoutineChoice.Free)

        assertNull(machineLog(press).target)
    }

    @Test
    fun `a machine with no history has no suggestion`() = runTest {
        val press = machine("Leg press")
        routine("A", press)

        assertNull(machineLog(press).suggestion)
    }

    @Test
    fun `after three sets at the top of the range the next visit suggests one step up`() = runTest {
        val press = machine("Leg press", stepKg = 5.0)
        routine("A", press)
        log(press, 120.0, 12)
        log(press, 120.0, 12)
        log(press, 120.0, 12)
        finishLater()
        onDay(1)

        assertEquals(Suggestion.Up(125.0, 8), machineLog(press).suggestion)
    }

    @Test
    fun `after two sessions under the bottom of the range the next visit suggests one step down`() = runTest {
        val press = machine("Leg press", stepKg = 5.0)
        routine("A", press)
        log(press, 120.0, 7)
        log(press, 120.0, 8)
        log(press, 120.0, 8)
        finishLater()
        onDay(1)
        log(press, 120.0, 8)
        log(press, 120.0, 7)
        log(press, 120.0, 8)
        finishLater()
        onDay(2)

        assertEquals(Suggestion.Down(115.0, 8), machineLog(press).suggestion)
    }

    @Test
    fun `the open session is not part of the progression history`() = runTest {
        val press = machine("Leg press", stepKg = 5.0)
        routine("A", press)
        log(press, 120.0, 12)
        log(press, 120.0, 12)
        log(press, 120.0, 12)
        finishLater()
        onDay(1)

        // Today's set is short of the top, but the suggestion still comes from last time.
        log(press, 125.0, 8)

        assertEquals(Suggestion.Up(125.0, 8), machineLog(press).suggestion)
    }

    @Test
    fun `a machine outside the routine gets no suggestion even with a full history`() = runTest {
        val press = machine("Leg press")
        val row = machine("Seated row")
        routine("A", press)
        log(row, 40.0, 12)
        log(row, 40.0, 12)
        log(row, 40.0, 12)
        finishLater()
        onDay(1)

        assertNull(machineLog(row).suggestion)
    }

    // Records and totals at finish

    @Test
    fun `finishing reports how many sets were records`() = runTest {
        val press = machine("Leg press")
        log(press, 100.0, 10)
        log(press, 100.0, 12)
        log(press, 105.0, 5)

        val summary = finishLater()

        assertEquals(2, summary?.recordCount)
        assertEquals(3, summary?.setCount)
    }

    @Test
    fun `a session with no records reports zero`() = runTest {
        val press = machine("Leg press")
        log(press, 100.0, 10)
        log(press, 100.0, 12)
        log(press, 105.0, 5)
        finishLater()
        onDay(1)
        log(press, 100.0, 12)

        assertEquals(0, finishLater()?.recordCount)
    }

    @Test
    fun `the record count compares with sets from before the session started`() = runTest {
        val press = machine("Leg press")
        log(press, 120.0, 12)
        finishLater()
        onDay(1)
        // Heavier than last time: a record in this session, against an earlier session's set.
        log(press, 125.0, 6)

        assertEquals(1, finishLater()?.recordCount)
    }

    @Test
    fun `finishing with nothing open has no summary to count`() = runTest {
        assertNull(finishLater())
    }
}

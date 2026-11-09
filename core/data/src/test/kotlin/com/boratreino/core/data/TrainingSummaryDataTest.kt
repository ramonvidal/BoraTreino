package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Record
import com.boratreino.core.model.RecordEvent
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetType
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * [TrainingRepository.trainingSummary] over the in-memory store: the previous session of the same routine,
 * the records set in the session and the open or missing sessions. Written from the public API.
 */
class TrainingSummaryDataTest {
    private val t0: Instant = Instant.parse("2026-10-30T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var training: TrainingRepository
    private lateinit var routines: RoutineRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(t0)
        routines = DefaultRoutineRepository(store)
        machines = DefaultMachineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun routine(name: String): Long {
        val planId = store.ensurePlan().id
        return routines.saveRoutine(Routine(0, planId, name, name.takeLast(1), 0, emptySet()), emptyList())
    }

    /** A session with its sets written straight to the store; [endedAt] null = open. */
    private suspend fun session(
        startedAt: Instant,
        endedAt: Instant?,
        routineId: Long?,
        sets: List<Triple<Long, Pair<Double?, Int?>, SetType>> = emptyList(),
    ): Long {
        val id = store.insertSession(Session(0, startedAt, endedAt, routineId, null))
        sets.forEachIndexed { index, (machineId, weightAndReps, type) ->
            store.insertSet(
                WorkoutSet(
                    id = 0,
                    sessionId = id,
                    machineId = machineId,
                    position = index,
                    weightKg = weightAndReps.first,
                    reps = weightAndReps.second,
                    seconds = null,
                    calories = null,
                    distanceM = null,
                    loggedAt = startedAt.plusSeconds(index.toLong()),
                    type = type,
                ),
            )
        }
        return id
    }

    private fun Duration.after(instant: Instant): Instant = instant.plus(this)

    private fun set(machineId: Long, weightKg: Double, reps: Int) = Triple(machineId, weightKg to reps, SetType.NORMAL)

    // Missing session

    @Test
    fun `an unknown session has no summary`() = runTest {
        assertNull(training.trainingSummary(999L))
    }

    // Plain numbers

    @Test
    fun `a closed session gives its duration, working sets, volume and machines`() = runTest {
        val leg = machine("Leg press")
        val row = machine("Seated row")
        val routineId = routine("Routine A")
        val id = session(
            startedAt = t0,
            endedAt = Duration.ofMinutes(62).after(t0),
            routineId = routineId,
            sets = listOf(
                set(leg, 100.0, 10),
                set(leg, 100.0, 8),
                Triple(row, 40.0 to 12, SetType.WARMUP),
                set(row, 50.0, 10),
            ),
        )

        val summary = requireNotNull(training.trainingSummary(id))

        assertEquals(id, summary.sessionId)
        assertEquals("Routine A", summary.routineName)
        assertEquals(LocalDate.of(2026, 10, 30), summary.date)
        assertEquals(62, summary.durationMinutes)
        assertEquals(3, summary.workingSets)
        assertEquals(1000.0 + 800.0 + 500.0, summary.volumeKg, 0.001)
        assertEquals(2, summary.machineCount)
    }

    @Test
    fun `an open session has a summary without a duration`() = runTest {
        val leg = machine("Leg press")
        val id = session(startedAt = t0, endedAt = null, routineId = null, sets = listOf(set(leg, 60.0, 12)))

        val summary = requireNotNull(training.trainingSummary(id))

        assertNull(summary.durationMinutes)
        assertEquals(1, summary.workingSets)
        assertEquals(720.0, summary.volumeKg, 0.001)
    }

    @Test
    fun `a free session has no routine name and no comparison`() = runTest {
        val leg = machine("Leg press")
        session(startedAt = t0.minus(Duration.ofDays(3)), endedAt = Duration.ofHours(1).after(t0.minus(Duration.ofDays(3))), routineId = null, sets = listOf(set(leg, 80.0, 10)))
        val id = session(startedAt = t0, endedAt = Duration.ofHours(1).after(t0), routineId = null, sets = listOf(set(leg, 90.0, 10)))

        val summary = requireNotNull(training.trainingSummary(id))

        assertNull(summary.routineName)
        assertNull(summary.previous)
    }

    // Previous session of the same routine

    @Test
    fun `the comparison is with the latest earlier closed session of the same routine`() = runTest {
        val leg = machine("Leg press")
        val routineA = routine("Routine A")
        val routineB = routine("Routine B")
        val hour = Duration.ofHours(1)
        // Earlier, same routine, closed: the one that should not be the latest.
        session(t0.minus(Duration.ofDays(10)), hour.after(t0.minus(Duration.ofDays(10))), routineA, listOf(set(leg, 100.0, 10)))
        // The latest earlier closed session of Routine A: two sets, volume 2000.
        session(t0.minus(Duration.ofDays(3)), hour.after(t0.minus(Duration.ofDays(3))), routineA, listOf(set(leg, 100.0, 10), set(leg, 100.0, 10)))
        // Same routine, still open: ignored.
        session(t0.minus(Duration.ofDays(1)), null, routineA, listOf(set(leg, 50.0, 10)))
        // Another routine: ignored.
        session(t0.minus(Duration.ofDays(2)), hour.after(t0.minus(Duration.ofDays(2))), routineB, listOf(set(leg, 500.0, 10)))
        // A free session: ignored.
        session(t0.minus(Duration.ofHours(36)), hour.after(t0.minus(Duration.ofHours(36))), null, listOf(set(leg, 500.0, 10)))
        // Later than the current session: ignored.
        session(t0.plus(Duration.ofDays(1)), hour.after(t0.plus(Duration.ofDays(1))), routineA, listOf(set(leg, 500.0, 10)))
        val current = session(t0, hour.after(t0), routineA, listOf(set(leg, 100.0, 12)))

        val previous = requireNotNull(requireNotNull(training.trainingSummary(current)).previous)

        assertEquals(2000.0, previous.volumeKg, 0.001)
        assertEquals(2, previous.workingSets)
        assertEquals(-40.0, previous.volumeChangePercent!!, 0.001)
        assertEquals(-1, previous.setsChange)
    }

    @Test
    fun `a routine with no earlier closed session has no comparison`() = runTest {
        val leg = machine("Leg press")
        val routineA = routine("Routine A")
        session(t0.minus(Duration.ofDays(2)), null, routineA, listOf(set(leg, 100.0, 10)))
        val current = session(t0, Duration.ofHours(1).after(t0), routineA, listOf(set(leg, 100.0, 10)))

        assertNull(requireNotNull(training.trainingSummary(current)).previous)
    }

    @Test
    fun `an open session of the routine is compared as the current one`() = runTest {
        val leg = machine("Leg press")
        val routineA = routine("Routine A")
        session(t0.minus(Duration.ofDays(2)), Duration.ofHours(1).after(t0.minus(Duration.ofDays(2))), routineA, listOf(set(leg, 100.0, 10)))
        val current = session(t0, null, routineA, listOf(set(leg, 100.0, 10), set(leg, 100.0, 10)))

        val summary = requireNotNull(training.trainingSummary(current))

        assertNull(summary.durationMinutes)
        assertEquals(1000.0, requireNotNull(summary.previous).volumeKg, 0.001)
        assertEquals(1, requireNotNull(summary.previous).setsChange)
    }

    // Records

    @Test
    fun `records set in the session are listed, judged only against earlier sessions`() = runTest {
        val leg = machine("Leg press")
        val earlierRoutine = routine("Routine A")
        session(t0.minus(Duration.ofDays(5)), Duration.ofHours(1).after(t0.minus(Duration.ofDays(5))), earlierRoutine, listOf(set(leg, 100.0, 10)))
        // A heavier set logged after this session must not count against it.
        session(t0.plus(Duration.ofDays(2)), Duration.ofHours(1).after(t0.plus(Duration.ofDays(2))), earlierRoutine, listOf(set(leg, 300.0, 1)))
        val current = session(
            t0,
            Duration.ofHours(1).after(t0),
            earlierRoutine,
            listOf(set(leg, 105.0, 5), set(leg, 105.0, 8)),
        )

        val summary = requireNotNull(training.trainingSummary(current))

        assertEquals(
            listOf(
                RecordEvent(Machine(leg, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false), LocalDate.of(2026, 10, 30), Record.MostReps(105.0, 8)),
                RecordEvent(Machine(leg, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false), LocalDate.of(2026, 10, 30), Record.HeaviestWeight(105.0)),
            ),
            summary.records,
        )
    }

    @Test
    fun `a session with no records has an empty list`() = runTest {
        val leg = machine("Leg press")
        val routineA = routine("Routine A")
        session(t0.minus(Duration.ofDays(5)), Duration.ofHours(1).after(t0.minus(Duration.ofDays(5))), routineA, listOf(set(leg, 100.0, 10)))
        val current = session(t0, Duration.ofHours(1).after(t0), routineA, listOf(set(leg, 90.0, 8)))

        assertTrue(requireNotNull(training.trainingSummary(current)).records.isEmpty())
    }
}

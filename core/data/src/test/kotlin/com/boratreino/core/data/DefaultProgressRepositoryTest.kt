package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** The progress overview and machine progress, read from the store with the Clock's month and zone. */
class DefaultProgressRepositoryTest {
    // Monday, 2 November 2026, evening (UTC).
    private val monday: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var repo: ProgressRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(monday)
        repo = DefaultProgressRepository(store, clock)
    }

    private suspend fun machine(
        name: String,
        kind: MachineKind = MachineKind.WEIGHT_REPS,
        archived: Boolean = false,
    ): Long = store.saveMachine(Machine(0, name, MuscleGroup.CHEST, kind, 2.5, null, null, archived))

    private suspend fun session(startedAt: Instant): Long =
        store.insertSession(Session(0, startedAt, startedAt.plusSeconds(3600), null, null))

    private suspend fun set(
        sessionId: Long,
        machineId: Long,
        weightKg: Double?,
        reps: Int?,
        loggedAt: Instant,
        position: Int = 0,
    ) {
        store.insertSet(WorkoutSet(0, sessionId, machineId, position, weightKg, reps, null, null, null, loggedAt))
    }

    /** Waits for an emission that satisfies [wanted]; the flow may emit more than once per write. */
    private suspend fun <T> Channel<T>.receiveUntil(wanted: (T) -> Boolean): T {
        while (true) {
            val value = receive()
            if (wanted(value)) return value
        }
    }

    // observeOverview

    @Test
    fun `machines without sessions are left out of the overview`() = runTest {
        val squat = machine("Squat")
        machine("Never trained")
        val s = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(s, squat, 100.0, 8, Instant.parse("2026-11-02T10:05:00Z"))

        val overview = repo.observeOverview().first()

        assertEquals(listOf("Squat"), overview.machines.map { it.machine.name })
    }

    @Test
    fun `machines are sorted last trained first and ties by name ignoring case`() = runTest {
        val squat = machine("Squat")
        val bench = machine("Bench press")
        val adductor = machine("adductor")
        val zeta = machine("Zeta")
        val oct = session(Instant.parse("2026-10-10T10:00:00Z"))
        val nov1 = session(Instant.parse("2026-11-01T10:00:00Z"))
        val nov2 = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(oct, squat, 100.0, 8, Instant.parse("2026-10-10T10:05:00Z"))
        set(nov1, bench, 60.0, 10, Instant.parse("2026-11-01T10:05:00Z"))
        set(nov1, adductor, 40.0, 12, Instant.parse("2026-11-01T10:06:00Z"))
        set(nov2, zeta, 20.0, 15, Instant.parse("2026-11-02T10:05:00Z"))

        val overview = repo.observeOverview().first()

        assertEquals(listOf("Zeta", "adductor", "Bench press", "Squat"), overview.machines.map { it.machine.name })
    }

    @Test
    fun `archived machines with history are included`() = runTest {
        val old = machine("Old machine", archived = true)
        val s = session(Instant.parse("2026-10-20T10:00:00Z"))
        set(s, old, 50.0, 10, Instant.parse("2026-10-20T10:05:00Z"))
        machine("Archived, never used", archived = true)

        val overview = repo.observeOverview().first()

        assertEquals(listOf("Old machine"), overview.machines.map { it.machine.name })
        assertTrue(overview.machines.single().machine.archived)
    }

    @Test
    fun `an empty store gives an empty overview with zero counts`() = runTest {
        val overview = repo.observeOverview().first()

        assertTrue(overview.machines.isEmpty())
        assertEquals(0, overview.trainingsThisMonth)
        assertEquals(0, overview.recordsThisMonth)
    }

    @Test
    fun `trainings and records this month are counted`() = runTest {
        val squat = machine("Squat")
        val october = session(Instant.parse("2026-10-15T10:00:00Z"))
        set(october, squat, 100.0, 8, Instant.parse("2026-10-15T10:05:00Z"))
        val first = session(Instant.parse("2026-11-01T10:00:00Z"))
        set(first, squat, 110.0, 8, Instant.parse("2026-11-01T10:05:00Z"))
        val second = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(second, squat, 110.0, 6, Instant.parse("2026-11-02T10:05:00Z"))

        val overview = repo.observeOverview().first()

        assertEquals(2, overview.trainingsThisMonth)
        assertEquals(1, overview.recordsThisMonth)
    }

    @Test
    fun `a session at 23 30 on the first counts in that month in the clock's zone`() = runTest {
        // 23:30 on 1 November in São Paulo (UTC-3) is 02:30 UTC on 2 November.
        clock = TestClock(monday, ZoneId.of("America/Sao_Paulo"))
        repo = DefaultProgressRepository(store, clock)
        val squat = machine("Squat")
        val previous = session(Instant.parse("2026-10-15T15:00:00Z"))
        set(previous, squat, 100.0, 8, Instant.parse("2026-10-15T15:05:00Z"))
        val lateFirst = session(Instant.parse("2026-11-02T02:30:00Z"))
        set(lateFirst, squat, 110.0, 8, Instant.parse("2026-11-02T02:35:00Z"))

        val overview = repo.observeOverview().first()

        assertEquals(1, overview.trainingsThisMonth)
        assertEquals(1, overview.recordsThisMonth)
        assertEquals(LocalDate.of(2026, 11, 1), overview.machines.single().points.last().date)
    }

    @Test
    fun `a session at 23 30 on the last day of the month counts in the previous month in the clock's zone`() = runTest {
        // 23:30 on 31 October in São Paulo is 02:30 UTC on 1 November.
        clock = TestClock(monday, ZoneId.of("America/Sao_Paulo"))
        repo = DefaultProgressRepository(store, clock)
        val squat = machine("Squat")
        // The 110 kg set is a record in October, so it must not count for November.
        val lateOctober = session(Instant.parse("2026-11-01T02:30:00Z"))
        set(lateOctober, squat, 100.0, 8, Instant.parse("2026-11-01T02:35:00Z"))
        set(lateOctober, squat, 110.0, 8, Instant.parse("2026-11-01T02:40:00Z"), position = 1)
        val firstNovember = session(Instant.parse("2026-11-02T02:30:00Z"))
        set(firstNovember, squat, 105.0, 8, Instant.parse("2026-11-02T02:35:00Z"))

        val overview = repo.observeOverview().first()

        assertEquals(1, overview.trainingsThisMonth)
        assertEquals(0, overview.recordsThisMonth)
    }

    @Test
    fun `the overview re-emits when a set is added`() = runTest {
        val squat = machine("Squat")
        val first = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(first, squat, 100.0, 8, Instant.parse("2026-11-02T10:05:00Z"))
        val emissions = Channel<ProgressOverview>(Channel.UNLIMITED)
        backgroundScope.launch { repo.observeOverview().collect { emissions.send(it) } }
        advanceUntilIdle()
        runCurrent()

        val before = emissions.receiveUntil { it.machines.isNotEmpty() }
        assertEquals(1, before.machines.single().points.size)

        val second = session(Instant.parse("2026-11-02T12:00:00Z"))
        set(second, squat, 125.0, 8, Instant.parse("2026-11-02T12:05:00Z"))
        val after = emissions.receiveUntil { it.machines.single().points.size == 2 }

        assertEquals(125.0, after.machines.single().points.last().value, 0.0)
        // Both sessions are on 2 November, so they are one training day.
        assertEquals(1, after.trainingsThisMonth)
    }

    @Test
    fun `the current month is read again on each emission`() = runTest {
        // Two sessions in October (UTC), then the clock moves into November.
        val squat = machine("Squat")
        val earlyOctober = session(Instant.parse("2026-10-30T10:00:00Z"))
        set(earlyOctober, squat, 100.0, 8, Instant.parse("2026-10-30T10:05:00Z"))
        val lateOctober = session(Instant.parse("2026-10-31T10:00:00Z"))
        set(lateOctober, squat, 100.0, 8, Instant.parse("2026-10-31T10:05:00Z"))
        clock = TestClock(Instant.parse("2026-10-31T20:00:00Z"))
        repo = DefaultProgressRepository(store, clock)
        val emissions = Channel<ProgressOverview>(Channel.UNLIMITED)
        backgroundScope.launch { repo.observeOverview().collect { emissions.send(it) } }
        advanceUntilIdle()
        runCurrent()
        assertEquals(2, emissions.receiveUntil { true }.trainingsThisMonth)

        clock.advance(java.time.Duration.ofDays(2))
        val november = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(november, squat, 102.5, 8, Instant.parse("2026-11-02T10:05:00Z"))
        val after = emissions.receiveUntil { it.machines.single().points.size == 3 }

        assertEquals(1, after.trainingsThisMonth)
    }

    // observeMachineProgress

    @Test
    fun `an unknown machine has no progress`() = runTest {
        assertNull(repo.observeMachineProgress(999L).first())
    }

    @Test
    fun `a machine without sets has empty points`() = runTest {
        val id = machine("Leg press")

        val progress = repo.observeMachineProgress(id).first()!!

        assertEquals("Leg press", progress.machine.name)
        assertEquals(ProgressMetric.WEIGHT, progress.metric)
        assertTrue(progress.points.isEmpty())
    }

    @Test
    fun `a bodyweight machine without weights measures reps`() = runTest {
        val id = machine("Pull-up bar", kind = MachineKind.BODYWEIGHT_REPS)
        val s = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(s, id, null, 12, Instant.parse("2026-11-02T10:05:00Z"))
        set(s, id, null, 10, Instant.parse("2026-11-02T10:06:00Z"), position = 1)

        val progress: MachineProgress = repo.observeMachineProgress(id).first()!!

        assertEquals(ProgressMetric.REPS, progress.metric)
        assertEquals(listOf(12.0), progress.points.map { it.value })
        assertEquals(2, progress.points.single().setCount)
    }

    @Test
    fun `machine progress lists every session ascending`() = runTest {
        val id = machine("Squat")
        val early = session(Instant.parse("2026-10-01T10:00:00Z"))
        val late = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(late, id, 120.0, 5, Instant.parse("2026-11-02T10:05:00Z"))
        set(early, id, 100.0, 8, Instant.parse("2026-10-01T10:05:00Z"))

        val progress = repo.observeMachineProgress(id).first()!!

        assertEquals(ProgressMetric.WEIGHT, progress.metric)
        assertEquals(listOf(100.0, 120.0), progress.points.map { it.value })
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 2)), progress.points.map { it.date })
    }

    @Test
    fun `archived machine progress is still available`() = runTest {
        val id = machine("Old machine", archived = true)
        val s = session(Instant.parse("2026-10-20T10:00:00Z"))
        set(s, id, 50.0, 10, Instant.parse("2026-10-20T10:05:00Z"))

        val progress = repo.observeMachineProgress(id).first()

        assertEquals(listOf(50.0), progress!!.points.map { it.value })
    }
}

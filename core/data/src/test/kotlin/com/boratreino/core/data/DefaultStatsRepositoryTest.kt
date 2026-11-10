package com.boratreino.core.data

import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Session
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
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

/** The statistics report, read from the store with the Clock's today and zone. */
class DefaultStatsRepositoryTest {
    // Thursday, 5 November 2026, noon (UTC).
    private val thursday: Instant = Instant.parse("2026-11-05T12:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var library: FakeLibrary
    private lateinit var repo: StatsRepository

    /** Serves a fixed list and counts how often it is read. */
    private class FakeLibrary(private val exercises: List<LibraryExercise> = emptyList()) : ExerciseLibraryRepository {
        var reads = 0

        override suspend fun all(): List<LibraryExercise> {
            reads++
            return exercises
        }

        override suspend fun byId(id: String): LibraryExercise? = exercises.firstOrNull { it.id == id }
    }

    private fun exercise(id: String, force: String?, vararg primary: String) = LibraryExercise(
        id = id,
        name = id,
        namePt = id,
        equipment = LibraryEquipment.MACHINE,
        level = LibraryLevel.BEGINNER,
        category = "strength",
        mechanic = null,
        force = force,
        primaryMuscles = primary.toList(),
        secondaryMuscles = emptyList(),
        steps = emptyList(),
    )

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(thursday)
        library = FakeLibrary()
        repo = DefaultStatsRepository(store, clock, library)
    }

    private suspend fun machine(
        name: String,
        group: MuscleGroup = MuscleGroup.CHEST,
        archived: Boolean = false,
    ): Long = store.saveMachine(Machine(0, name, group, MachineKind.WEIGHT_REPS, 2.5, null, null, archived))

    private suspend fun session(startedAt: Instant): Long =
        store.insertSession(Session(0, startedAt, startedAt.plusSeconds(3600), null, null))

    private suspend fun set(sessionId: Long, machineId: Long, weightKg: Double, reps: Int, loggedAt: Instant) {
        store.insertSet(WorkoutSet(0, sessionId, machineId, 0, weightKg, reps, null, null, null, loggedAt))
    }

    private suspend fun <T> Channel<T>.receiveUntil(wanted: (T) -> Boolean): T {
        while (true) {
            val value = receive()
            if (wanted(value)) return value
        }
    }

    private fun StatsReport.lastTrained(group: MuscleGroup): LocalDate? =
        recency.first { it.muscleGroup == group }.lastTrained

    @Test
    fun `an empty store gives an empty report for the period`() = runTest {
        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertEquals(StatsPeriod.FOUR_WEEKS, report.period)
        assertEquals(4, report.weeks.size)
        assertTrue(report.weeks.all { it.sessions == 0 })
        assertTrue(report.records.isEmpty())
        assertEquals(MuscleGroup.entries.size, report.recency.size)
        assertTrue(report.recency.all { it.lastTrained == null })
    }

    @Test
    fun `the report combines the sets, sessions and machines`() = runTest {
        val bench = machine("Bench press")
        val s = session(Instant.parse("2026-11-04T10:00:00Z"))
        set(s, bench, 50.0, 10, Instant.parse("2026-11-04T10:05:00Z"))
        set(s, bench, 50.0, 8, Instant.parse("2026-11-04T10:08:00Z"))

        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertEquals(1, report.weeks.sumOf { it.sessions })
        assertEquals(2, report.weeks.sumOf { it.workingSets })
        assertEquals(900.0, report.weeks.sumOf { it.volumeKg }, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 4), report.lastTrained(MuscleGroup.CHEST))
        assertEquals(1, report.recency.first { it.muscleGroup == MuscleGroup.CHEST }.daysSince)
        assertEquals(1, report.times.sessionsCounted)
        assertEquals(60, report.times.averageMinutes)
    }

    @Test
    fun `archived machines are included`() = runTest {
        val old = machine("Old machine", MuscleGroup.LEGS, archived = true)
        val s = session(Instant.parse("2026-11-03T10:00:00Z"))
        set(s, old, 80.0, 10, Instant.parse("2026-11-03T10:05:00Z"))

        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertEquals(LocalDate.of(2026, 11, 3), report.lastTrained(MuscleGroup.LEGS))
        assertEquals(1, report.weeks.sumOf { it.workingSets })
    }

    @Test
    fun `the weekly goal is passed on`() = runTest {
        assertEquals(3, repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first().consistency.goal)
        assertEquals(5, repo.observeStats(StatsPeriod.FOUR_WEEKS, 5).first().consistency.goal)
    }

    @Test
    fun `the period is passed on`() = runTest {
        assertEquals(13, repo.observeStats(StatsPeriod.THREE_MONTHS, 3).first().weeks.size)
        assertEquals(52, repo.observeStats(StatsPeriod.ONE_YEAR, 3).first().weeks.size)
        assertEquals(StatsPeriod.ALL, repo.observeStats(StatsPeriod.ALL, 3).first().period)
    }

    @Test
    fun `today comes from the clock`() = runTest {
        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertEquals(LocalDate.of(2026, 11, 5), report.to)
        // The last week is the one that holds today, whatever day the locale starts its week on.
        val last = report.weeks.last().weekStart
        assertTrue(!last.isAfter(report.to) && report.to.isBefore(last.plusDays(7)))
    }

    @Test
    fun `a session at 23 30 the day before is that day in the clock's zone`() = runTest {
        // 02:30 UTC on 5 November is 23:30 on 4 November in Sao Paulo (UTC-3).
        clock = TestClock(thursday, ZoneId.of("America/Sao_Paulo"))
        repo = DefaultStatsRepository(store, clock, library)
        val bench = machine("Bench press")
        val s = session(Instant.parse("2026-11-05T02:30:00Z"))
        set(s, bench, 50.0, 10, Instant.parse("2026-11-05T02:35:00Z"))

        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertEquals(LocalDate.of(2026, 11, 4), report.lastTrained(MuscleGroup.CHEST))
    }

    @Test
    fun `the same session is today in UTC`() = runTest {
        val bench = machine("Bench press")
        val s = session(Instant.parse("2026-11-05T02:30:00Z"))
        set(s, bench, 50.0, 10, Instant.parse("2026-11-05T02:35:00Z"))

        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertEquals(LocalDate.of(2026, 11, 5), report.lastTrained(MuscleGroup.CHEST))
    }

    @Test
    fun `today is read in the clock's zone`() = runTest {
        // 01:00 UTC on 6 November is still 5 November in Sao Paulo.
        clock = TestClock(Instant.parse("2026-11-06T01:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        repo = DefaultStatsRepository(store, clock, library)

        assertEquals(LocalDate.of(2026, 11, 5), repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first().to)
    }

    @Test
    fun `the report re-emits when a set is added`() = runTest {
        val bench = machine("Bench press")
        val first = session(Instant.parse("2026-11-03T10:00:00Z"))
        set(first, bench, 50.0, 10, Instant.parse("2026-11-03T10:05:00Z"))
        val emissions = Channel<StatsReport>(Channel.UNLIMITED)
        backgroundScope.launch { repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).collect { emissions.send(it) } }
        advanceUntilIdle()
        runCurrent()

        val before = emissions.receiveUntil { it.weeks.sumOf { w -> w.workingSets } == 1 }
        assertEquals(500.0, before.weeks.sumOf { it.volumeKg }, 1e-9)

        set(first, bench, 60.0, 10, Instant.parse("2026-11-03T10:10:00Z"))
        val after = emissions.receiveUntil { it.weeks.sumOf { w -> w.workingSets } == 2 }

        assertEquals(1100.0, after.weeks.sumOf { it.volumeKg }, 1e-9)
        assertNull(after.strength.firstOrNull())
    }

    @Test
    fun `the strength now list holds the best estimate of each machine in the last window`() = runTest {
        val bench = machine("Bench press")
        val s = session(Instant.parse("2026-11-03T10:00:00Z"))
        set(s, bench, 80.0, 5, Instant.parse("2026-11-03T10:05:00Z"))

        val report = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        assertEquals(listOf(bench), report.strengthNow.map { it.machine.id })
        assertEquals(80.0 * (1 + 5 / 30.0), report.strengthNow.single().estimate1rm, 1e-9)
    }

    @Test
    fun `the library is read once and its force classifies a machine in the balance`() = runTest {
        library = FakeLibrary(listOf(exercise("row", "pull", "lats")))
        repo = DefaultStatsRepository(store, clock, library)
        val row = store.saveMachine(
            Machine(0, "Row", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false, "row"),
        )
        val s = session(Instant.parse("2026-11-03T10:00:00Z"))
        set(s, row, 50.0, 10, Instant.parse("2026-11-03T10:05:00Z"))

        val first = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()
        val second = repo.observeStats(StatsPeriod.FOUR_WEEKS, 3).first()

        // The group says chest (push); the library says pull.
        assertEquals(1, first.balance.pullSets)
        assertEquals(0, first.balance.pushSets)
        assertEquals(1, second.balance.pullSets)
        assertEquals(1, library.reads)
    }
}

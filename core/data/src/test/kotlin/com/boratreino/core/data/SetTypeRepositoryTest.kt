package com.boratreino.core.data

import com.boratreino.core.model.BackupCodec
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.BackupDecodeResult
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Record
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Set types through the repositories: written as given, and warm-ups left out of counts, records and repeats. */
class SetTypeRepositoryTest {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val now: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var routines: RoutineRepository
    private lateinit var training: DefaultTrainingRepository
    private lateinit var history: HistoryRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(now, zone)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        history = DefaultHistoryRepository(store, clock, training)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.BACK, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun routine(shortName: String, vararg machineIds: Long): Long = routines.saveRoutine(
        Routine(id = 0, planId = 0, name = "Routine $shortName", shortName = shortName, position = 0, weekdays = emptySet()),
        machineIds.mapIndexed { i, id -> RoutineItem(0, id, i, 3, 8, 12) },
    )

    private suspend fun pastSession(startedAt: Instant, routineId: Long?, endedAt: Instant? = startedAt.plusSeconds(3600)): Long =
        store.insertSession(Session(id = 0, startedAt = startedAt, endedAt = endedAt, routineId = routineId, note = null))

    private suspend fun storeSet(
        sessionId: Long,
        machineId: Long,
        position: Int,
        kg: Double,
        reps: Int,
        at: Instant,
        type: SetType = SetType.NORMAL,
    ) = store.insertSet(WorkoutSet(0, sessionId, machineId, position, kg, reps, null, null, null, at, type))

    private fun setsOf(sessionId: Long, machineId: Long) =
        store.allSets().filter { it.sessionId == sessionId && it.machineId == machineId }.sortedBy { it.position }

    // logSet

    @Test
    fun `logSet writes the type of the draft`() = runTest {
        val m = machine("Press")

        SetType.entries.forEach { type ->
            val logged = training.logSet(m, SetDraft(40.0, 10, type)).set
            assertEquals(type, logged.type)
        }

        assertEquals(SetType.entries.toList(), store.allSets().sortedBy { it.position }.map { it.type })
    }

    @Test
    fun `a set logged with the default draft is normal`() = runTest {
        val m = machine("Press")

        assertEquals(SetType.NORMAL, training.logSet(m, SetDraft(40.0, 10)).set.type)
    }

    @Test
    fun `a warm-up returns no record even when it is heavier than everything`() = runTest {
        val m = machine("Press")
        training.logSet(m, SetDraft(40.0, 10))
        clock.advance(Duration.ofMinutes(2))

        val warmup = training.logSet(m, SetDraft(100.0, 20, SetType.WARMUP))

        assertNull(warmup.record)
        assertEquals(SetType.WARMUP, warmup.set.type)
    }

    @Test
    fun `a working set after a heavier warm-up is still a record against working sets`() = runTest {
        val m = machine("Press")
        training.logSet(m, SetDraft(40.0, 10))
        clock.advance(Duration.ofMinutes(2))
        training.logSet(m, SetDraft(100.0, 20, SetType.WARMUP))
        clock.advance(Duration.ofMinutes(2))

        val working = training.logSet(m, SetDraft(45.0, 8))

        assertEquals(Record.HeaviestWeight(45.0), working.record)
    }

    @Test
    fun `warm-ups keep the positions unique`() = runTest {
        val m = machine("Press")
        training.logSet(m, SetDraft(20.0, 15, SetType.WARMUP))
        training.logSet(m, SetDraft(40.0, 10))

        assertEquals(listOf(0, 1), store.allSets().map { it.position }.sorted())
    }

    @Test
    fun `finishing a session counts working sets and machines only`() = runTest {
        val press = machine("Press")
        val curl = machine("Curl")
        training.logSet(press, SetDraft(20.0, 15, SetType.WARMUP))
        training.logSet(press, SetDraft(40.0, 10))
        training.logSet(press, SetDraft(30.0, 8, SetType.DROP))
        training.logSet(curl, SetDraft(5.0, 15, SetType.WARMUP))

        val summary = requireNotNull(training.finishSession())

        assertEquals(2, summary.setCount)
        assertEquals(1, summary.machineCount)
    }

    @Test
    fun `a finished session counts no record from warm-ups`() = runTest {
        val press = machine("Press")
        val earlier = pastSession(now.minus(Duration.ofDays(3)), null)
        storeSet(earlier, press, 0, 40.0, 10, now.minus(Duration.ofDays(3)))
        training.logSet(press, SetDraft(100.0, 20, SetType.WARMUP))
        clock.advance(Duration.ofMinutes(2))
        training.logSet(press, SetDraft(30.0, 10))

        val summary = requireNotNull(training.finishSession())

        assertEquals(0, summary.recordCount)
    }

    @Test
    fun `the machine log keeps warm-ups in this session's sets`() = runTest {
        val press = machine("Press")
        training.logSet(press, SetDraft(20.0, 15, SetType.WARMUP))

        val log = requireNotNull(training.observeMachineLog(press).first())

        assertEquals(listOf(SetType.WARMUP), log.thisSession.map { it.type })
    }

    // addSet

    @Test
    fun `addSet writes the type of the draft`() = runTest {
        val m = machine("Press")
        val session = pastSession(Instant.parse("2026-10-20T18:00:00Z"), null)

        SetType.entries.forEach { type ->
            assertEquals(type, history.addSet(session, m, SetDraft(40.0, 10, type)).type)
        }

        assertEquals(SetType.entries.toList(), setsOf(session, m).map { it.type })
    }

    @Test
    fun `addSet defaults to normal`() = runTest {
        val m = machine("Press")
        val session = pastSession(Instant.parse("2026-10-20T18:00:00Z"), null)

        assertEquals(SetType.NORMAL, history.addSet(session, m, SetDraft(40.0, 10)).type)
    }

    // observeMonth

    @Test
    fun `the month counts working sets and machines only`() = runTest {
        val press = machine("Press")
        val curl = machine("Curl")
        val start = Instant.parse("2026-11-01T15:00:00Z")
        val s = pastSession(start, null)
        storeSet(s, press, 0, 20.0, 15, start, SetType.WARMUP)
        storeSet(s, press, 1, 40.0, 10, start.plusSeconds(60))
        storeSet(s, press, 2, 40.0, 6, start.plusSeconds(120), SetType.FAILURE)
        storeSet(s, curl, 0, 5.0, 15, start.plusSeconds(180), SetType.WARMUP)

        val info = history.observeMonth(YearMonth.of(2026, 11)).first().values.flatten().single()

        assertEquals(2, info.setCount)
        assertEquals(1, info.machineCount)
    }

    // Repeat

    @Test
    fun `repeating the last training writes normal sets from working sets only`() = runTest {
        val press = machine("Press")
        val lat = machine("Lat")
        val routineA = routine("A", press, lat)
        val start = now.minus(Duration.ofDays(2))
        val earlier = pastSession(start, routineA)
        storeSet(earlier, press, 0, 20.0, 15, start, SetType.WARMUP)
        storeSet(earlier, press, 1, 40.0, 10, start.plusSeconds(60))
        storeSet(earlier, press, 2, 35.0, 8, start.plusSeconds(120), SetType.DROP)
        storeSet(earlier, lat, 0, 10.0, 15, start.plusSeconds(180), SetType.WARMUP)
        training.chooseRoutine(RoutineChoice.Chosen(routineA))

        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        val written = setsOf(outcome.sessionId, press)
        assertEquals(listOf(40.0, 35.0), written.map { it.weightKg })
        assertTrue(written.all { it.type == SetType.NORMAL })
        assertTrue(setsOf(outcome.sessionId, lat).isEmpty())
        assertEquals(1, outcome.machineCount)
    }

    @Test
    fun `the repeat offer counts working sets only`() = runTest {
        val press = machine("Press")
        val routineA = routine("A", press)
        val start = now.minus(Duration.ofDays(2))
        val earlier = pastSession(start, routineA)
        storeSet(earlier, press, 0, 20.0, 15, start, SetType.WARMUP)
        storeSet(earlier, press, 1, 40.0, 10, start.plusSeconds(60))
        training.chooseRoutine(RoutineChoice.Chosen(routineA))

        val offer = requireNotNull(training.observeRepeatOffer().first())

        assertEquals(1, offer.setCount)
    }

    @Test
    fun `repeating a last session writes normal sets from working sets only`() = runTest {
        val press = machine("Press")
        val routineA = routine("A", press)
        val day1 = Instant.parse("2026-10-26T18:00:00Z")
        val day2 = Instant.parse("2026-10-30T18:00:00Z")
        val last = pastSession(day1, routineA)
        storeSet(last, press, 0, 20.0, 15, day1, SetType.WARMUP)
        storeSet(last, press, 1, 40.0, 10, day1.plusSeconds(60))
        storeSet(last, press, 2, 40.0, 6, day1.plusSeconds(120), SetType.FAILURE)
        val target = pastSession(day2, routineA, endedAt = null)

        val saved = history.repeatLastSession(target, RepeatMode.SAME)

        assertEquals(listOf(40.0, 40.0), saved.map { it.weightKg })
        assertEquals(listOf(10, 6), saved.map { it.reps })
        assertTrue(saved.all { it.type == SetType.NORMAL })
    }

    // Backup

    @Test
    fun `a backup export and import keeps every set type`() = runTest {
        val m = Machine(1, "Press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false)
        val t = Instant.parse("2026-10-27T18:00:00Z")
        val sets = SetType.entries.mapIndexed { i, type ->
            WorkoutSet(i + 1L, 1, 1, i, 40.0, 10, null, null, null, t.plusSeconds(60L * i), type)
        }
        val data = BackupData(
            machines = listOf(m),
            plans = emptyList(),
            routines = emptyList(),
            items = emptyList(),
            sessions = listOf(Session(1, t, t.plusSeconds(3600), null, null)),
            sets = sets,
        )
        store.replaceAll(data)
        val repo = DefaultBackupRepository(store, clock, training)

        val decoded = BackupCodec.decode(repo.export()) as BackupDecodeResult.Ok
        val otherStore = FakeWorkoutStore()
        val otherClock = TestClock(now, zone)
        DefaultBackupRepository(otherStore, otherClock, DefaultTrainingRepository(otherStore, otherClock, DefaultRoutineRepository(otherStore)))
            .import(decoded.data)

        assertEquals(SetType.entries.toList(), otherStore.exportAll().sets.map { it.type })
        assertNotNull(decoded.data.sets.firstOrNull { it.type == SetType.WARMUP })
    }
}

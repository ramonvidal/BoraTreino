package com.boratreino.core.data

import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.HealthExerciseKind
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Cardio logging, the Health Connect export feed and the session ids, on the in-memory store. */
class CardioRepositoryTest {
    /** 2 November 2026, 15:00 in Sao Paulo (UTC-3). */
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val now: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var training: DefaultTrainingRepository
    private lateinit var history: HistoryRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(now, zone)
        training = DefaultTrainingRepository(store, clock, DefaultRoutineRepository(store))
        history = DefaultHistoryRepository(store, clock, training)
        machines = DefaultMachineRepository(store)
    }

    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private suspend fun cardioMachine(name: String = "Treadmill"): Long =
        machines.save(Machine(0, name, MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false))

    private suspend fun weightMachine(name: String = "Leg press"): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun session(startedAt: Instant, endedAt: Instant? = startedAt, routineId: Long? = null): Long =
        store.insertSession(Session(0, startedAt, endedAt, routineId, null))

    private suspend fun routine(name: String): Long {
        val plan = store.ensurePlan()
        return store.insertRoutine(Routine(0, plan.id, name, name.take(1), store.allRoutines().size + 1, emptySet()))
    }

    private suspend fun storeCardio(sessionId: Long, machineId: Long, position: Int, loggedAt: Instant, draft: CardioDraft): WorkoutSet {
        val set = WorkoutSet(
            0, sessionId, machineId, position, null, null,
            draft.minutes.takeIf { it > 0 }?.let { it * 60 }, draft.calories.takeIf { it > 0 }, draft.distanceM.takeIf { it > 0 }, loggedAt,
        )
        return set.copy(id = store.insertSet(set))
    }

    private suspend fun storeWeights(sessionId: Long, machineId: Long, position: Int, loggedAt: Instant): WorkoutSet {
        val set = WorkoutSet(0, sessionId, machineId, position, 40.0, 10, null, null, null, loggedAt)
        return set.copy(id = store.insertSet(set))
    }

    private fun draft(minutes: Int, calories: Int = 0, distanceM: Int = 0) = CardioDraft(minutes, calories, distanceM)

    // logCardio

    @Test
    fun `logging cardio opens a session and writes the set before anything else`() = runTest {
        val treadmill = cardioMachine()

        val logged = training.logCardio(treadmill, draft(25, 200, 4000))

        val stored = store.allSets().single()
        assertEquals(logged.set, stored)
        assertEquals(WorkoutSet(stored.id, stored.sessionId, treadmill, 0, null, null, 1500, 200, 4000, now), stored)
        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(stored.sessionId, open.session.id)
        assertEquals(now, open.session.startedAt)
        assertNull(open.session.endedAt)
    }

    @Test
    fun `a logged cardio set is never a record`() = runTest {
        val treadmill = cardioMachine()
        training.logCardio(treadmill, draft(10))
        clock.advance(Duration.ofMinutes(15))

        val second = training.logCardio(treadmill, draft(60, 900, 20_000))

        assertNull(second.record)
    }

    @Test
    fun `zero values are stored as null`() = runTest {
        val treadmill = cardioMachine()

        val set = training.logCardio(treadmill, draft(0, 120, 0)).set

        assertNull(set.seconds)
        assertEquals(120, set.calories)
        assertNull(set.distanceM)
        assertNull(set.weightKg)
        assertNull(set.reps)
    }

    @Test
    fun `later cardio sets join the open session and count positions per machine`() = runTest {
        val treadmill = cardioMachine()
        val bike = cardioMachine("Stationary bike")
        val first = training.logCardio(treadmill, draft(10)).set
        clock.advance(Duration.ofMinutes(12))
        val second = training.logCardio(treadmill, draft(10)).set
        clock.advance(Duration.ofMinutes(12))
        val other = training.logCardio(bike, draft(15)).set

        assertEquals(first.sessionId, second.sessionId)
        assertEquals(first.sessionId, other.sessionId)
        assertEquals(1, store.allSessions().size)
        assertEquals(0, first.position)
        assertEquals(1, second.position)
        assertEquals(0, other.position)
    }

    @Test
    fun `logging cardio after 3 hours closes the stale session at its last set and opens a new one`() = runTest {
        val treadmill = cardioMachine()
        val first = training.logCardio(treadmill, draft(20)).set
        clock.advance(Duration.ofHours(3))

        val second = training.logCardio(treadmill, draft(20)).set

        assertNotEquals(first.sessionId, second.sessionId)
        assertEquals(first.loggedAt, store.allSessions().first { it.id == first.sessionId }.endedAt)
        assertEquals(second.sessionId, requireNotNull(training.observeOpenSession().first()).session.id)
        assertEquals(0, second.position)
    }

    @Test
    fun `cardio logged into a session with strength sets joins it`() = runTest {
        val press = weightMachine()
        val treadmill = cardioMachine()
        val strength = training.logSet(press, SetDraft(100.0, 10)).set
        clock.advance(Duration.ofMinutes(20))

        val cardio = training.logCardio(treadmill, draft(15)).set

        assertEquals(strength.sessionId, cardio.sessionId)
    }

    @Test
    fun `a cardio machine's log has last time and this session, and no suggestion`() = runTest {
        val treadmill = cardioMachine()
        val earlier = session(at("2026-10-26", "18:00"), at("2026-10-26", "18:40"))
        val lastSet = storeCardio(earlier, treadmill, 0, at("2026-10-26", "18:00"), draft(30, 250, 5000))
        val today = training.logCardio(treadmill, draft(20, 150)).set

        val log = requireNotNull(training.observeMachineLog(treadmill).first())

        assertEquals(listOf(lastSet), log.lastTime)
        assertEquals(listOf(today), log.thisSession)
        assertNull(log.suggestion)
    }

    // addCardioSet

    @Test
    fun `a cardio set added to a session gets the next position of its machine`() = runTest {
        val treadmill = cardioMachine()
        val bike = cardioMachine("Stationary bike")
        val id = session(at("2026-11-02", "18:00"), endedAt = null)
        storeCardio(id, treadmill, 0, at("2026-11-02", "18:05"), draft(10))

        val next = history.addCardioSet(id, treadmill, draft(15, 100))
        val first = history.addCardioSet(id, bike, draft(5))

        assertEquals(1, next.position)
        assertEquals(0, first.position)
    }

    @Test
    fun `an added cardio set holds the draft and is logged one minute after the last set`() = runTest {
        val treadmill = cardioMachine()
        val id = session(at("2026-11-02", "18:00"), endedAt = null)
        storeCardio(id, treadmill, 0, at("2026-11-02", "18:20"), draft(10))

        val added = history.addCardioSet(id, treadmill, draft(25, 180, 3500))

        assertEquals(at("2026-11-02", "18:21"), added.loggedAt)
        assertEquals(1500, added.seconds)
        assertEquals(180, added.calories)
        assertEquals(3500, added.distanceM)
        assertNull(added.weightKg)
        assertNull(added.reps)
        assertEquals(added, store.allSets().last())
    }

    @Test
    fun `the first cardio set of an empty session is logged at its start`() = runTest {
        val treadmill = cardioMachine()
        val start = at("2026-10-20", "18:00")
        val id = session(start, endedAt = start)

        val added = history.addCardioSet(id, treadmill, draft(20))

        assertEquals(start, added.loggedAt)
        assertEquals(start, store.session(id)?.endedAt)
    }

    @Test
    fun `a closed session stays closed and ends at the new cardio set when it is later`() = runTest {
        val treadmill = cardioMachine()
        val start = at("2026-10-20", "18:00")
        val id = session(start, endedAt = start)
        storeCardio(id, treadmill, 0, start, draft(10))

        history.addCardioSet(id, treadmill, draft(10))

        val stored = requireNotNull(store.session(id))
        assertEquals(start.plusSeconds(60), stored.endedAt)
        assertFalse(stored.isOpen)
        assertNull(training.observeOpenSession().first())
    }

    @Test
    fun `a closed session keeps its end when the new cardio set is before it`() = runTest {
        val treadmill = cardioMachine()
        val end = at("2026-10-20", "19:00")
        val id = session(at("2026-10-20", "18:00"), endedAt = end)
        storeCardio(id, treadmill, 0, at("2026-10-20", "18:10"), draft(10))

        history.addCardioSet(id, treadmill, draft(10))

        assertEquals(end, store.session(id)?.endedAt)
    }

    @Test
    fun `an open session stays open after a cardio set is added`() = runTest {
        val treadmill = cardioMachine()
        val id = session(at("2026-11-02", "18:00"), endedAt = null)

        history.addCardioSet(id, treadmill, draft(10))

        assertNull(store.session(id)?.endedAt)
    }

    @Test
    fun `adding a cardio set to a session that does not exist fails`() = runTest {
        val treadmill = cardioMachine()

        try {
            history.addCardioSet(999L, treadmill, draft(10))
            fail("expected an IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(store.allSets().isEmpty())
        }
    }

    // observeSessionIds

    @Test
    fun `session ids are all the sessions, open or closed`() = runTest {
        val a = session(at("2026-10-20", "18:00"))
        val b = session(at("2026-11-02", "18:00"), endedAt = null)

        assertEquals(setOf(a, b), history.observeSessionIds().first())
    }

    @Test
    fun `no sessions gives no ids`() = runTest {
        assertEquals(emptySet<Long>(), history.observeSessionIds().first())
    }

    @Test
    fun `a deleted session leaves the ids`() = runTest {
        val a = session(at("2026-10-20", "18:00"))
        val b = session(at("2026-10-21", "18:00"))

        history.deleteSession(a)

        assertEquals(setOf(b), history.observeSessionIds().first())
    }

    @Test
    fun `session ids re-emit when a session is added or deleted`() = runTest {
        val emissions = Channel<Set<Long>>(Channel.UNLIMITED)
        backgroundScope.launch { history.observeSessionIds().collect { emissions.send(it) } }
        advanceUntilIdle()
        runCurrent()
        assertEquals(emptySet<Long>(), emissions.receive())

        val a = session(at("2026-10-20", "18:00"))
        advanceUntilIdle()
        runCurrent()
        assertEquals(setOf(a), emissions.receive())

        history.deleteSession(a)
        advanceUntilIdle()
        runCurrent()
        assertEquals(emptySet<Long>(), emissions.receive())
    }

    // observeClosedSessionExports

    private suspend fun exports(sinceDays: Int = 30): List<HealthExport> = history.observeClosedSessionExports(sinceDays).first()

    @Test
    fun `no sessions gives no exports`() = runTest {
        assertTrue(exports().isEmpty())
    }

    @Test
    fun `a closed session with sets is exported with its times and client id`() = runTest {
        val press = weightMachine()
        val start = at("2026-11-01", "17:00")
        val end = at("2026-11-01", "18:00")
        val id = session(start, end)
        storeWeights(id, press, 0, at("2026-11-01", "17:05"))
        storeWeights(id, press, 1, at("2026-11-01", "17:10"))

        val export = exports().single()

        assertEquals(id, export.sessionId)
        assertEquals("boratreino-session-$id", export.clientRecordId)
        assertEquals(start, export.start)
        assertEquals(end, export.end)
        assertEquals(HealthExerciseKind.STRENGTH_TRAINING, export.kind)
        assertEquals(2, export.strengthSets)
        assertEquals(20, export.strengthReps)
        assertEquals("Training", export.title)
    }

    @Test
    fun `an open session is not exported`() = runTest {
        val press = weightMachine()
        val id = session(at("2026-11-02", "17:00"), endedAt = null)
        storeWeights(id, press, 0, at("2026-11-02", "17:05"))

        assertTrue(exports().isEmpty())
    }

    @Test
    fun `a closed session without sets is omitted`() = runTest {
        session(at("2026-11-01", "17:00"), at("2026-11-01", "17:00"))

        assertTrue(exports().isEmpty())
    }

    @Test
    fun `cardio totals and the machine's kind come through`() = runTest {
        val treadmill = cardioMachine("Treadmill")
        val id = session(at("2026-11-01", "17:00"), at("2026-11-01", "18:00"))
        storeCardio(id, treadmill, 0, at("2026-11-01", "17:00"), draft(20, 150, 3000))
        storeCardio(id, treadmill, 1, at("2026-11-01", "17:25"), draft(10, 80, 1500))

        val export = exports().single()

        assertEquals(HealthExerciseKind.TREADMILL, export.kind)
        assertEquals(230, export.activeCalories)
        assertEquals(4500, export.distanceM)
    }

    @Test
    fun `an archived machine still gives the session its kind`() = runTest {
        val treadmill = cardioMachine("Treadmill")
        val id = session(at("2026-11-01", "17:00"), at("2026-11-01", "18:00"))
        storeCardio(id, treadmill, 0, at("2026-11-01", "17:00"), draft(20))
        machines.setArchived(treadmill, true)

        assertEquals(HealthExerciseKind.TREADMILL, exports().single().kind)
    }

    @Test
    fun `the title is the routine's name`() = runTest {
        val press = weightMachine()
        val legs = routine("Legs day")
        val id = session(at("2026-11-01", "17:00"), at("2026-11-01", "18:00"), routineId = legs)
        storeWeights(id, press, 0, at("2026-11-01", "17:05"))

        assertEquals("Legs day", exports().single().title)
    }

    @Test
    fun `only sessions that started within the last days are exported`() = runTest {
        val press = weightMachine()
        val recent = session(at("2026-10-20", "17:00"), at("2026-10-20", "18:00"))
        val old = session(at("2026-09-01", "17:00"), at("2026-09-01", "18:00"))
        storeWeights(recent, press, 0, at("2026-10-20", "17:05"))
        storeWeights(old, press, 0, at("2026-09-01", "17:05"))

        assertEquals(listOf(recent), exports(sinceDays = 30).map { it.sessionId })
        assertEquals(setOf(recent, old), exports(sinceDays = 90).map { it.sessionId }.toSet())
    }

    @Test
    fun `today's closed session is exported even with a small window`() = runTest {
        val press = weightMachine()
        val id = session(at("2026-11-02", "09:00"), at("2026-11-02", "10:00"))
        storeWeights(id, press, 0, at("2026-11-02", "09:05"))

        assertEquals(listOf(id), exports(sinceDays = 1).map { it.sessionId })
    }

    @Test
    fun `the same content gives the same version and a changed set gives another`() = runTest {
        val press = weightMachine()
        val id = session(at("2026-11-01", "17:00"), at("2026-11-01", "18:00"))
        val set = storeWeights(id, press, 0, at("2026-11-01", "17:05"))

        val before = exports().single().version
        assertEquals(before, exports().single().version)

        store.updateSet(set.copy(reps = 12))

        assertNotEquals(before, exports().single().version)
    }

    @Test
    fun `exports re-emit when a set, a machine or a routine changes`() = runTest {
        val treadmill = cardioMachine("Treadmill")
        val legs = routine("Legs")
        val id = session(at("2026-11-01", "17:00"), at("2026-11-01", "18:00"), routineId = legs)
        storeCardio(id, treadmill, 0, at("2026-11-01", "17:00"), draft(20, 100))

        val emissions = Channel<List<HealthExport>>(Channel.UNLIMITED)
        backgroundScope.launch { history.observeClosedSessionExports(30).collect { emissions.send(it) } }
        advanceUntilIdle()
        runCurrent()
        suspend fun waitFor(wanted: (HealthExport) -> Boolean): HealthExport {
            advanceUntilIdle()
            runCurrent()
            while (true) {
                val list = emissions.receive()
                list.singleOrNull()?.takeIf(wanted)?.let { return it }
            }
        }

        assertEquals(100, waitFor { true }.activeCalories)

        storeCardio(id, treadmill, 1, at("2026-11-01", "17:30"), draft(10, 60))
        assertEquals(160, waitFor { it.activeCalories == 160 }.activeCalories)

        store.saveMachine(store.machines().first { it.id == treadmill }.copy(name = "Stationary bike"))
        assertEquals(HealthExerciseKind.STATIONARY_BIKE, waitFor { it.kind == HealthExerciseKind.STATIONARY_BIKE }.kind)

        store.updateRoutine(store.allRoutines().first { it.id == legs }.copy(name = "Cardio day", shortName = "C"))
        assertEquals("Cardio day", waitFor { it.title == "Cardio day" }.title)
    }

    @Test
    fun `a session that gets closed appears in the exports`() = runTest {
        val press = weightMachine()
        val set = training.logSet(press, SetDraft(100.0, 10)).set
        assertTrue(exports().isEmpty())

        clock.advance(Duration.ofMinutes(30))
        training.finishSession()

        assertEquals(listOf(set.sessionId), exports().map { it.sessionId })
    }
}

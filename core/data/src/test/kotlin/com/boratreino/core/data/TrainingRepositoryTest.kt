package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TrainingRepositoryTest {
    private val t0: Instant = Instant.parse("2026-10-30T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var training: TrainingRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(t0)
        training = DefaultTrainingRepository(store, clock, DefaultRoutineRepository(store))
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String, group: MuscleGroup = MuscleGroup.BACK): Long =
        machines.save(Machine(0, name, group, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    /** The logged set without its record; the record has its own tests. */
    private suspend fun logSet(machineId: Long, draft: SetDraft): WorkoutSet =
        training.logSet(machineId, draft).set

    private suspend fun machineLog(machineId: Long): MachineLog =
        requireNotNull(training.observeMachineLog(machineId).first())

    private fun minutes(n: Long): Duration = Duration.ofMinutes(n)

    // Opening and closing sessions

    @Test
    fun `the first logged set opens a session at the time of the set`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        assertNull(training.observeOpenSession().first())

        val set = logSet(leg, SetDraft(120.0, 12))

        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(set.sessionId, open.session.id)
        assertEquals(t0, open.session.startedAt)
        assertNull(open.session.endedAt)
        assertEquals(t0, set.loggedAt)
    }

    @Test
    fun `later sets in the same visit join the open session`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val first = logSet(leg, SetDraft(120.0, 12))
        clock.advance(minutes(5))
        val second = logSet(leg, SetDraft(120.0, 10))

        assertEquals(first.sessionId, second.sessionId)
        assertEquals(1, store.allSessions().size)
    }

    @Test
    fun `a set 2 h 59 min after the last one stays in the same session`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val first = logSet(leg, SetDraft(120.0, 12))
        clock.advance(Duration.ofHours(2).plusMinutes(59))

        val second = logSet(leg, SetDraft(120.0, 12))

        assertEquals(first.sessionId, second.sessionId)
        assertEquals(1, store.allSessions().size)
    }

    @Test
    fun `a set 3 h after the last one closes the old session at that last set and opens a new one`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val first = logSet(leg, SetDraft(120.0, 12))
        clock.advance(Duration.ofHours(1))
        val second = logSet(leg, SetDraft(120.0, 12))
        val lastSetAt = second.loggedAt
        clock.advance(Duration.ofHours(3))

        val third = logSet(leg, SetDraft(125.0, 10))

        val old = store.allSessions().first { it.id == first.sessionId }
        assertEquals(lastSetAt, old.endedAt)
        assertNotEquals(first.sessionId, third.sessionId)
        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(third.sessionId, open.session.id)
        assertEquals(clock.now, open.session.startedAt)
        assertEquals(listOf(third), open.machines.single().sets)
    }

    @Test
    fun `a stale session with no sets closes at its start time`() = runTest {
        val emptySession = store.insertSession(Session(0, t0, null, null, null))
        clock.advance(Duration.ofHours(3))

        training.closeStaleSession()

        assertEquals(t0, store.allSessions().first { it.id == emptySession }.endedAt)
        assertNull(training.observeOpenSession().first())
    }

    @Test
    fun `close stale session leaves a fresh session open`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        logSet(leg, SetDraft(120.0, 12))
        clock.advance(Duration.ofHours(2))

        training.closeStaleSession()

        assertNotNull(training.observeOpenSession().first())
    }

    @Test
    fun `close stale session closes a stale session at its last set`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val set = logSet(leg, SetDraft(120.0, 12))
        clock.advance(Duration.ofHours(3))

        training.closeStaleSession()

        assertEquals(set.loggedAt, store.allSessions().single().endedAt)
        assertNull(training.observeOpenSession().first())
    }

    @Test
    fun `staleness counts from the last set, not from the start of the session`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val first = logSet(leg, SetDraft(120.0, 12))
        clock.advance(Duration.ofHours(2))
        logSet(leg, SetDraft(120.0, 12))
        clock.advance(Duration.ofHours(2))

        val third = logSet(leg, SetDraft(120.0, 12))

        assertEquals(first.sessionId, third.sessionId)
        assertEquals(1, store.allSessions().size)
    }

    // Positions

    @Test
    fun `position counts the sets of that machine already in the session`() = runTest {
        val lat = machine("Lat pulldown")
        val row = machine("Seated row")

        val a = logSet(lat, SetDraft(55.0, 10))
        val b = logSet(row, SetDraft(40.0, 10))
        val c = logSet(lat, SetDraft(55.0, 9))

        assertEquals(listOf(0, 0, 1), listOf(a, b, c).map { it.position })
    }

    // Open session view

    @Test
    fun `the open session lists its machines most recently logged first`() = runTest {
        val lat = machine("Lat pulldown")
        val row = machine("Seated row")
        logSet(lat, SetDraft(55.0, 10))
        clock.advance(minutes(5))
        logSet(row, SetDraft(40.0, 10))
        clock.advance(minutes(10))
        logSet(lat, SetDraft(55.0, 8))

        val open = requireNotNull(training.observeOpenSession().first())

        assertEquals(listOf(lat, row), open.machines.map { it.machine.id })
        assertEquals(listOf(10, 8), open.machines.first().sets.map { it.reps })
        assertEquals(listOf(0, 1), open.machines.first().sets.map { it.position })
    }

    @Test
    fun `the open session lists only machines with sets`() = runTest {
        val lat = machine("Lat pulldown")
        machine("Seated row")
        logSet(lat, SetDraft(55.0, 10))

        val open = requireNotNull(training.observeOpenSession().first())

        assertEquals(listOf(lat), open.machines.map { it.machine.id })
    }

    // Machine log: last time and this session

    @Test
    fun `a machine never used has no last time and no this-session sets`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)

        val log = machineLog(press)

        assertEquals(press, log.machine.id)
        assertEquals(emptyList<WorkoutSet>(), log.lastTime)
        assertNull(log.lastTimeAt)
        assertEquals(emptyList<WorkoutSet>(), log.thisSession)
    }

    @Test
    fun `an unknown machine has no log`() = runTest {
        assertNull(training.observeMachineLog(999L).first())
    }

    @Test
    fun `last time comes from the previous session, not the open one`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val lastVisit1 = logSet(press, SetDraft(120.0, 12))
        clock.advance(minutes(10))
        val lastVisit2 = logSet(press, SetDraft(120.0, 10))
        clock.advance(minutes(10))
        training.finishSession()
        clock.now = t0.plus(Duration.ofDays(1))
        val today = logSet(press, SetDraft(125.0, 12))

        val log = machineLog(press)

        assertEquals(listOf(lastVisit1, lastVisit2), log.lastTime)
        assertEquals(lastVisit2.loggedAt, log.lastTimeAt)
        assertEquals(listOf(today), log.thisSession)
    }

    @Test
    fun `last time is the most recent other session that has sets on that machine`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val row = machine("Seated row")
        val curl = machine("Biceps curl")
        val lastVisit = logSet(press, SetDraft(120.0, 12))
        training.finishSession()
        clock.now = t0.plus(Duration.ofDays(1))
        logSet(row, SetDraft(40.0, 10))
        training.finishSession()
        clock.now = t0.plus(Duration.ofDays(2))
        logSet(curl, SetDraft(10.0, 12))

        val log = machineLog(press)

        assertEquals(listOf(lastVisit), log.lastTime)
        assertEquals(lastVisit.loggedAt, log.lastTimeAt)
        assertEquals(emptyList<WorkoutSet>(), log.thisSession)
    }

    @Test
    fun `this session holds only that machine's sets in the open session`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val row = machine("Seated row")
        val pressSet = logSet(press, SetDraft(120.0, 12))
        logSet(row, SetDraft(40.0, 10))

        val log = machineLog(press)

        assertEquals(listOf(pressSet), log.thisSession)
    }

    // Finish and reopen

    @Test
    fun `finishing returns the totals and closes the session now`() = runTest {
        val lat = machine("Lat pulldown")
        val row = machine("Seated row")
        val first = logSet(lat, SetDraft(55.0, 10))
        logSet(lat, SetDraft(55.0, 9))
        logSet(row, SetDraft(40.0, 10))
        clock.advance(minutes(40))

        val summary = training.finishSession()

        assertEquals(SessionSummary(sessionId = first.sessionId, machineCount = 2, setCount = 3), summary)
        assertNull(training.observeOpenSession().first())
        assertEquals(t0.plus(minutes(40)), store.allSessions().single().endedAt)
    }

    @Test
    fun `finishing with nothing open returns null`() = runTest {
        assertNull(training.finishSession())
    }

    @Test
    fun `reopening a finished session makes it open again`() = runTest {
        val lat = machine("Lat pulldown")
        val set = logSet(lat, SetDraft(55.0, 10))
        training.finishSession()

        training.reopenSession(set.sessionId)

        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(set.sessionId, open.session.id)
        assertNull(open.session.endedAt)
    }

    @Test
    fun `reopening does nothing while another session is open`() = runTest {
        val lat = machine("Lat pulldown")
        val finished = logSet(lat, SetDraft(55.0, 10))
        training.finishSession()
        val other = logSet(lat, SetDraft(55.0, 10))

        training.reopenSession(finished.sessionId)

        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(other.sessionId, open.session.id)
        assertNotNull(store.allSessions().first { it.id == finished.sessionId }.endedAt)
    }

    // Delete, restore and update

    @Test
    fun `a deleted set is gone and restoring it brings it back with its id`() = runTest {
        val lat = machine("Lat pulldown")
        val set = logSet(lat, SetDraft(55.0, 10))

        training.deleteSet(set)
        assertEquals(emptyList<WorkoutSet>(), machineLog(lat).thisSession)

        training.restoreSet(set)
        assertEquals(listOf(set), machineLog(lat).thisSession)
    }

    @Test
    fun `an updated set shows its new weight and reps`() = runTest {
        val lat = machine("Lat pulldown")
        val set = logSet(lat, SetDraft(55.0, 10))

        training.updateSet(set.copy(weightKg = 57.5, reps = 8))

        assertEquals(listOf(set.copy(weightKg = 57.5, reps = 8)), machineLog(lat).thisSession)
    }

    // Skipping a machine for today

    private val routines by lazy { DefaultRoutineRepository(store) }

    /** A routine (Treino A) with these machines in order: the next one, so the active ficha. */
    private suspend fun fichaWith(vararg machineIds: Long): Long =
        routines.saveRoutine(Routine(0, 0, "Treino A", "A", 0, emptySet()), machineIds.map { RoutineItem(0, it, 0, 3, 8, 12) })

    @Test
    fun `a skipped machine leaves the active ficha and is listed as skipped`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val curl = machine("Leg curl", MuscleGroup.LEGS)
        fichaWith(press, curl)

        training.skipMachineToday(curl)

        val plan = training.observeTodayPlan().first()
        assertEquals(listOf(press), plan.active?.items?.map { it.machineId })
        assertEquals(setOf(curl), plan.skipped)
    }

    @Test
    fun `unskipping a machine puts it back in the active ficha`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val curl = machine("Leg curl", MuscleGroup.LEGS)
        fichaWith(press, curl)
        training.skipMachineToday(curl)

        training.unskipMachineToday(curl)

        val plan = training.observeTodayPlan().first()
        assertEquals(listOf(press, curl), plan.active?.items?.map { it.machineId })
        assertEquals(emptySet<Long>(), plan.skipped)
    }

    @Test
    fun `a skipped machine has no target on its machine screen`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val curl = machine("Leg curl", MuscleGroup.LEGS)
        fichaWith(press, curl)
        training.skipMachineToday(curl)

        assertNull(machineLog(curl).target)
    }

    @Test
    fun `logging a set keeps the skipped machines skipped`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val curl = machine("Leg curl", MuscleGroup.LEGS)
        fichaWith(press, curl)
        training.skipMachineToday(curl)

        logSet(press, SetDraft(120.0, 12))

        assertEquals(setOf(curl), training.observeTodayPlan().first().skipped)
    }

    @Test
    fun `finishing the session clears the skipped machines`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val curl = machine("Leg curl", MuscleGroup.LEGS)
        fichaWith(press, curl)
        logSet(press, SetDraft(120.0, 12))
        training.skipMachineToday(curl)

        training.finishSession()

        val plan = training.observeTodayPlan().first()
        assertEquals(emptySet<Long>(), plan.skipped)
        assertEquals(listOf(press, curl), plan.active?.items?.map { it.machineId })
    }

    @Test
    fun `a session closed as stale clears the skipped machines`() = runTest {
        val press = machine("Leg press", MuscleGroup.LEGS)
        val curl = machine("Leg curl", MuscleGroup.LEGS)
        fichaWith(press, curl)
        logSet(press, SetDraft(120.0, 12))
        training.skipMachineToday(curl)

        clock.advance(Duration.ofHours(3))
        training.closeStaleSession()

        assertEquals(emptySet<Long>(), training.observeTodayPlan().first().skipped)
    }
}

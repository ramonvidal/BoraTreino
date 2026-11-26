package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** A session exists only while it has sets: deleting the last set removes the session, and Undo brings both back. */
class SessionWithSetsTest {
    private val t0: Instant = Instant.parse("2026-11-09T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var routines: RoutineRepository
    private lateinit var training: TrainingRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(t0)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun logSet(machineId: Long, weightKg: Double = 60.0): WorkoutSet =
        training.logSet(machineId, SetDraft(weightKg, 10)).set

    @Test
    fun `deleting the only set of a session deletes the session too`() = runTest {
        val leg = machine("Leg press")
        val set = logSet(leg)

        val removed = training.deleteSet(set)

        assertEquals(listOf(set), removed.sets)
        assertNotNull(removed.session)
        assertEquals(set.sessionId, removed.session?.id)
        assertTrue(store.allSessions().isEmpty())
        assertNull(training.observeOpenSession().first())
    }

    @Test
    fun `restoring the removed sets brings back the session and the set with the same ids and start`() = runTest {
        val leg = machine("Leg press")
        val set = logSet(leg)
        val removed = training.deleteSet(set)

        training.restoreSets(removed)

        val session = store.allSessions().single()
        assertEquals(set.sessionId, session.id)
        assertEquals(t0, session.startedAt)
        assertEquals(listOf(set), store.allSets())
        assertEquals(set.sessionId, requireNotNull(training.observeOpenSession().first()).session.id)
    }

    @Test
    fun `deleting one of two sets keeps the session and reports no session removed`() = runTest {
        val leg = machine("Leg press")
        val first = logSet(leg, 60.0)
        clock.advance(Duration.ofMinutes(2))
        logSet(leg, 62.5)

        val removed = training.deleteSet(first)

        assertEquals(listOf(first), removed.sets)
        assertNull(removed.session)
        assertEquals(1, store.allSessions().size)
        assertEquals(first.sessionId, requireNotNull(training.observeOpenSession().first()).session.id)
    }

    @Test
    fun `discarding the only machine of the session deletes the session, and undo brings it back`() = runTest {
        val leg = machine("Leg press")
        val set = logSet(leg)

        val removed = training.discardMachineToday(leg)

        assertEquals(listOf(set), removed.sets)
        assertNotNull(removed.session)
        assertTrue(store.allSessions().isEmpty())
        assertNull(training.observeOpenSession().first())

        training.undoDiscardMachineToday(leg, removed)

        assertEquals(set.sessionId, store.allSessions().single().id)
        assertEquals(listOf(set), store.allSets())
        assertNotNull(training.observeOpenSession().first())
    }

    @Test
    fun `logging again after the only set was deleted starts a session at the new set's time`() = runTest {
        val leg = machine("Leg press")
        val first = logSet(leg)
        training.deleteSet(first)
        clock.advance(Duration.ofMinutes(30))

        val second = logSet(leg)

        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(clock.now, open.session.startedAt)
        assertEquals(second.loggedAt, open.session.startedAt)
        assertEquals(1, store.allSessions().size)
        assertNotEquals(first.sessionId, second.sessionId)
    }

    @Test
    fun `finishing a session that has no sets returns null and deletes it`() = runTest {
        store.insertSession(Session(0, t0, null, null, null))

        assertNull(training.finishSession())

        assertTrue(store.allSessions().isEmpty())
        assertNull(training.observeOpenSession().first())
    }

    @Test
    fun `the routine choice survives deleting the last set`() = runTest {
        val leg = machine("Leg press")
        val a = routines.saveRoutine(
            Routine(0, 0, "Treino A", "A", 0, emptySet()),
            listOf(RoutineItem(0, leg, 0, 3, 8, 12)),
        )
        training.chooseRoutine(RoutineChoice.Chosen(a))
        val set = logSet(leg)

        training.deleteSet(set)

        assertEquals(RoutineChoice.Chosen(a), training.observeTodayPlan().first().choice)
    }

    @Test
    fun `restoring when another session is open brings the old one back closed`() = runTest {
        val leg = machine("Leg press")
        val old = logSet(leg)
        val removed = training.deleteSet(old)
        clock.advance(Duration.ofHours(4))
        val other = logSet(leg, 70.0)

        training.restoreSets(removed)

        val restored = store.allSessions().first { it.id == old.sessionId }
        assertNotNull(restored.endedAt)
        assertEquals(t0, restored.startedAt)
        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(other.sessionId, open.session.id)
        assertTrue(store.allSets().any { it.id == old.id })
    }
}

package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
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

/** Automatic finish of a training when its routine is complete, the pending summary and discarding a machine's sets. */
class RoutineAutoFinishTest {
    private val t0: Instant = Instant.parse("2026-11-09T18:00:00Z")
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
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    /** One routine with the given machines, each with [targetSets] sets; it is the suggested routine. */
    private suspend fun ficha(targetSets: Int, vararg machineIds: Long): Long = routines.saveRoutine(
        Routine(0, 0, "Treino A", "A", 0, emptySet()),
        machineIds.mapIndexed { i, id -> RoutineItem(0, id, i, targetSets, 8, 12) },
    )

    private suspend fun log(machineId: Long, times: Int) {
        repeat(times) {
            clock.advance(Duration.ofMinutes(2))
            training.logSet(machineId, SetDraft(60.0, 10))
        }
    }

    private suspend fun setsOf(machineId: Long): List<WorkoutSet> =
        store.allSets().filter { it.machineId == machineId }

    // finishIfRoutineComplete

    @Test
    fun `finishes the training when the last programmed exercise is done`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Leg curl")
        ficha(2, press, curl)
        log(press, 2)
        log(curl, 1)
        assertNull(training.observeOpenSession().first()?.session?.endedAt)

        log(curl, 1)
        val summary = training.finishIfRoutineComplete()

        assertNotNull(summary)
        assertEquals(2, summary!!.machineCount)
        assertEquals(4, summary.setCount)
        assertTrue(store.allSessions().single().endedAt != null)
        assertNull(training.observeOpenSession().first())
    }

    @Test
    fun `does nothing while a programmed exercise still has sets to do`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Leg curl")
        ficha(3, press, curl)
        log(press, 3)
        log(curl, 2)

        assertNull(training.finishIfRoutineComplete())
        assertNotNull(training.observeOpenSession().first())
        assertNull(store.allSessions().single().endedAt)
    }

    @Test
    fun `does nothing without an open session`() = runTest {
        val press = machine("Leg press")
        ficha(1, press)

        assertNull(training.finishIfRoutineComplete())
        assertNull(training.pendingSummarySessionId.first())
    }

    @Test
    fun `does nothing for a free training with no routine`() = runTest {
        val press = machine("Leg press")
        ficha(1, press)
        training.chooseRoutine(RoutineChoice.Free)
        log(press, 3)

        assertNull(training.finishIfRoutineComplete())
        assertNotNull(training.observeOpenSession().first())
        assertNull(training.pendingSummarySessionId.first())
    }

    @Test
    fun `a machine skipped today counts as done`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Leg curl")
        ficha(2, press, curl)
        log(press, 2)
        training.skipMachineToday(curl)

        val summary = training.finishIfRoutineComplete()

        assertNotNull(summary)
        assertEquals(1, summary!!.machineCount)
        assertEquals(2, summary.setCount)
    }

    @Test
    fun `warm-up sets do not complete a machine`() = runTest {
        val press = machine("Leg press")
        ficha(2, press)
        training.logWarmups(press, listOf(SetDraft(20.0, 10), SetDraft(30.0, 8), SetDraft(40.0, 5)))
        log(press, 1)

        assertNull(training.finishIfRoutineComplete())
        assertNotNull(training.observeOpenSession().first())
    }

    @Test
    fun `after a reopen the same session never finishes automatically again`() = runTest {
        val press = machine("Leg press")
        ficha(1, press)
        log(press, 1)
        val summary = requireNotNull(training.finishIfRoutineComplete())
        training.reopenSession(summary.sessionId)

        assertNull(training.finishIfRoutineComplete())
        log(press, 1)
        assertNull(training.finishIfRoutineComplete())
        assertNull(store.allSessions().single().endedAt)
    }

    @Test
    fun `the owner finishing by hand still works after a reopen`() = runTest {
        val press = machine("Leg press")
        ficha(1, press)
        log(press, 1)
        val summary = requireNotNull(training.finishIfRoutineComplete())
        training.reopenSession(summary.sessionId)

        val manual = training.finishSession()

        assertEquals(summary.sessionId, manual?.sessionId)
        assertNotNull(store.allSessions().single().endedAt)
    }

    // pendingSummarySessionId

    @Test
    fun `no pending summary at the start`() = runTest {
        assertNull(training.pendingSummarySessionId.first())
    }

    @Test
    fun `finishSession sets the pending summary to its session`() = runTest {
        val press = machine("Leg press")
        ficha(5, press)
        log(press, 1)

        val summary = requireNotNull(training.finishSession())

        assertEquals(summary.sessionId, training.pendingSummarySessionId.first())
    }

    @Test
    fun `finishIfRoutineComplete sets the pending summary to its session`() = runTest {
        val press = machine("Leg press")
        ficha(1, press)
        log(press, 1)

        val summary = requireNotNull(training.finishIfRoutineComplete())

        assertEquals(summary.sessionId, training.pendingSummarySessionId.first())
    }

    @Test
    fun `consumePendingSummary clears it`() = runTest {
        val press = machine("Leg press")
        ficha(1, press)
        log(press, 1)
        training.finishIfRoutineComplete()

        training.consumePendingSummary()

        assertNull(training.pendingSummarySessionId.first())
    }

    @Test
    fun `reopenSession clears the pending summary`() = runTest {
        val press = machine("Leg press")
        ficha(1, press)
        log(press, 1)
        val summary = requireNotNull(training.finishSession())

        training.reopenSession(summary.sessionId)

        assertNull(training.pendingSummarySessionId.first())
    }

    @Test
    fun `finishing with nothing open leaves no pending summary`() = runTest {
        assertNull(training.finishSession())
        assertNull(training.pendingSummarySessionId.first())
    }

    // discardMachineToday / undoDiscardMachineToday

    @Test
    fun `discard deletes only that machine's sets in the open session and hides it`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Leg curl")
        ficha(3, press, curl)
        log(press, 2)
        log(curl, 1)

        val removed = training.discardMachineToday(press)

        assertEquals(2, removed.size)
        assertEquals(emptyList<WorkoutSet>(), setsOf(press))
        assertEquals(1, setsOf(curl).size)
        assertEquals(setOf(press), training.observeTodayPlan().first().skipped)
        assertNotNull(training.observeOpenSession().first())
    }

    @Test
    fun `discard returns the deleted sets in their order`() = runTest {
        val press = machine("Leg press")
        ficha(3, press)
        log(press, 3)
        val before = setsOf(press)

        val removed = training.discardMachineToday(press)

        assertEquals(before.map { it.id }, removed.map { it.id })
    }

    @Test
    fun `discard with no open session returns nothing and hides nothing`() = runTest {
        val press = machine("Leg press")
        ficha(3, press)

        assertEquals(emptyList<WorkoutSet>(), training.discardMachineToday(press))
        assertEquals(emptySet<Long>(), training.observeTodayPlan().first().skipped)
    }

    @Test
    fun `undo restores the discarded sets and shows the machine again`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Leg curl")
        ficha(3, press, curl)
        log(press, 2)
        log(curl, 1)
        val before = setsOf(press)
        val removed = training.discardMachineToday(press)

        training.undoDiscardMachineToday(press, removed)

        assertEquals(before, setsOf(press))
        assertEquals(emptySet<Long>(), training.observeTodayPlan().first().skipped)
    }

    @Test
    fun `a machine discarded today counts as done for the routine`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Leg curl")
        ficha(2, press, curl)
        log(press, 2)
        training.discardMachineToday(press)
        log(curl, 2)

        // Press is hidden, so it counts as done: the training finishes with curl's two sets.
        val summary = training.finishIfRoutineComplete()

        assertEquals(1, summary?.machineCount)
        assertEquals(2, summary?.setCount)
    }
}

package com.boratreino.core.data

import com.boratreino.core.model.Addition
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetType
import com.boratreino.core.model.SetsChange
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RoutineUpdateRepositoryTest {
    private val t0: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var routines: RoutineRepository
    private lateinit var training: TrainingRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, TestClock(t0), routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    /** A routine with the given machines and target sets, in that order; returns its id. */
    private suspend fun routineWith(vararg targets: Pair<Long, Int>): Long =
        routines.saveRoutine(
            Routine(id = 0, planId = 0, name = "Legs", shortName = "A", position = 0, weekdays = emptySet()),
            targets.mapIndexed { i, (machineId, sets) ->
                RoutineItem(routineId = 0, machineId = machineId, position = i, targetSets = sets, repsMin = 8, repsMax = 12)
            },
        )

    /** A finished session on [routineId] (null for a free session) with [sets] normal sets per machine, then [warmups]. */
    private suspend fun session(
        routineId: Long?,
        sets: List<Pair<Long, Int>>,
        warmups: List<Pair<Long, Int>> = emptyList(),
    ): Long {
        val sessionId = store.insertSession(Session(0, t0, t0.plusSeconds(3_600), routineId, null))
        var position = 0
        var second = 0L
        suspend fun log(machineId: Long, type: SetType) {
            store.insertSet(
                WorkoutSet(
                    id = 0, sessionId = sessionId, machineId = machineId, position = position++,
                    weightKg = 20.0, reps = 10, seconds = null, calories = null, distanceM = null,
                    loggedAt = t0.plusSeconds(second++), type = type,
                ),
            )
        }
        for ((machineId, count) in sets) repeat(count) { log(machineId, SetType.NORMAL) }
        for ((machineId, count) in warmups) repeat(count) { log(machineId, SetType.WARMUP) }
        return sessionId
    }

    private suspend fun items(routineId: Long) = store.routineItems(routineId)

    // updateRoutineFromSession

    @Test
    fun `finishing a routine session updates the targets and reports what changed`() = runTest {
        val legs = machine("Leg press")
        val calf = machine("Standing calf raise")
        val routine = routineWith(legs to 3, calf to 2)
        val before = items(routine)
        val sessionId = session(routine, listOf(legs to 4, calf to 2))

        val update = routines.updateRoutineFromSession(routine, sessionId)

        assertEquals(listOf(SetsChange(legs, from = 3, to = 4)), update.setChanges)
        assertTrue(update.additions.isEmpty())
        assertEquals(before, update.previousItems)
        assertEquals(listOf(4, 2), items(routine).map { it.targetSets })
        assertEquals(listOf(legs, calf), items(routine).map { it.machineId })
    }

    @Test
    fun `the update keeps the rep ranges and the order of the routine`() = runTest {
        val legs = machine("Leg press")
        val calf = machine("Standing calf raise")
        val routine = routines.saveRoutine(
            Routine(0, 0, "Legs", "A", 0, emptySet()),
            listOf(
                RoutineItem(0, calf, 0, 2, 15, 20),
                RoutineItem(0, legs, 1, 3, 6, 8),
            ),
        )
        val sessionId = session(routine, listOf(legs to 5, calf to 2))

        routines.updateRoutineFromSession(routine, sessionId)

        assertEquals(listOf(calf, legs), items(routine).map { it.machineId })
        assertEquals(listOf(15, 6), items(routine).map { it.repsMin })
        assertEquals(listOf(20, 8), items(routine).map { it.repsMax })
        assertEquals(listOf(0, 1), items(routine).map { it.position })
        assertEquals(5, items(routine).last().targetSets)
    }

    @Test
    fun `a machine trained but not in the routine is added at the end with the default rep range`() = runTest {
        val legs = machine("Leg press")
        val extra = machine("Hip thrust")
        val routine = routineWith(legs to 3)
        val sessionId = session(routine, listOf(legs to 3, extra to 2))

        val update = routines.updateRoutineFromSession(routine, sessionId)

        assertEquals(listOf(Addition(extra, 2)), update.additions)
        val added = items(routine).last()
        assertEquals(extra, added.machineId)
        assertEquals(2, added.targetSets)
        assertEquals(8, added.repsMin)
        assertEquals(12, added.repsMax)
        assertEquals(listOf(0, 1), items(routine).map { it.position })
    }

    @Test
    fun `a target above ten is kept at ten`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val sessionId = session(routine, listOf(legs to 12))

        routines.updateRoutineFromSession(routine, sessionId)

        assertEquals(10, items(routine).single().targetSets)
    }

    @Test
    fun `warm-up sets do not change the routine`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val before = items(routine)
        val sessionId = session(routine, listOf(legs to 3), warmups = listOf(legs to 4))

        val update = routines.updateRoutineFromSession(routine, sessionId)

        assertTrue(update.isEmpty)
        assertEquals(before, items(routine))
    }

    @Test
    fun `a session that matches the routine changes nothing and says so`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val before = items(routine)
        val sessionId = session(routine, listOf(legs to 3))

        val update = routines.updateRoutineFromSession(routine, sessionId)

        assertTrue(update.isEmpty)
        assertTrue(update.previousItems.isEmpty())
        assertEquals(before, items(routine))
    }

    @Test
    fun `a routine that no longer exists gives an empty update`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val sessionId = session(routine, listOf(legs to 5))
        routines.deleteRoutine(routine)

        val update = routines.updateRoutineFromSession(routine, sessionId)

        assertTrue(update.isEmpty)
    }

    @Test
    fun `a session that no longer exists gives an empty update`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val before = items(routine)

        val update = routines.updateRoutineFromSession(routine, 999L)

        assertTrue(update.isEmpty)
        assertEquals(before, items(routine))
    }

    // undoRoutineUpdate

    @Test
    fun `undo puts the routine back as it was before the update`() = runTest {
        val legs = machine("Leg press")
        val extra = machine("Hip thrust")
        val routine = routineWith(legs to 3)
        val before = items(routine)
        val sessionId = session(routine, listOf(legs to 5, extra to 2))
        val update = routines.updateRoutineFromSession(routine, sessionId)
        assertTrue(items(routine) != before)

        routines.undoRoutineUpdate(routine, update.previousItems)

        assertEquals(before, items(routine))
    }

    @Test
    fun `undo after the routine was deleted does nothing`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val sessionId = session(routine, listOf(legs to 5))
        val update = routines.updateRoutineFromSession(routine, sessionId)
        routines.deleteRoutine(routine)

        routines.undoRoutineUpdate(routine, update.previousItems)

        assertTrue(items(routine).isEmpty())
        assertNull(training.sessionRoutineId(sessionId))
    }

    // What Today offers

    @Test
    fun `the offer is the update the routine would get, and asking for it changes nothing`() = runTest {
        val legs = machine("Leg press")
        val extra = machine("Hip thrust")
        val routine = routineWith(legs to 3)
        val before = items(routine)
        val sessionId = session(routine, listOf(legs to 4, extra to 1))

        val offer = training.routineUpdateOffer(sessionId)

        assertEquals(listOf(SetsChange(legs, 3, 4)), offer?.setChanges)
        assertEquals(listOf(Addition(extra, 1)), offer?.additions)
        assertEquals(before, items(routine))
    }

    @Test
    fun `a free session has no offer and no routine`() = runTest {
        val legs = machine("Leg press")
        val sessionId = session(null, listOf(legs to 3))

        assertNull(training.routineUpdateOffer(sessionId))
        assertNull(training.sessionRoutineId(sessionId))
    }

    @Test
    fun `a routine session that matches its routine has no offer`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val sessionId = session(routine, listOf(legs to 3))

        assertNull(training.routineUpdateOffer(sessionId))
        assertEquals(routine, training.sessionRoutineId(sessionId))
    }

    @Test
    fun `the session routine is the one it was trained on`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val sessionId = session(routine, listOf(legs to 4))

        assertEquals(routine, training.sessionRoutineId(sessionId))
        assertNull(training.sessionRoutineId(999L))
    }

    @Test
    fun `once the routine is deleted the session has no routine and no offer`() = runTest {
        val legs = machine("Leg press")
        val routine = routineWith(legs to 3)
        val sessionId = session(routine, listOf(legs to 4))

        routines.deleteRoutine(routine)

        assertNull(training.sessionRoutineId(sessionId))
        assertNull(training.routineUpdateOffer(sessionId))
    }
}

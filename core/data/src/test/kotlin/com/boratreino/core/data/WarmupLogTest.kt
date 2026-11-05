package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Logging the suggested warm-up sets in one go, and undoing them. */
class WarmupLogTest {
    private val now: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var training: TrainingRepository
    private lateinit var routines: RoutineRepository
    private lateinit var machines: MachineRepository

    private val plan = listOf(
        SetDraft(50.0, 8, SetType.WARMUP),
        SetDraft(70.0, 5, SetType.WARMUP),
        SetDraft(85.0, 3, SetType.WARMUP),
    )

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(now)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.BACK, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun routine(machineId: Long): Long = routines.saveRoutine(
        Routine(id = 0, planId = 0, name = "Routine A", shortName = "A", position = 0, weekdays = emptySet()),
        listOf(RoutineItem(0, machineId, 0, 3, 8, 12)),
    )

    private fun setsOf(machineId: Long) = store.allSets().filter { it.machineId == machineId }.sortedBy { it.position }

    @Test
    fun `with no open session one is opened and gets the warm-ups`() = runTest {
        val press = machine("Leg press")

        val outcome = training.logWarmups(press, plan)

        val sessions = store.allSessions()
        assertEquals(1, sessions.size)
        assertEquals(null, sessions.single().endedAt)
        assertEquals(sessions.single().id, outcome.sessionId)
        assertTrue(outcome.sessionCreated)
        assertEquals(3, setsOf(press).size)
        assertTrue(setsOf(press).all { it.sessionId == outcome.sessionId })
    }

    @Test
    fun `the new session belongs to the chosen routine`() = runTest {
        val press = machine("Leg press")
        val routineA = routine(press)
        training.chooseRoutine(RoutineChoice.Chosen(routineA))

        training.logWarmups(press, plan)

        assertEquals(routineA, store.allSessions().single().routineId)
    }

    @Test
    fun `an open session is used and not marked as created`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Curl")
        val open = store.insertSession(Session(id = 0, startedAt = now.minusSeconds(600), endedAt = null, routineId = null, note = null))
        training.logWarmups(curl, listOf(SetDraft(10.0, 10)))

        val outcome = training.logWarmups(press, plan)

        assertEquals(1, store.allSessions().size)
        assertEquals(open, outcome.sessionId)
        assertEquals(false, outcome.sessionCreated)
    }

    @Test
    fun `every set is written as a warm-up whatever the draft says`() = runTest {
        val press = machine("Leg press")
        val mixed = listOf(
            SetDraft(50.0, 8, SetType.NORMAL),
            SetDraft(70.0, 5, SetType.FAILURE),
            SetDraft(85.0, 3, SetType.WARMUP),
        )

        training.logWarmups(press, mixed)

        assertEquals(listOf(SetType.WARMUP, SetType.WARMUP, SetType.WARMUP), setsOf(press).map { it.type })
        assertEquals(listOf(50.0, 70.0, 85.0), setsOf(press).map { it.weightKg })
        assertEquals(listOf(8, 5, 3), setsOf(press).map { it.reps })
    }

    @Test
    fun `positions continue after the sets the machine already has`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Curl")
        training.logSet(press, SetDraft(60.0, 10))
        training.logSet(curl, SetDraft(20.0, 10))

        training.logWarmups(press, plan)

        assertEquals(listOf(0, 1, 2, 3), setsOf(press).map { it.position })
        assertEquals(listOf(0), setsOf(curl).map { it.position })
    }

    @Test
    fun `logged at increases with the order of the sets`() = runTest {
        val press = machine("Leg press")

        training.logWarmups(press, plan)

        val times = setsOf(press).map { it.loggedAt }
        assertEquals(times.sorted(), times)
        assertEquals(3, times.distinct().size)
        assertEquals(now, times.first())
    }

    @Test
    fun `the outcome lists the new set ids and counts one machine`() = runTest {
        val press = machine("Leg press")

        val outcome = training.logWarmups(press, plan)

        assertEquals(setsOf(press).map { it.id }, outcome.setIds)
        assertEquals(1, outcome.machineCount)
    }

    @Test
    fun `undo deletes the sets and the session it created`() = runTest {
        val press = machine("Leg press")
        val outcome = training.logWarmups(press, plan)

        training.undoRepeat(outcome)

        assertTrue(store.allSets().isEmpty())
        assertTrue(store.allSessions().isEmpty())
    }

    @Test
    fun `undo keeps a session that was already open`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Curl")
        training.logSet(curl, SetDraft(20.0, 10))
        val outcome = training.logWarmups(press, plan)

        training.undoRepeat(outcome)

        assertEquals(1, store.allSessions().size)
        assertEquals(1, store.allSets().size)
        assertTrue(setsOf(press).isEmpty())
    }

    @Test
    fun `undo keeps a created session that has other sets by now`() = runTest {
        val press = machine("Leg press")
        val curl = machine("Curl")
        val outcome = training.logWarmups(press, plan)
        training.logSet(curl, SetDraft(20.0, 10))

        training.undoRepeat(outcome)

        assertEquals(1, store.allSessions().size)
        assertEquals(1, store.allSets().size)
    }

    @Test
    fun `a stale session is closed and a new one gets the warm-ups`() = runTest {
        val press = machine("Leg press")
        val stale = store.insertSession(
            Session(id = 0, startedAt = now.minus(Duration.ofHours(5)), endedAt = null, routineId = null, note = null),
        )

        val outcome = training.logWarmups(press, plan)

        val old = store.allSessions().single { it.id == stale }
        assertEquals(now.minus(Duration.ofHours(5)), old.endedAt)
        assertTrue(outcome.sessionId != stale)
        assertTrue(outcome.sessionCreated)
        assertTrue(setsOf(press).all { it.sessionId == outcome.sessionId })
    }
}

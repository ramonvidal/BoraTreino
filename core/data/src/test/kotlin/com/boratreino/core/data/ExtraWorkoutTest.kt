package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.SetDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** An extra workout is flagged on its session, does not move the rotation, and follows the owner's choice. */
class ExtraWorkoutTest {
    // Monday, 2 November 2026, evening.
    private val monday: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var routines: RoutineRepository
    private lateinit var training: TrainingRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(monday)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, false))

    /** Saves a rotation routine with the given machine (3 x 8-12); returns its id. */
    private suspend fun routine(shortName: String, machineId: Long): Long = routines.saveRoutine(
        Routine(id = 0, planId = 0, name = "Routine $shortName", shortName = shortName, position = 0, weekdays = emptySet()),
        listOf(RoutineItem(0, machineId, 0, 3, 8, 12)),
    )

    private suspend fun plan() = training.observeTodayPlan().first()

    private suspend fun openSession() = training.observeOpenSession().first()?.session

    /** Logs one set on [machineId] at the clock's time and finishes the session. */
    private suspend fun trainAndFinish(machineId: Long) {
        training.logSet(machineId, SetDraft(100.0, 10))
        training.finishSession()
    }

    @Test
    fun `a session opened with an extra choice is flagged extra`() = runTest {
        val pulldown = machine("Lat pulldown")
        val b = routine("B", pulldown)

        training.chooseRoutine(RoutineChoice.Chosen(b, extra = true))
        training.logSet(pulldown, SetDraft(60.0, 10))

        val session = openSession()
        assertEquals(b, session?.routineId)
        assertTrue(session?.isExtra == true)
    }

    @Test
    fun `a session opened with a normal choice is not extra`() = runTest {
        val pulldown = machine("Lat pulldown")
        val b = routine("B", pulldown)

        training.chooseRoutine(RoutineChoice.Chosen(b))
        training.logSet(pulldown, SetDraft(60.0, 10))

        assertEquals(b, openSession()?.routineId)
        assertFalse(openSession()?.isExtra == true)
    }

    @Test
    fun `a suggested session is not extra`() = runTest {
        val press = machine("Leg press")
        routine("A", press)

        training.logSet(press, SetDraft(100.0, 10))

        assertFalse(openSession()?.isExtra == true)
    }

    @Test
    fun `an extra workout of the due routine does not move the rotation`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        routine("B", machine("Lat pulldown"))

        training.chooseRoutine(RoutineChoice.Chosen(a, extra = true))
        trainAndFinish(press)

        assertEquals("A", plan().suggested?.shortName)
    }

    @Test
    fun `the suggestion after an extra B still follows the last normal A`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        val pulldown = machine("Lat pulldown")
        val b = routine("B", pulldown)
        trainAndFinish(press) // Monday: A, the normal one.
        assertEquals("B", plan().suggested?.shortName)
        clock.advance(Duration.ofHours(1))

        training.chooseRoutine(RoutineChoice.Chosen(b, extra = true))
        trainAndFinish(pulldown)

        val extraSession = store.allSessions().last()
        assertEquals(b, extraSession.routineId)
        assertTrue(extraSession.isExtra)
        // A was the last rotation routine, so B is still the one due.
        assertEquals("B", plan().suggested?.shortName)
    }

    @Test
    fun `choosing a routine on an open session flags it extra and unflags it again`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        training.logSet(press, SetDraft(100.0, 10))
        assertFalse(openSession()?.isExtra == true)

        training.chooseRoutine(RoutineChoice.Chosen(a, extra = true))
        assertTrue(openSession()?.isExtra == true)
        assertEquals(a, openSession()?.routineId)

        training.chooseRoutine(RoutineChoice.Chosen(a))
        assertFalse(openSession()?.isExtra == true)
    }

    @Test
    fun `choosing Suggested on an open extra session clears its extra flag`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        training.chooseRoutine(RoutineChoice.Chosen(a, extra = true))
        training.logSet(press, SetDraft(100.0, 10))
        assertTrue(openSession()?.isExtra == true)

        training.chooseRoutine(RoutineChoice.Suggested)

        assertFalse(openSession()?.isExtra == true)
    }

    @Test
    fun `choosing Free on an open extra session leaves it without a routine and not extra`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        training.chooseRoutine(RoutineChoice.Chosen(a, extra = true))
        training.logSet(press, SetDraft(100.0, 10))

        training.chooseRoutine(RoutineChoice.Free)

        assertNull(openSession()?.routineId)
        assertFalse(openSession()?.isExtra == true)
    }
}

package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Filling a past or empty session from the last session of its routine. */
class RepeatHistoryTest {
    private val now: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private val day1: Instant = Instant.parse("2026-10-26T18:00:00Z")
    private val day2: Instant = Instant.parse("2026-10-28T18:00:00Z")
    private val day3: Instant = Instant.parse("2026-10-30T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var history: HistoryRepository
    private lateinit var routines: RoutineRepository
    private lateinit var machines: MachineRepository

    private var press = 0L
    private var lat = 0L
    private var routineA = 0L

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        val clock = TestClock(now)
        routines = DefaultRoutineRepository(store)
        val training = DefaultTrainingRepository(store, clock, routines)
        history = DefaultHistoryRepository(store, clock, training)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.BACK, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun routine(shortName: String, vararg machineIds: Long): Long = routines.saveRoutine(
        Routine(id = 0, planId = 0, name = "Routine $shortName", shortName = shortName, position = 0, weekdays = emptySet()),
        machineIds.mapIndexed { i, id -> RoutineItem(0, id, i, 3, 8, 12) },
    )

    private suspend fun session(startedAt: Instant, routineId: Long?, endedAt: Instant? = startedAt.plusSeconds(3600)): Long =
        store.insertSession(Session(id = 0, startedAt = startedAt, endedAt = endedAt, routineId = routineId, note = null))

    private suspend fun set(sessionId: Long, machineId: Long, position: Int, kg: Double, reps: Int, at: Instant) =
        store.insertSet(WorkoutSet(0, sessionId, machineId, position, kg, reps, null, null, null, at))

    private suspend fun fixtures() {
        press = machine("Leg press")
        lat = machine("Lat pulldown")
        routineA = routine("A", press, lat)
    }

    @Test
    fun `an empty session is filled from the latest earlier session of the same routine`() = runTest {
        fixtures()
        val old = session(day1, routineA)
        set(old, press, 0, 10.0, 10, day1)
        val last = session(day2, routineA)
        set(last, press, 0, 40.0, 10, day2)
        set(last, press, 1, 40.0, 9, day2.plusSeconds(60))
        set(last, lat, 0, 30.0, 12, day2.plusSeconds(120))
        val target = session(day3, routineA, endedAt = null)

        val saved = history.repeatLastSession(target, RepeatMode.SAME)

        assertEquals(3, saved.size)
        assertTrue(saved.all { it.sessionId == target && it.id != 0L })
        assertEquals(listOf(40.0, 40.0, 30.0), saved.map { it.weightKg })
        assertEquals(listOf(10, 9, 12), saved.map { it.reps })
        assertEquals(listOf(press, press, lat), saved.map { it.machineId })
        assertEquals(listOf(0, 1, 0), saved.map { it.position })
        assertEquals(3, store.allSets().count { it.sessionId == target })
    }

    @Test
    fun `a later session of the routine is not used`() = runTest {
        fixtures()
        val target = session(day2, routineA)
        val later = session(day3, routineA)
        set(later, press, 0, 99.0, 1, day3)

        assertTrue(history.repeatLastSession(target, RepeatMode.SAME).isEmpty())
    }

    @Test
    fun `an earlier session of another routine is not used`() = runTest {
        fixtures()
        val routineB = routine("B", press)
        val other = session(day1, routineB)
        set(other, press, 0, 40.0, 10, day1)
        val target = session(day2, routineA)

        assertTrue(history.repeatLastSession(target, RepeatMode.SAME).isEmpty())
    }

    @Test
    fun `machines that already have sets are left out`() = runTest {
        fixtures()
        val last = session(day1, routineA)
        set(last, press, 0, 40.0, 10, day1)
        set(last, lat, 0, 30.0, 12, day1.plusSeconds(60))
        val target = session(day2, routineA, endedAt = null)
        val existing = set(target, press, 0, 50.0, 5, day2)

        val saved = history.repeatLastSession(target, RepeatMode.SAME)

        assertEquals(listOf(lat), saved.map { it.machineId })
        assertEquals(2, store.allSets().count { it.sessionId == target })
        assertEquals(50.0, store.allSets().first { it.id == existing }.weightKg)
    }

    @Test
    fun `suggested mode applies the progression suggestion`() = runTest {
        fixtures()
        val last = session(day1, routineA)
        repeat(3) { set(last, press, it, 40.0, 12, day1.plusSeconds(60L * it)) }
        val target = session(day2, routineA, endedAt = null)

        val saved = history.repeatLastSession(target, RepeatMode.SUGGESTED)

        assertEquals(listOf(42.5, 42.5, 42.5), saved.map { it.weightKg })
        assertEquals(listOf(8, 8, 8), saved.map { it.reps })
    }

    @Test
    fun `a session without a routine gives an empty result`() = runTest {
        fixtures()
        val last = session(day1, routineA)
        set(last, press, 0, 40.0, 10, day1)
        val target = session(day2, null)

        assertTrue(history.repeatLastSession(target, RepeatMode.SAME).isEmpty())
        assertTrue(store.allSets().none { it.sessionId == target })
    }

    @Test
    fun `no earlier session of the routine gives an empty result`() = runTest {
        fixtures()
        val target = session(day2, routineA)

        assertTrue(history.repeatLastSession(target, RepeatMode.SAME).isEmpty())
    }

    @Test
    fun `a closed session stays closed and ends after its new sets`() = runTest {
        fixtures()
        val last = session(day1, routineA)
        set(last, press, 0, 40.0, 10, day1)
        set(last, press, 1, 40.0, 10, day1.plusSeconds(60))
        set(last, lat, 0, 30.0, 12, day1.plusSeconds(120))
        val originalEnd = day2.plus(Duration.ofSeconds(1))
        val target = session(day2, routineA, endedAt = originalEnd)

        val saved = history.repeatLastSession(target, RepeatMode.SAME)

        val after = requireNotNull(store.session(target))
        val newest = saved.maxOf { it.loggedAt }
        assertEquals(3, saved.size)
        assertTrue(after.endedAt != null)
        assertTrue(after.endedAt!! >= newest)
        assertTrue(after.endedAt!! > originalEnd)
    }

    @Test
    fun `an open session stays open`() = runTest {
        fixtures()
        val last = session(day1, routineA)
        set(last, press, 0, 40.0, 10, day1)
        val target = session(day2, routineA, endedAt = null)

        history.repeatLastSession(target, RepeatMode.SAME)

        assertNull(store.session(target)?.endedAt)
    }
}

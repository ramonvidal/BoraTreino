package com.boratreino.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant

/** Routines, their machines and the sessions that point at them, through the store and an in-memory database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomRoutineStoreRoundTripTest {
    private lateinit var db: BoraTreinoDatabase
    private lateinit var store: WorkoutStore
    private val t0 = Instant.ofEpochMilli(1_790_000_000_000L)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoraTreinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomWorkoutStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun machine(name: String): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, false))

    private suspend fun routine(planId: Long, name: String, shortName: String, position: Int, weekdays: Set<DayOfWeek> = emptySet()): Long =
        store.insertRoutine(Routine(0, planId, name, shortName, position, weekdays))

    @Test
    fun `the plan is created once, in turn`() = runTest {
        val first = store.ensurePlan()
        val second = store.ensurePlan()

        assertEquals(first.id, second.id)
        assertEquals(ScheduleMode.IN_TURN, first.scheduleMode)
        assertEquals(first, store.plan())
    }

    @Test
    fun `the schedule mode is stored and comes back`() = runTest {
        val plan = store.ensurePlan()

        store.setScheduleMode(plan.id, ScheduleMode.WEEKDAYS)

        assertEquals(ScheduleMode.WEEKDAYS, store.plan()?.scheduleMode)
        assertEquals(ScheduleMode.WEEKDAYS, store.observePlan().first()?.scheduleMode)
    }

    @Test
    fun `a routine comes back with its name, short name, position and weekdays`() = runTest {
        val plan = store.ensurePlan()
        val id = routine(plan.id, "Push day", "P", 2, setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))

        assertEquals(
            Routine(id, plan.id, "Push day", "P", 2, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            store.observeRoutine(id).first(),
        )
    }

    @Test
    fun `a routine with no weekdays comes back with none`() = runTest {
        val plan = store.ensurePlan()
        val id = routine(plan.id, "Routine A", "A", 0)

        assertEquals(emptySet<DayOfWeek>(), store.observeRoutine(id).first()?.weekdays)
    }

    @Test
    fun `routines come back by position`() = runTest {
        val plan = store.ensurePlan()
        routine(plan.id, "Routine B", "B", 1)
        routine(plan.id, "Routine A", "A", 0)

        assertEquals(listOf("A", "B"), store.observeRoutines(plan.id).first().map { it.shortName })
    }

    @Test
    fun `routine items come back in position order and replacing them replaces all`() = runTest {
        val plan = store.ensurePlan()
        val press = machine("Leg press")
        val row = machine("Seated row")
        val id = routine(plan.id, "Routine A", "A", 0)

        store.replaceRoutineItems(
            id,
            listOf(
                RoutineItem(id, row, 1, 3, 8, 12),
                RoutineItem(id, press, 0, 4, 6, 10),
            ),
        )
        assertEquals(listOf(press, row), store.observeRoutineItems(id).first().map { it.machineId })
        assertEquals(listOf(0, 1), store.observeRoutineItems(id).first().map { it.position })

        store.replaceRoutineItems(id, listOf(RoutineItem(id, row, 0, 2, 10, 15)))

        assertEquals(
            listOf(RoutineItem(id, row, 0, 2, 10, 15)),
            store.observeRoutineItems(id).first(),
        )
    }

    @Test
    fun `deleting a routine removes its items and clears its sessions' routine but keeps their sets`() = runTest {
        val plan = store.ensurePlan()
        val press = machine("Leg press")
        val id = routine(plan.id, "Routine A", "A", 0)
        store.replaceRoutineItems(id, listOf(RoutineItem(id, press, 0, 3, 8, 12)))
        val sessionId = store.insertSession(Session(0, t0, null, id, null))
        val loggedAt = t0.plusSeconds(60)
        store.insertSet(WorkoutSet(0, sessionId, press, 0, 120.0, 12, null, null, null, loggedAt))

        store.deleteRoutine(id)

        assertNull(store.observeRoutine(id).first())
        assertEquals(emptyList<RoutineItem>(), store.observeRoutineItems(id).first())
        assertNull(store.observeOpenSession().first()?.routineId)
        assertEquals(1, store.observeMachineSets(sessionId, press).first().size)
    }

    @Test
    fun `the last routine is the one of the latest finished session that had one`() = runTest {
        val plan = store.ensurePlan()
        val a = routine(plan.id, "Routine A", "A", 0)
        val b = routine(plan.id, "Routine B", "B", 1)
        val earlier = store.insertSession(Session(0, t0, null, a, null))
        store.setSessionEnd(earlier, t0.plusSeconds(3600))
        val later = store.insertSession(Session(0, t0.plusSeconds(86_400), null, b, null))

        // The later session is still open, so it doesn't count yet.
        assertEquals(a, store.lastRoutineId())

        store.setSessionEnd(later, t0.plusSeconds(86_400 + 3600))
        assertEquals(b, store.lastRoutineId())
    }

    @Test
    fun `a session with no routine is not a last routine`() = runTest {
        val plan = store.ensurePlan()
        val a = routine(plan.id, "Routine A", "A", 0)
        val free = store.insertSession(Session(0, t0, null, null, null))
        store.setSessionEnd(free, t0.plusSeconds(3600))

        assertNull(store.lastRoutineId())

        val finishedWithA = store.insertSession(Session(0, t0.plusSeconds(7200), null, a, null))
        store.setSessionEnd(finishedWithA, t0.plusSeconds(9000))
        store.insertSession(Session(0, t0.plusSeconds(10_000), null, null, null))
        assertEquals(a, store.lastRoutineId())
    }

    @Test
    fun `setting a session's routine to null clears it`() = runTest {
        val plan = store.ensurePlan()
        val a = routine(plan.id, "Routine A", "A", 0)
        val sessionId = store.insertSession(Session(0, t0, null, a, null))

        store.setSessionRoutine(sessionId, null)

        assertNull(store.observeOpenSession().first()?.routineId)
    }
}

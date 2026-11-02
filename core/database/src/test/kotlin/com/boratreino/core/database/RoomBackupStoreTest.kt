package com.boratreino.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant

/** The backup export and replace on in-memory databases: the same data in, the same data out, ids kept. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomBackupStoreTest {
    private val databases = mutableListOf<BoraTreinoDatabase>()

    private fun newStore(): WorkoutStore {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoraTreinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases += db
        return RoomWorkoutStore(db)
    }

    private lateinit var store: WorkoutStore

    @Before
    fun setUp() {
        store = newStore()
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    /** Ids deliberately not 1..n, to show they are kept. Sorted the way the store reads them back. */
    private val backup = BackupData(
        machines = listOf(
            Machine(3, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, "Seat 4", 120, archived = false),
            Machine(8, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS, 2.5, null, null, archived = true),
        ),
        plans = listOf(RoutinePlan(2, ScheduleMode.WEEKDAYS)),
        routines = listOf(Routine(4, 2, "Routine \"A\"", "A", 0, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))),
        items = listOf(RoutineItem(routineId = 4, machineId = 8, position = 0, targetSets = 3, repsMin = 6, repsMax = 10)),
        sessions = listOf(
            Session(5, Instant.ofEpochMilli(1_790_000_000_000L), Instant.ofEpochMilli(1_790_003_600_000L), 4, "Note\nline"),
            Session(6, Instant.ofEpochMilli(1_790_100_000_000L), null, null, null),
        ),
        sets = listOf(
            WorkoutSet(40, 5, 3, 0, 120.0, 12, null, null, null, Instant.ofEpochMilli(1_790_000_060_000L)),
            WorkoutSet(41, 5, 8, 0, null, 8, null, null, null, Instant.ofEpochMilli(1_790_000_300_000L)),
        ),
    )

    /** Some rows the replace must get rid of. */
    private suspend fun fillWithOldData(target: WorkoutStore) {
        repeat(12) { i ->
            target.saveMachine(Machine(0, "Old machine $i", MuscleGroup.ARMS, MachineKind.WEIGHT_REPS, 1.0, null, null, archived = false))
        }
        val session = target.insertSession(Session(0, Instant.ofEpochMilli(1_700_000_000_000L), null, null, null))
        target.insertSet(WorkoutSet(0, session, 1, 0, 30.0, 10, null, null, null, Instant.ofEpochMilli(1_700_000_010_000L)))
    }

    @Test
    fun `an export imported into a fresh database gives the same data`() = runTest {
        store.replaceAll(backup)
        val exported = store.exportAll()
        val fresh = newStore()

        fresh.replaceAll(exported)

        assertEquals(exported, fresh.exportAll())
        assertEquals(backup, fresh.exportAll())
    }

    @Test
    fun `a replace over existing data leaves only the backup's rows`() = runTest {
        fillWithOldData(store)

        store.replaceAll(backup)

        assertEquals(backup, store.exportAll())
    }

    @Test
    fun `replaced rows keep their ids`() = runTest {
        fillWithOldData(store)

        store.replaceAll(backup)

        assertEquals(listOf(3L, 8L), store.exportAll().machines.map { it.id })
        assertEquals(listOf(40L, 41L), store.exportAll().sets.map { it.id })
        assertEquals(listOf(5L, 6L), store.exportAll().sessions.map { it.id })
    }

    @Test
    fun `a machine added after a replace gets an id above the highest old and backup id`() = runTest {
        fillWithOldData(store)

        store.replaceAll(backup)
        val added = store.saveMachine(Machine(0, "New machine", MuscleGroup.CORE, MachineKind.WEIGHT_REPS, 2.5, null, null, archived = false))

        assertTrue("added id $added should be above 12 (old) and 8 (backup)", added > 12L)
        assertEquals(backup.machines.map { it.id } + added, store.exportAll().machines.map { it.id })
    }

    @Test
    fun `a set added after a replace gets an id above the backup's sets`() = runTest {
        store.replaceAll(backup)

        val session = store.insertSession(Session(0, Instant.ofEpochMilli(1_790_200_000_000L), null, null, null))
        val id = store.insertSet(WorkoutSet(0, session, 3, 0, 100.0, 10, null, null, null, Instant.ofEpochMilli(1_790_200_060_000L)))

        assertTrue(id > 41L)
    }

    @Test
    fun `an empty backup empties the database`() = runTest {
        fillWithOldData(store)

        store.replaceAll(BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))

        assertEquals(BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), store.exportAll())
    }
}

package com.boratreino.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.ScheduleMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Planned workouts in the Room store: several routines per date (unique per date and routine), cleared one by one, removed with their routine, kept in backups. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPlannedWorkoutStoreTest {
    private lateinit var db: BoraTreinoDatabase
    private lateinit var store: WorkoutStore

    private val nov4 = LocalDate.of(2026, 11, 4)
    private val nov12 = LocalDate.of(2026, 11, 12)

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

    private suspend fun routine(shortName: String, position: Int): Long {
        val plan = store.ensurePlan()
        return store.insertRoutine(Routine(0, plan.id, "Routine $shortName", shortName, position, emptySet()))
    }

    @Test
    fun `nothing is planned in a new database`() = runTest {
        assertEquals(emptyList<PlannedWorkout>(), store.observePlannedWorkouts().first())
    }

    @Test
    fun `a planned workout comes back with its date and routine, in date order`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)

        store.setPlannedWorkout(nov12, a)
        store.setPlannedWorkout(nov4, b)

        assertEquals(
            listOf(PlannedWorkout(nov4, b), PlannedWorkout(nov12, a)),
            store.observePlannedWorkouts().first(),
        )
    }

    @Test
    fun `two routines planned on the same date are both kept, each at the end of the date's order`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)

        store.setPlannedWorkout(nov4, b)
        store.setPlannedWorkout(nov4, a)

        assertEquals(
            listOf(PlannedWorkout(nov4, b, 0), PlannedWorkout(nov4, a, 1)),
            store.observePlannedWorkouts().first(),
        )
    }

    @Test
    fun `a new plan takes the position after the highest one on its date, not after the number of plans`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        val c = routine("C", 2)
        store.setPlannedWorkout(nov4, a)
        store.setPlannedWorkout(nov4, b)
        store.setPlannedWorkout(nov4, c)
        store.reorderPlannedWorkouts(nov4, listOf(c))
        store.clearPlannedWorkout(nov4, a)

        store.setPlannedWorkout(nov4, a)

        // c is 0, b is 1, and a is added after the highest: 2.
        assertEquals(
            listOf(PlannedWorkout(nov4, c, 0), PlannedWorkout(nov4, b, 1), PlannedWorkout(nov4, a, 2)),
            store.observePlannedWorkouts().first(),
        )
    }

    @Test
    fun `reordering the routines of a date changes the order they are observed in`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        val c = routine("C", 2)
        store.setPlannedWorkout(nov4, a)
        store.setPlannedWorkout(nov4, b)
        store.setPlannedWorkout(nov4, c)

        store.reorderPlannedWorkouts(nov4, listOf(c, a, b))

        assertEquals(
            listOf(PlannedWorkout(nov4, c, 0), PlannedWorkout(nov4, a, 1), PlannedWorkout(nov4, b, 2)),
            store.observePlannedWorkouts().first(),
        )
        assertEquals(
            listOf(PlannedWorkout(nov4, c, 0), PlannedWorkout(nov4, a, 1), PlannedWorkout(nov4, b, 2)),
            store.exportAll().plannedWorkouts,
        )
    }

    @Test
    fun `a reorder ignores ids that are not planned on that date and leaves other dates alone`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        val c = routine("C", 2)
        store.setPlannedWorkout(nov4, a)
        store.setPlannedWorkout(nov4, b)
        store.setPlannedWorkout(nov12, c)

        store.reorderPlannedWorkouts(nov4, listOf(999L, c, b))
        store.reorderPlannedWorkouts(nov12, listOf(a))

        // c is not planned on the 4th, 999 does not exist: b takes 0, a keeps 0 and the tie goes by routine id.
        assertEquals(
            listOf(PlannedWorkout(nov4, a, 0), PlannedWorkout(nov4, b, 0), PlannedWorkout(nov12, c, 0)),
            store.observePlannedWorkouts().first(),
        )
    }

    @Test
    fun `a reorder with a partial list moves only the given routines`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        val c = routine("C", 2)
        store.setPlannedWorkout(nov4, a)
        store.setPlannedWorkout(nov4, b)
        store.setPlannedWorkout(nov4, c)

        store.reorderPlannedWorkouts(nov4, listOf(c))

        // c takes 0; a and b keep 0 and 1.
        assertEquals(
            listOf(PlannedWorkout(nov4, a, 0), PlannedWorkout(nov4, c, 0), PlannedWorkout(nov4, b, 1)),
            store.observePlannedWorkouts().first(),
        )
    }

    @Test
    fun `planned positions survive a backup round trip, read in the stored order`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        store.setPlannedWorkout(nov4, a)
        store.setPlannedWorkout(nov4, b)
        store.reorderPlannedWorkouts(nov4, listOf(b, a))

        val exported = store.exportAll()
        store.replaceAll(exported)

        assertEquals(listOf(PlannedWorkout(nov4, b, 0), PlannedWorkout(nov4, a, 1)), exported.plannedWorkouts)
        assertEquals(exported.plannedWorkouts, store.exportAll().plannedWorkouts)
        assertEquals(exported.plannedWorkouts, store.observePlannedWorkouts().first())
    }

    @Test
    fun `planning the same routine on the same date again is ignored`() = runTest {
        val a = routine("A", 0)
        store.setPlannedWorkout(nov4, a)

        store.setPlannedWorkout(nov4, a)

        assertEquals(listOf(PlannedWorkout(nov4, a)), store.observePlannedWorkouts().first())
        assertEquals(listOf(PlannedWorkout(nov4, a)), store.exportAll().plannedWorkouts)
    }

    @Test
    fun `clearing one routine of a date keeps the others planned there`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        store.setPlannedWorkout(nov4, a)
        store.setPlannedWorkout(nov4, b)

        store.clearPlannedWorkout(nov4, a)

        assertEquals(listOf(PlannedWorkout(nov4, b, 1)), store.observePlannedWorkouts().first())
    }

    @Test
    fun `clearing a routine that is not planned on that date is harmless`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        store.setPlannedWorkout(nov4, a)

        store.clearPlannedWorkout(nov12, a)
        store.clearPlannedWorkout(nov4, b)

        assertEquals(listOf(PlannedWorkout(nov4, a)), store.observePlannedWorkouts().first())
    }

    @Test
    fun `deleting a routine removes all its planned dates and keeps the others on those dates`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        store.setPlannedWorkout(nov4, a)
        store.setPlannedWorkout(nov4, b)
        store.setPlannedWorkout(nov12, a)

        store.deleteRoutine(a)

        assertEquals(listOf(PlannedWorkout(nov4, b, 1)), store.observePlannedWorkouts().first())
    }

    @Test
    fun `an export holds the planned workouts in date order`() = runTest {
        val a = routine("A", 0)
        val b = routine("B", 1)
        store.setPlannedWorkout(nov12, a)
        store.setPlannedWorkout(nov4, b)

        assertEquals(listOf(PlannedWorkout(nov4, b), PlannedWorkout(nov12, a)), store.exportAll().plannedWorkouts)
    }

    private val backup = BackupData(
        machines = emptyList(),
        plans = listOf(RoutinePlan(1, ScheduleMode.IN_TURN)),
        routines = listOf(
            Routine(1, 1, "Routine A", "A", 0, emptySet()),
            Routine(2, 1, "Routine B", "B", 1, emptySet()),
        ),
        items = emptyList(),
        sessions = emptyList(),
        sets = emptyList(),
        plannedWorkouts = listOf(
            PlannedWorkout(nov4, 2L),
            PlannedWorkout(nov12, 1L),
        ),
    )

    @Test
    fun `a backup's planned workouts come back on a fresh database`() = runTest {
        store.replaceAll(backup)
        val exported = store.exportAll()
        val freshDb = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoraTreinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val fresh = RoomWorkoutStore(freshDb)

            fresh.replaceAll(exported)

            assertEquals(backup.plannedWorkouts, fresh.exportAll().plannedWorkouts)
            assertEquals(backup, fresh.exportAll())
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun `a backup with two routines on one date replaces the store and comes back in routine order`() = runTest {
        val sameDay = backup.copy(
            plannedWorkouts = listOf(PlannedWorkout(nov4, 2L), PlannedWorkout(nov4, 1L)),
        )

        store.replaceAll(sameDay)

        assertEquals(
            listOf(PlannedWorkout(nov4, 1L), PlannedWorkout(nov4, 2L)),
            store.exportAll().plannedWorkouts,
        )
    }

    @Test
    fun `a replace leaves only the backup's planned workouts`() = runTest {
        val a = routine("A", 0)
        store.setPlannedWorkout(LocalDate.of(2026, 11, 20), a)

        store.replaceAll(backup)

        assertEquals(backup.plannedWorkouts, store.exportAll().plannedWorkouts)
        assertEquals(backup.plannedWorkouts, store.observePlannedWorkouts().first())
    }

    @Test
    fun `a replace with no planned workouts clears the ones that were there`() = runTest {
        store.replaceAll(backup)

        store.replaceAll(backup.copy(plannedWorkouts = emptyList()))

        assertEquals(emptyList<PlannedWorkout>(), store.exportAll().plannedWorkouts)
    }
}

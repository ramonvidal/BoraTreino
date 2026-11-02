package com.boratreino.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** The progress reads: every set by logged time, every session by start time, open and closed alike. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomProgressStoreTest {
    private lateinit var db: BoraTreinoDatabase
    private lateinit var store: WorkoutStore

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

    private fun at(millis: Long): Instant = Instant.ofEpochMilli(millis)

    private suspend fun machine(name: String): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun session(start: Long): Long =
        store.insertSession(Session(0, at(start), null, null, null))

    // observeAllSets

    @Test
    fun `no sets gives an empty list`() = runTest {
        assertTrue(store.observeAllSets().first().isEmpty())
    }

    @Test
    fun `sets come back ordered by logged time whatever order they were written in`() = runTest {
        val squat = machine("Squat")
        val bench = machine("Bench press")
        val s = session(1_790_000_000_000L)
        val late = store.insertSet(WorkoutSet(0, s, squat, 0, 100.0, 8, null, null, null, at(1_790_000_300_000L)))
        val early = store.insertSet(WorkoutSet(0, s, bench, 0, 60.0, 10, null, null, null, at(1_790_000_100_000L)))
        val middle = store.insertSet(WorkoutSet(0, s, squat, 1, 105.0, 8, null, null, null, at(1_790_000_200_000L)))

        assertEquals(
            listOf(early, middle, late),
            store.observeAllSets().first().map { it.id },
        )
    }

    @Test
    fun `sets logged at the same time are ordered by id`() = runTest {
        val squat = machine("Squat")
        val s = session(1_790_000_000_000L)
        val same = at(1_790_000_500_000L)
        val first = store.insertSet(WorkoutSet(0, s, squat, 0, 100.0, 8, null, null, null, same))
        val second = store.insertSet(WorkoutSet(0, s, squat, 1, 100.0, 8, null, null, null, same))

        assertEquals(listOf(first, second), store.observeAllSets().first().map { it.id })
    }

    @Test
    fun `sets from every session and machine are included`() = runTest {
        val squat = machine("Squat")
        val bench = machine("Bench press")
        val monday = session(1_790_000_000_000L)
        val tuesday = session(1_790_090_000_000L)
        store.insertSet(WorkoutSet(0, monday, squat, 0, 100.0, 8, null, null, null, at(1_790_000_100_000L)))
        store.insertSet(WorkoutSet(0, tuesday, bench, 0, 60.0, 10, null, null, null, at(1_790_090_100_000L)))

        val sets = store.observeAllSets().first()

        assertEquals(listOf(squat, bench), sets.map { it.machineId })
        assertEquals(listOf(monday, tuesday), sets.map { it.sessionId })
    }

    @Test
    fun `a set comes back with every field`() = runTest {
        val squat = machine("Squat")
        val s = session(1_790_000_000_000L)
        val loggedAt = at(1_790_000_500_456L)
        val id = store.insertSet(WorkoutSet(0, s, squat, 2, 120.0, 12, null, null, null, loggedAt))

        assertEquals(
            listOf(WorkoutSet(id, s, squat, 2, 120.0, 12, null, null, null, loggedAt)),
            store.observeAllSets().first(),
        )
    }

    // observeSessions

    @Test
    fun `no sessions gives an empty list`() = runTest {
        assertTrue(store.observeSessions().first().isEmpty())
    }

    @Test
    fun `open and closed sessions are both returned ordered by start time`() = runTest {
        val later = session(1_790_300_000_000L)
        val earliest = session(1_790_100_000_000L)
        val middle = session(1_790_200_000_000L)
        store.setSessionEnd(earliest, at(1_790_103_600_000L))
        store.setSessionEnd(middle, at(1_790_203_600_000L))

        assertEquals(
            listOf(
                Session(earliest, at(1_790_100_000_000L), at(1_790_103_600_000L), null, null),
                Session(middle, at(1_790_200_000_000L), at(1_790_203_600_000L), null, null),
                Session(later, at(1_790_300_000_000L), null, null, null),
            ),
            store.observeSessions().first(),
        )
    }

    @Test
    fun `an open session is included`() = runTest {
        val id = session(1_790_000_000_000L)

        val sessions = store.observeSessions().first()

        assertEquals(listOf(id), sessions.map { it.id })
        assertTrue(sessions.single().isOpen)
    }

    @Test
    fun `a session with sets is still listed after it is closed`() = runTest {
        val squat = machine("Squat")
        val id = session(1_790_000_000_000L)
        store.insertSet(WorkoutSet(0, id, squat, 0, 100.0, 8, null, null, null, at(1_790_000_100_000L)))

        store.setSessionEnd(id, at(1_790_003_600_000L))

        assertEquals(listOf(id), store.observeSessions().first().map { it.id })
        assertEquals(at(1_790_003_600_000L), store.observeSessions().first().single().endedAt)
    }
}

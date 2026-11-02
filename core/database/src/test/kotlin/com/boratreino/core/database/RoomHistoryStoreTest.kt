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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** Deleting and restoring a session on an in-memory database: the sets go with it and come back with their ids. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomHistoryStoreTest {
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

    private val t0: Instant = Instant.ofEpochMilli(1_790_000_000_000L)

    private suspend fun machine(name: String): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, archived = false))

    private suspend fun newSession(start: Instant, end: Instant? = start): Session {
        val id = store.insertSession(Session(id = 0, startedAt = start, endedAt = end, routineId = null, note = null))
        return Session(id, start, end, routineId = null, note = null)
    }

    private suspend fun insertSet(sessionId: Long, machineId: Long, position: Int, weight: Double, loggedAt: Instant): WorkoutSet {
        val id = store.insertSet(
            WorkoutSet(0, sessionId, machineId, position, weight, 10, null, null, null, loggedAt),
        )
        return WorkoutSet(id, sessionId, machineId, position, weight, 10, null, null, null, loggedAt)
    }

    @Test
    fun `deleting a session removes its sets and keeps the machine`() = runTest {
        val leg = machine("Leg press")
        val row = machine("Seated row")
        val doomed = newSession(t0, t0.plusSeconds(3600))
        insertSet(doomed.id, leg, 0, 120.0, t0)
        insertSet(doomed.id, leg, 1, 120.0, t0.plusSeconds(120))
        insertSet(doomed.id, row, 0, 40.0, t0.plusSeconds(300))
        val kept = newSession(t0.plusSeconds(86_400))
        val keptSet = insertSet(kept.id, leg, 0, 125.0, kept.startedAt)

        store.deleteSession(doomed.id)

        assertNull(store.session(doomed.id))
        assertTrue(store.sessionSets(doomed.id).isEmpty())
        assertEquals(listOf(keptSet), store.machineSets(leg))
        assertEquals("Leg press", store.observeMachine(leg).first()?.name)
    }

    @Test
    fun `restoring a session brings back its id and its sets with their ids`() = runTest {
        val leg = machine("Leg press")
        val row = machine("Seated row")
        val session = newSession(t0, t0.plusSeconds(3600))
        val first = insertSet(session.id, leg, 0, 120.0, t0)
        val second = insertSet(session.id, leg, 1, 125.0, t0.plusSeconds(120))
        val third = insertSet(session.id, row, 0, 40.0, t0.plusSeconds(300))
        val before = store.sessionSets(session.id)

        store.deleteSession(session.id)
        store.restoreSession(session, listOf(first, second, third))

        assertEquals(session, store.session(session.id))
        assertEquals(before, store.sessionSets(session.id))
        assertEquals(listOf(first.id, second.id, third.id), store.sessionSets(session.id).map { it.id })
    }

    @Test
    fun `restoring a session with no sets brings back just the session`() = runTest {
        val session = newSession(t0)

        store.deleteSession(session.id)
        store.restoreSession(session, emptyList())

        assertEquals(session, store.session(session.id))
        assertTrue(store.sessionSets(session.id).isEmpty())
    }

    @Test
    fun `a restored set keeps its id, so the next set does not reuse it`() = runTest {
        val leg = machine("Leg press")
        val session = newSession(t0, t0.plusSeconds(3600))
        val only = insertSet(session.id, leg, 0, 120.0, t0)
        store.deleteSession(session.id)
        store.restoreSession(session, listOf(only))

        val next = insertSet(session.id, leg, 1, 120.0, t0.plusSeconds(60))

        assertEquals(listOf(only.id, next.id), store.sessionSets(session.id).map { it.id })
        assertTrue(next.id != only.id)
    }
}

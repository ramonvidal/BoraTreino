package com.boratreino.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetType
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** A new database stores the set type, and the session counts leave warm-ups out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomSetTypeStoreTest {
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

    private suspend fun machine(name: String): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun session(): Long = store.insertSession(Session(0, Instant.ofEpochMilli(1_000), null, null, null))

    private fun set(sessionId: Long, machineId: Long, position: Int, type: SetType) = WorkoutSet(
        0, sessionId, machineId, position, 20.0, 12, null, null, null, Instant.ofEpochMilli(2_000L + position), type,
    )

    @Test
    fun `a warm-up set is stored and read back as a warm-up`() = runTest {
        val m = machine("Press")
        val s = session()

        val id = store.insertSet(set(s, m, 0, SetType.WARMUP))

        val read = store.observeMachineSets(s, m).first().single()
        assertEquals(id, read.id)
        assertEquals(SetType.WARMUP, read.type)
    }

    @Test
    fun `every type survives insert and update`() = runTest {
        val m = machine("Press")
        val s = session()
        val ids = SetType.entries.mapIndexed { i, t -> store.insertSet(set(s, m, i, t)) }

        val read = store.sessionSets(s)
        assertEquals(SetType.entries.toList(), read.map { it.type })

        store.updateSet(read[0].copy(type = SetType.FAILURE))
        assertEquals(SetType.FAILURE, store.sessionSets(s).first { it.id == ids[0] }.type)
    }

    @Test
    fun `the session summary counts working sets and machines only`() = runTest {
        val press = machine("Press")
        val curl = machine("Curl")
        val s = session()
        store.insertSet(set(s, press, 0, SetType.WARMUP))
        store.insertSet(set(s, press, 1, SetType.NORMAL))
        store.insertSet(set(s, press, 2, SetType.DROP))
        store.insertSet(set(s, curl, 3, SetType.WARMUP))

        val summary = store.summary(s)

        assertEquals(2, summary.setCount)
        assertEquals(1, summary.machineCount)
    }

    @Test
    fun `a session with only warm-ups counts nothing`() = runTest {
        val m = machine("Press")
        val s = session()
        store.insertSet(set(s, m, 0, SetType.WARMUP))

        val summary = store.summary(s)

        assertEquals(0, summary.setCount)
        assertEquals(0, summary.machineCount)
    }
}

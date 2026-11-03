package com.boratreino.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** Deleting machines and reading their use, through the store and an in-memory database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomMachineEditingStoreTest {
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

    /** A routine with the machines as items, at the given position. */
    private suspend fun routine(name: String, position: Int, vararg machineIds: Long): Long {
        val planId = store.ensurePlan().id
        val id = store.insertRoutine(Routine(0, planId, name, name.take(1), position, emptySet()))
        store.replaceRoutineItems(id, machineIds.mapIndexed { i, m -> RoutineItem(id, m, i, 3, 8, 12) })
        return id
    }

    private suspend fun logSet(machineId: Long, sessionId: Long, position: Int) {
        store.insertSet(WorkoutSet(0, sessionId, machineId, position, 40.0, 10, null, null, null, t0))
    }

    @Test
    fun `machine set count counts every set ever logged on the machine`() = runTest {
        val curl = machine("Leg curl")
        val other = machine("Leg press")
        val session = store.insertSession(Session(0, t0, null, null, null))
        logSet(curl, session, 0)
        logSet(curl, session, 1)
        logSet(other, session, 0)

        assertEquals(2, store.machineSetCount(curl))
        assertEquals(0, store.machineSetCount(999L))
    }

    @Test
    fun `routine names with a machine follow the routine position`() = runTest {
        val curl = machine("Leg curl")
        routine("Treino C", 0, curl)
        routine("Treino A", 1, machine("Leg press"))
        routine("Treino B", 2, curl)

        assertEquals(listOf("Treino C", "Treino B"), store.routineNamesWithMachine(curl))
    }

    @Test
    fun `delete removes the routine items and then the machine`() = runTest {
        val curl = machine("Leg curl")
        val press = machine("Leg press")
        val a = routine("Treino A", 0, press, curl)
        val b = routine("Treino B", 1, curl)

        assertTrue(store.deleteMachine(curl))

        assertNull(store.observeMachine(curl).first())
        assertEquals(listOf(press), store.routineItems(a).map { it.machineId })
        assertEquals(emptyList<Long>(), store.routineItems(b).map { it.machineId })
        assertEquals(emptyList<String>(), store.routineNamesWithMachine(curl))
    }

    @Test
    fun `delete is refused for a machine with logged sets and changes nothing`() = runTest {
        val curl = machine("Leg curl")
        val a = routine("Treino A", 0, curl)
        val session = store.insertSession(Session(0, t0, null, null, null))
        logSet(curl, session, 0)

        assertFalse(store.deleteMachine(curl))

        assertTrue(store.observeMachine(curl).first() != null)
        assertEquals(listOf(curl), store.routineItems(a).map { it.machineId })
        assertEquals(1, store.machineSetCount(curl))
    }

    @Test
    fun `delete returns false for a machine that does not exist`() = runTest {
        assertFalse(store.deleteMachine(999L))
    }
}

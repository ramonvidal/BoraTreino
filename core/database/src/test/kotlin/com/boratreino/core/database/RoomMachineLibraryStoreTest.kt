package com.boratreino.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
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

/** A machine's exercise-library id, through the store and an in-memory database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomMachineLibraryStoreTest {
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

    private fun machine(name: String, libraryId: String? = null, archived: Boolean = false) =
        Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, archived, libraryId)

    @Test
    fun `a machine saved with a library id reads it back`() = runTest {
        val id = store.saveMachine(machine("Leg press", libraryId = "Leg_Press"))

        assertEquals("Leg_Press", store.observeMachine(id).first()?.libraryId)
        assertEquals("Leg_Press", store.machines().single().libraryId)
    }

    @Test
    fun `a machine saved without a library id has none`() = runTest {
        val id = store.saveMachine(machine("Home-made rig"))

        assertNull(store.observeMachine(id).first()?.libraryId)
    }

    @Test
    fun `saving an existing machine can set or clear its library id`() = runTest {
        val id = store.saveMachine(machine("Leg press"))

        store.saveMachine(machine("Leg press", libraryId = "Leg_Press").copy(id = id))
        assertEquals("Leg_Press", store.observeMachine(id).first()?.libraryId)

        store.saveMachine(machine("Leg press").copy(id = id))
        assertNull(store.observeMachine(id).first()?.libraryId)
    }

    @Test
    fun `the library id is listed with the machines, archived ones too`() = runTest {
        store.saveMachine(machine("Leg press", libraryId = "Leg_Press"))
        store.saveMachine(machine("Pull-ups", libraryId = "Pullups", archived = true))

        val listed = store.observeMachines(includeArchived = true).first().associate { it.name to it.libraryId }

        assertEquals(mapOf("Leg press" to "Leg_Press", "Pull-ups" to "Pullups"), listed)
    }

    @Test
    fun `adding a machine by name keeps its library id`() = runTest {
        val id = store.addMachineOrUnarchive(machine("Leg press", libraryId = "Leg_Press"))

        assertEquals("Leg_Press", store.observeMachine(id).first()?.libraryId)
    }

    @Test
    fun `the library id survives an export and a replace`() = runTest {
        val id = store.saveMachine(machine("Leg press", libraryId = "Leg_Press"))
        val exported = store.exportAll()

        store.replaceAll(BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        assertEquals(emptyList<Machine>(), store.machines())

        store.replaceAll(exported)

        assertEquals("Leg_Press", store.observeMachine(id).first()?.libraryId)
    }
}

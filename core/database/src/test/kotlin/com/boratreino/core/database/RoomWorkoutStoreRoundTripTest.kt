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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** Machines, sessions and sets written through the store come back unchanged from an in-memory database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomWorkoutStoreRoundTripTest {
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

    @Test
    fun `a machine comes back with every field`() = runTest {
        val id = store.saveMachine(
            Machine(0, "Chest press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, "Seat 4, back pad 2", 90, archived = true),
        )

        assertEquals(
            Machine(id, "Chest press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, "Seat 4, back pad 2", 90, archived = true),
            store.observeMachine(id).first(),
        )
    }

    @Test
    fun `every muscle group and machine kind survives a save`() = runTest {
        MuscleGroup.entries.forEachIndexed { i, group ->
            MachineKind.entries.forEachIndexed { j, kind ->
                val name = "machine $i-$j"
                val id = store.saveMachine(Machine(0, name, group, kind, 1.0, null, null, archived = false))

                val stored = store.observeMachine(id).first()
                assertEquals(group, stored?.muscleGroup)
                assertEquals(kind, stored?.kind)
            }
        }
    }

    @Test
    fun `an open session comes back with its start and note`() = runTest {
        val start = Instant.ofEpochMilli(1_790_000_000_123L)
        val id = store.insertSession(Session(0, start, null, null, "felt strong"))

        assertEquals(Session(id, start, null, null, "felt strong"), store.observeOpenSession().first())
    }

    @Test
    fun `a closed session is not open and stores its end time in epoch millis`() = runTest {
        val start = Instant.ofEpochMilli(1_790_000_000_123L)
        val end = Instant.ofEpochMilli(1_790_003_600_456L)
        val id = store.insertSession(Session(0, start, null, null, null))

        store.setSessionEnd(id, end)

        assertNull(store.observeOpenSession().first())
        val stored = db.openHelper.readableDatabase
            .query("SELECT endedAt FROM sessions WHERE id = $id")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        assertEquals(end.toEpochMilli(), stored)
    }

    @Test
    fun `a set with weight and reps comes back with every field`() = runTest {
        val machineId = store.saveMachine(Machine(0, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, false))
        val sessionId = store.insertSession(Session(0, Instant.ofEpochMilli(1_790_000_000_000L), null, null, null))
        val loggedAt = Instant.ofEpochMilli(1_790_000_500_456L)

        val id = store.insertSet(
            WorkoutSet(0, sessionId, machineId, 0, 120.0, 12, null, null, null, loggedAt),
        )

        assertEquals(
            listOf(WorkoutSet(id, sessionId, machineId, 0, 120.0, 12, null, null, null, loggedAt)),
            store.observeMachineSets(sessionId, machineId).first(),
        )
    }

    @Test
    fun `a bodyweight set with no weight and a cardio set with every measurement come back intact`() = runTest {
        val pullUps = store.saveMachine(Machine(0, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS, 2.5, null, null, false))
        val bike = store.saveMachine(Machine(0, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false))
        val sessionId = store.insertSession(Session(0, Instant.ofEpochMilli(1_790_000_000_000L), null, null, null))
        val at = Instant.ofEpochMilli(1_790_000_100_789L)

        val pullUpSet = store.insertSet(WorkoutSet(0, sessionId, pullUps, 0, null, 10, null, null, null, at))
        val bikeSet = store.insertSet(WorkoutSet(0, sessionId, bike, 0, null, null, 600, 85, 2400, at))

        assertEquals(
            listOf(WorkoutSet(pullUpSet, sessionId, pullUps, 0, null, 10, null, null, null, at)),
            store.observeMachineSets(sessionId, pullUps).first(),
        )
        assertEquals(
            listOf(WorkoutSet(bikeSet, sessionId, bike, 0, null, null, 600, 85, 2400, at)),
            store.observeMachineSets(sessionId, bike).first(),
        )
    }

    @Test
    fun `an updated set comes back with the new values`() = runTest {
        val machineId = store.saveMachine(Machine(0, "Lat pulldown", MuscleGroup.BACK, MachineKind.WEIGHT_REPS, 2.5, null, null, false))
        val sessionId = store.insertSession(Session(0, Instant.ofEpochMilli(1_790_000_000_000L), null, null, null))
        val at = Instant.ofEpochMilli(1_790_000_200_000L)
        val id = store.insertSet(WorkoutSet(0, sessionId, machineId, 0, 55.0, 10, null, null, null, at))

        store.updateSet(WorkoutSet(id, sessionId, machineId, 0, 57.5, 8, null, null, null, at))

        assertEquals(
            listOf(WorkoutSet(id, sessionId, machineId, 0, 57.5, 8, null, null, null, at)),
            store.observeMachineSets(sessionId, machineId).first(),
        )
    }
}

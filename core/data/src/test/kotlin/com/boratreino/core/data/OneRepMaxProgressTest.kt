package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetType
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The estimated 1RM points the progress repository fills for weight machines. */
class OneRepMaxProgressTest {
    private val monday: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var repo: ProgressRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        repo = DefaultProgressRepository(store, TestClock(monday))
    }

    private suspend fun machine(name: String, kind: MachineKind): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.CHEST, kind, 2.5, null, null, false))

    private suspend fun session(startedAt: Instant): Long =
        store.insertSession(Session(0, startedAt, startedAt.plusSeconds(3600), null, null))

    private suspend fun set(
        sessionId: Long,
        machineId: Long,
        weightKg: Double?,
        reps: Int?,
        at: Instant,
        position: Int = 0,
        seconds: Int? = null,
        type: SetType = SetType.NORMAL,
    ) {
        store.insertSet(WorkoutSet(0, sessionId, machineId, position, weightKg, reps, seconds, null, null, at, type))
    }

    @Test
    fun `a weight machine has one rm points in machine progress`() = runTest {
        val id = machine("Bench", MachineKind.WEIGHT_REPS)
        val early = session(Instant.parse("2026-10-01T10:00:00Z"))
        val late = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(early, id, 80.0, 8, Instant.parse("2026-10-01T10:05:00Z"))
        set(late, id, 100.0, 1, Instant.parse("2026-11-02T10:05:00Z"))
        set(late, id, 150.0, 5, Instant.parse("2026-11-02T10:01:00Z"), position = 1, type = SetType.WARMUP)

        val progress = repo.observeMachineProgress(id).first()!!

        assertEquals(2, progress.oneRmPoints.size)
        assertEquals(80.0 * (1 + 8 / 30.0), progress.oneRmPoints[0].value, 1e-9)
        assertEquals(100.0, progress.oneRmPoints[1].value, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 1), progress.oneRmPoints[0].date)
        assertEquals(1, progress.oneRmPoints[1].setCount)
    }

    @Test
    fun `a bodyweight machine with added weight has one rm points`() = runTest {
        val id = machine("Dips", MachineKind.BODYWEIGHT_REPS)
        val s = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(s, id, 20.0, 10, Instant.parse("2026-11-02T10:05:00Z"))

        val progress = repo.observeMachineProgress(id).first()!!

        assertEquals(listOf(20.0 * (1 + 10 / 30.0)), progress.oneRmPoints.map { it.value })
    }

    @Test
    fun `a bodyweight machine without weights has no one rm points`() = runTest {
        val id = machine("Pull-up bar", MachineKind.BODYWEIGHT_REPS)
        val s = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(s, id, null, 12, Instant.parse("2026-11-02T10:05:00Z"))

        assertTrue(repo.observeMachineProgress(id).first()!!.oneRmPoints.isEmpty())
    }

    @Test
    fun `sets above twelve reps give no one rm points`() = runTest {
        val id = machine("Leg press", MachineKind.WEIGHT_REPS)
        val s = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(s, id, 100.0, 15, Instant.parse("2026-11-02T10:05:00Z"))

        assertTrue(repo.observeMachineProgress(id).first()!!.oneRmPoints.isEmpty())
    }

    @Test
    fun `a cardio machine has no one rm points`() = runTest {
        val id = machine("Treadmill", MachineKind.CARDIO)
        val s = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(s, id, null, null, Instant.parse("2026-11-02T10:05:00Z"), seconds = 1200)

        assertTrue(repo.observeMachineProgress(id).first()!!.oneRmPoints.isEmpty())
    }

    @Test
    fun `the overview fills one rm points for weight machines and leaves cardio empty`() = runTest {
        val bench = machine("Bench", MachineKind.WEIGHT_REPS)
        val bike = machine("Bike", MachineKind.CARDIO)
        val s = session(Instant.parse("2026-11-02T10:00:00Z"))
        set(s, bench, 80.0, 8, Instant.parse("2026-11-02T10:05:00Z"))
        set(s, bike, null, null, Instant.parse("2026-11-02T10:30:00Z"), seconds = 900)

        val overview = repo.observeOverview().first()

        val benchProgress = overview.machines.single { it.machine.id == bench }
        val bikeProgress = overview.machines.single { it.machine.id == bike }
        assertEquals(listOf(80.0 * (1 + 8 / 30.0)), benchProgress.oneRmPoints.map { it.value })
        assertTrue(bikeProgress.oneRmPoints.isEmpty())
    }
}

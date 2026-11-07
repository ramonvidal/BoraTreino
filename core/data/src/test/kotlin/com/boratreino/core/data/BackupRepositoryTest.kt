package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.BackupCodec
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.BackupDecodeResult
import com.boratreino.core.model.BackupSummary

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class BackupRepositoryTest {
    /** 2 November 2026, 23:30 in Sao Paulo: already 3 November in UTC. */
    private val now: Instant = Instant.parse("2026-11-03T02:30:00Z")
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var training: DefaultTrainingRepository
    private lateinit var repo: BackupRepository

    /** A small history, with every table in id order (items by routine, then position). */
    private val seed = BackupData(
        machines = listOf(
            Machine(1, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, "Seat 4", 120, archived = false),
            Machine(2, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS, 2.5, null, null, archived = true),
        ),
        plans = listOf(RoutinePlan(1, ScheduleMode.WEEKDAYS)),
        routines = listOf(
            Routine(1, 1, "Routine A", "A", 0, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            Routine(2, 1, "Routine B", "B", 1, setOf(DayOfWeek.FRIDAY)),
        ),
        items = listOf(
            RoutineItem(routineId = 1, machineId = 1, position = 0, targetSets = 3, repsMin = 8, repsMax = 12),
            RoutineItem(routineId = 2, machineId = 1, position = 0, targetSets = 2, repsMin = 10, repsMax = 10),
        ),
        sessions = listOf(
            Session(1, Instant.parse("2026-10-27T18:05:00Z"), Instant.parse("2026-10-27T19:10:00Z"), 1, "Felt \"strong\""),
            Session(2, Instant.parse("2026-11-02T20:00:00Z"), Instant.parse("2026-11-02T20:45:00Z"), 2, null),
        ),
        sets = listOf(
            WorkoutSet(1, 1, 1, 0, 120.0, 12, null, null, null, Instant.parse("2026-10-27T18:05:00Z")),
            WorkoutSet(2, 1, 1, 1, 120.0, 11, null, null, null, Instant.parse("2026-10-27T18:07:00Z")),
            WorkoutSet(3, 2, 1, 0, 100.0, 10, null, null, null, Instant.parse("2026-11-02T20:00:00Z")),
        ),
    )

    private val other = BackupData(
        machines = listOf(Machine(7, "Chest press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, archived = false)),
        plans = emptyList(),
        routines = emptyList(),
        items = emptyList(),
        sessions = emptyList(),
        sets = emptyList(),
    )

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(now, zone)
        training = DefaultTrainingRepository(store, clock, DefaultRoutineRepository(store))
        repo = DefaultBackupRepository(store, clock, training)
    }

    private fun repoOn(otherStore: WorkoutStore, otherClock: TestClock): BackupRepository =
        DefaultBackupRepository(otherStore, otherClock, DefaultTrainingRepository(otherStore, otherClock, DefaultRoutineRepository(otherStore)))

    // export

    @Test
    fun `the export decodes and holds exactly what the store holds`() = runTest {
        store.replaceAll(seed)

        val result = BackupCodec.decode(repo.export())

        assertTrue(result is BackupDecodeResult.Ok)
        result as BackupDecodeResult.Ok
        assertEquals(seed, result.data)
        assertEquals(store.exportAll(), result.data)
        assertEquals(now, result.exportedAt)
    }

    @Test
    fun `the training days of the plan survive an export and an import`() = runTest {
        val withDays = seed.copy(plans = listOf(RoutinePlan(1, ScheduleMode.IN_TURN, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))))
        store.replaceAll(withDays)
        val exported = repo.export()
        val otherStore = FakeWorkoutStore()
        val otherRepo = repoOn(otherStore, TestClock(now, zone))

        val decoded = BackupCodec.decode(exported) as BackupDecodeResult.Ok
        otherRepo.import(decoded.data)

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), otherStore.exportAll().plans.single().trainingDays)
        assertEquals(withDays, otherStore.exportAll())
    }

    @Test
    fun `planned workouts survive an export and an import`() = runTest {
        val planned = seed.copy(
            plannedWorkouts = listOf(
                PlannedWorkout(LocalDate.of(2026, 11, 4), 1L),
                PlannedWorkout(LocalDate.of(2026, 11, 9), 2L),
            ),
        )
        store.replaceAll(planned)
        val exported = repo.export()
        val otherStore = FakeWorkoutStore()
        val otherRepo = repoOn(otherStore, TestClock(now, zone))

        val decoded = BackupCodec.decode(exported) as BackupDecodeResult.Ok
        otherRepo.import(decoded.data)

        assertEquals(planned, otherStore.exportAll())
    }

    @Test
    fun `an import replaces the planned workouts that were there`() = runTest {
        store.replaceAll(seed.copy(plannedWorkouts = listOf(PlannedWorkout(LocalDate.of(2026, 11, 4), 1L))))

        repo.import(seed)

        assertEquals(emptyList<PlannedWorkout>(), store.exportAll().plannedWorkouts)
    }

    @Test
    fun `a version 3 backup without planned workouts imports with none`() = runTest {
        store.replaceAll(seed.copy(plannedWorkouts = listOf(PlannedWorkout(LocalDate.of(2026, 11, 4), 1L))))
        val v3 = """
            {"app":"boratreino","version":3,"machines":[],
             "plans":[{"id":1,"scheduleMode":"IN_TURN","trainingDays":[]}],
             "routines":[{"id":1,"planId":1,"name":"Routine A","shortName":"A","position":0,"weekdays":[]}],
             "items":[],"sessions":[],"sets":[]}
        """.trimIndent()

        val preview = repo.preview(v3)
        assertTrue(preview is BackupPreview.Ready)
        repo.import((preview as BackupPreview.Ready).data)

        assertEquals(emptyList<PlannedWorkout>(), store.exportAll().plannedWorkouts)
        assertEquals(1, store.exportAll().routines.size)
    }

    @Test
    fun `a backup with a planned workout for a missing routine previews as invalid`() {
        val broken = BackupCodec.encode(seed.copy(plannedWorkouts = listOf(PlannedWorkout(LocalDate.of(2026, 11, 4), 99L))), now)

        assertTrue(repo.preview(broken) is BackupPreview.Invalid)
    }

    @Test
    fun `an export imported into another store gives back the same data`() = runTest {
        store.replaceAll(seed)
        val exported = repo.export()
        val otherStore = FakeWorkoutStore()
        val otherRepo = repoOn(otherStore, TestClock(now, zone))

        val decoded = BackupCodec.decode(exported) as BackupDecodeResult.Ok
        otherRepo.import(decoded.data)

        assertEquals(seed, otherStore.exportAll())
    }

    @Test
    fun `the suggested file name has the date in the clock's zone`() {
        assertEquals("boratreino-backup-2026-11-02.json", repo.suggestedFileName())
    }

    @Test
    fun `the suggested file name follows the clock's zone, not UTC`() {
        val utcRepo = repoOn(FakeWorkoutStore(), TestClock(now, ZoneOffset.UTC))

        assertEquals("boratreino-backup-2026-11-03.json", utcRepo.suggestedFileName())
    }

    // preview

    @Test
    fun `a backup previews as ready with its data and summary`() = runTest {
        store.replaceAll(seed)

        val preview = repo.preview(repo.export())

        assertTrue(preview is BackupPreview.Ready)
        preview as BackupPreview.Ready
        assertEquals(seed, preview.data)
        assertEquals(
            BackupSummary(
                machines = 2,
                routines = 2,
                sessions = 2,
                sets = 3,
                firstDay = LocalDate.of(2026, 10, 27),
                lastDay = LocalDate.of(2026, 11, 2),
            ),
            preview.summary,
        )
    }

    @Test
    fun `text that is not a backup previews as invalid with a reason`() {
        val preview = repo.preview("this is not a backup")

        assertTrue(preview is BackupPreview.Invalid)
        assertTrue((preview as BackupPreview.Invalid).reason.isNotBlank())
    }

    @Test
    fun `a newer backup previews as too new`() {
        val preview = repo.preview("{\"app\":\"boratreino\",\"version\":5,\"machines\":\"odd\"}")

        assertEquals(BackupPreview.TooNew(5), preview)
    }

    @Test
    fun `a version 2 backup without training days previews with every day`() {
        val v2 = """
            {"app":"boratreino","version":2,"machines":[],
             "plans":[{"id":1,"scheduleMode":"IN_TURN"}],
             "routines":[],"items":[],"sessions":[],"sets":[]}
        """.trimIndent()

        val preview = repo.preview(v2)

        assertTrue(preview is BackupPreview.Ready)
        assertEquals(
            RoutinePlan(1, ScheduleMode.IN_TURN, emptySet()),
            (preview as BackupPreview.Ready).data.plans.single(),
        )
    }

    @Test
    fun `a preview changes nothing`() = runTest {
        store.replaceAll(seed)
        val before = store.exportAll()

        repo.preview(BackupCodec.encode(other, now))

        assertEquals(before, store.exportAll())
    }

    // import

    @Test
    fun `an import replaces everything that was there`() = runTest {
        store.replaceAll(other)

        repo.import(seed)

        assertEquals(seed, store.exportAll())
        assertTrue(store.machines().none { it.id == 7L })
    }

    @Test
    fun `an import resets the routine choice to Suggested, even with a session open`() = runTest {
        store.replaceAll(seed)
        val open = training.logSet(1L, SetDraft(120.0, 12)).set.sessionId
        training.chooseRoutine(RoutineChoice.Free)
        assertEquals(RoutineChoice.Free, training.observeTodayPlan().first().choice)

        repo.import(seed)

        assertEquals(RoutineChoice.Suggested, training.observeTodayPlan().first().choice)
        assertNull(store.session(open))
        assertNull(store.openSession())
    }

    @Test
    fun `importing a backup clears the skipped machines`() = runTest {
        store.replaceAll(seed)
        training.skipMachineToday(1L)

        repo.import(seed)

        assertEquals(emptySet<Long>(), training.observeTodayPlan().first().skipped)
    }
}

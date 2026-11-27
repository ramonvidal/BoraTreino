package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.BackupCodec
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.BackupDecodeResult
import com.boratreino.core.model.BackupSummary

import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MeasurementType
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
    fun `several routines planned on one day survive an export and an import`() = runTest {
        val planned = seed.copy(
            plannedWorkouts = listOf(
                PlannedWorkout(LocalDate.of(2026, 11, 4), 1L),
                PlannedWorkout(LocalDate.of(2026, 11, 4), 2L),
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
    fun `the order of planned workouts on one day survives an export and an import`() = runTest {
        val planned = seed.copy(
            plannedWorkouts = listOf(
                PlannedWorkout(LocalDate.of(2026, 11, 4), 2L, 0),
                PlannedWorkout(LocalDate.of(2026, 11, 4), 1L, 1),
            ),
        )
        store.replaceAll(planned)
        val exported = repo.export()
        val otherStore = FakeWorkoutStore()
        val otherRepo = repoOn(otherStore, TestClock(now, zone))

        val decoded = BackupCodec.decode(exported) as BackupDecodeResult.Ok
        otherRepo.import(decoded.data)

        assertEquals(planned, otherStore.exportAll())
        assertEquals(planned.plannedWorkouts, otherStore.observePlannedWorkouts().first())
    }

    @Test
    fun `a backup that plans the same routine twice on one day previews as invalid`() = runTest {
        val twice = seed.copy(
            plannedWorkouts = listOf(PlannedWorkout(LocalDate.of(2026, 11, 4), 1L), PlannedWorkout(LocalDate.of(2026, 11, 4), 1L)),
        )

        assertTrue(repo.preview(BackupCodec.encode(twice, now)) is BackupPreview.Invalid)
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
    fun `a machine's exercise library link survives an export and an import`() = runTest {
        val linked = seed.copy(machines = listOf(seed.machines[0].copy(libraryId = "Leg_Press"), seed.machines[1]))
        store.replaceAll(linked)
        val exported = repo.export()
        store.replaceAll(other)

        val preview = repo.preview(exported)
        assertTrue(preview is BackupPreview.Ready)
        repo.import((preview as BackupPreview.Ready).data)

        assertEquals(linked.machines, store.exportAll().machines)
        assertEquals("Leg_Press", store.exportAll().machines.single { it.id == 1L }.libraryId)
    }

    @Test
    fun `a version 4 backup imports with no library links`() = runTest {
        val v4 = """
            {"app":"boratreino","version":4,"machines":[{"id":1,"name":"Leg press","muscleGroup":"LEGS","kind":"WEIGHT_REPS","stepKg":5.0,"setupNote":null,"restSeconds":null,"archived":false}],
             "plans":[{"id":1,"scheduleMode":"IN_TURN","trainingDays":[]}],
             "routines":[{"id":1,"planId":1,"name":"Routine A","shortName":"A","position":0,"weekdays":[]}],
             "items":[{"routineId":1,"machineId":1,"position":0,"targetSets":3,"repsMin":8,"repsMax":12}],
             "sessions":[],"sets":[],"plannedWorkouts":[]}
        """.trimIndent()

        val preview = repo.preview(v4)
        assertTrue(preview is BackupPreview.Ready)
        repo.import((preview as BackupPreview.Ready).data)

        val machine = store.exportAll().machines.single()
        assertEquals("Leg press", machine.name)
        assertNull(machine.libraryId)
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
        val preview = repo.preview("{\"app\":\"boratreino\",\"version\":12,\"machines\":\"odd\"}")

        assertEquals(BackupPreview.TooNew(12), preview)
    }

    @Test
    fun `an extra session survives an export and an import`() = runTest {
        val extra = seed.copy(sessions = listOf(seed.sessions[0], seed.sessions[1].copy(isExtra = true)))
        store.replaceAll(extra)
        val exported = repo.export()
        store.replaceAll(other)

        val preview = repo.preview(exported)
        assertTrue(preview is BackupPreview.Ready)
        repo.import((preview as BackupPreview.Ready).data)

        assertEquals(listOf(false, true), store.exportAll().sessions.map { it.isExtra })
    }

    @Test
    fun `a version 7 backup imports with its sessions not extra`() = runTest {
        store.replaceAll(seed)
        val v7 = """
            {"app":"boratreino","version":7,"machines":[{"id":1,"name":"Leg press","muscleGroup":"LEGS","kind":"WEIGHT_REPS","stepKg":5.0,"setupNote":null,"restSeconds":null,"archived":false}],
             "plans":[{"id":1,"scheduleMode":"IN_TURN","trainingDays":[]}],
             "routines":[{"id":1,"planId":1,"name":"Routine A","shortName":"A","position":0,"weekdays":[]}],
             "items":[],
             "sessions":[{"id":4,"startedAt":"2026-11-02T10:00:00Z","endedAt":"2026-11-02T10:45:00Z","routineId":1,"note":null}],
             "sets":[{"id":1,"sessionId":4,"machineId":1,"position":0,"weightKg":100.0,"reps":10,"seconds":null,"calories":null,"distanceM":null,"loggedAt":"2026-11-02T10:10:00Z","type":"NORMAL"}],
             "plannedWorkouts":[],"measurements":[]}
        """.trimIndent()

        val preview = repo.preview(v7)
        assertTrue(preview is BackupPreview.Ready)
        repo.import((preview as BackupPreview.Ready).data)

        val session = store.exportAll().sessions.single()
        assertEquals(1L, session.routineId)
        assertEquals(false, session.isExtra)
    }

    @Test
    fun `an import drops the sessions that have no sets and keeps everything else`() = runTest {
        val emptySession = Session(3, Instant.parse("2026-11-01T10:00:00Z"), Instant.parse("2026-11-01T10:00:00Z"), 1, null)

        repo.import(seed.copy(sessions = seed.sessions + emptySession))

        assertEquals(seed, store.exportAll())
        assertEquals(listOf(1L, 2L), store.allSessions().map { it.id })
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

    // body measurements

    /** Ids ascend in the same order the file writes them (date, then type), so both orders agree. */
    private val measurements = listOf(
        BodyMeasurement(1, LocalDate.of(2026, 10, 1), MeasurementType.WAIST, 84.0),
        BodyMeasurement(2, LocalDate.of(2026, 10, 1), MeasurementType.ARM, 33.5),
        BodyMeasurement(3, LocalDate.of(2026, 11, 1), MeasurementType.WAIST, 82.5),
        BodyMeasurement(4, LocalDate.of(2026, 11, 2), MeasurementType.NECK, 38.5),
    )

    @Test
    fun `measurements survive an export and an import`() = runTest {
        store.replaceAll(seed.copy(measurements = measurements))
        val exported = repo.export()
        val otherStore = FakeWorkoutStore()
        val otherRepo = repoOn(otherStore, TestClock(now, zone))

        val preview = otherRepo.preview(exported)
        assertTrue(preview is BackupPreview.Ready)
        val ready = preview as BackupPreview.Ready
        assertEquals(measurements, ready.data.measurements)
        otherRepo.import(ready.data)

        assertEquals(measurements, otherStore.exportAll().measurements)
    }

    @Test
    fun `an import replaces the measurements that were there`() = runTest {
        store.replaceAll(seed.copy(measurements = measurements))
        val replacement = BodyMeasurement(9, LocalDate.of(2026, 11, 3), MeasurementType.THIGH, 55.0)

        repo.import(seed.copy(measurements = listOf(replacement)))

        assertEquals(listOf(replacement), store.exportAll().measurements)
        assertEquals(listOf(replacement), store.observeMeasurements().first())
    }

    @Test
    fun `a version 6 backup without measurements empties the measurements table on import`() = runTest {
        store.replaceAll(seed.copy(measurements = measurements))
        val v6 = """
            {"app":"boratreino","version":6,"machines":[],
             "plans":[{"id":1,"scheduleMode":"IN_TURN","trainingDays":[]}],
             "routines":[{"id":1,"planId":1,"name":"Routine A","shortName":"A","position":0,"weekdays":[]}],
             "items":[],"sessions":[],"sets":[],"plannedWorkouts":[]}
        """.trimIndent()

        val preview = repo.preview(v6)
        assertTrue(preview is BackupPreview.Ready)
        repo.import((preview as BackupPreview.Ready).data)

        assertEquals(emptyList<BodyMeasurement>(), store.exportAll().measurements)
        assertEquals(emptyList<BodyMeasurement>(), store.observeMeasurements().first())
        assertEquals(1, store.exportAll().routines.size)
    }

    @Test
    fun `a backup with an unknown measurement type previews as invalid`() {
        val json = BackupCodec.encode(seed.copy(measurements = measurements), now)
        assertTrue(json.contains("\"type\":\"NECK\""))

        assertTrue(repo.preview(json.replace("\"type\":\"NECK\"", "\"type\":\"CALF\"")) is BackupPreview.Invalid)
    }
}

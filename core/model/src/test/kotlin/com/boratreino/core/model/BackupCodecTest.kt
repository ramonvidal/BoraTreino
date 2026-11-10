package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class BackupCodecTest {
    private val exportedAt: Instant = Instant.parse("2026-11-02T20:15:30.250Z")

    /** A dataset like a real history: nulls, weekdays, nanosecond instants and awkward text. */
    private val data = BackupData(
        machines = listOf(
            Machine(1, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, "Seat 4, back pad 2", 120, archived = false),
            Machine(9_007_199_254_740_993L, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS, 2.5, null, null, archived = true),
            Machine(
                3,
                "Cable \"biceps\" curl\nline two\ttab \\ slash \u0001 caf\u00e9 \u6F22\u5B57",
                MuscleGroup.ARMS,
                MachineKind.WEIGHT_REPS,
                1.25,
                "Note: \"hold\"\n",
                60,
                archived = false,
            ),
        ),
        plans = listOf(RoutinePlan(1, ScheduleMode.WEEKDAYS, setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))),
        routines = listOf(
            Routine(1, 1, "Routine A", "A", 0, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            Routine(2, 1, "Quote \"B\" \\ day", "B", 1, emptySet()),
        ),
        items = listOf(
            RoutineItem(routineId = 1, machineId = 1, position = 0, targetSets = 3, repsMin = 8, repsMax = 12),
            RoutineItem(routineId = 1, machineId = 3, position = 1, targetSets = 2, repsMin = 10, repsMax = 10),
        ),
        sessions = listOf(
            Session(1, Instant.parse("2026-10-27T18:05:00.123456789Z"), Instant.parse("2026-10-27T19:10:00.5Z"), 1, "Felt \"strong\"\nline two, caf\u00e9"),
            Session(2, Instant.parse("2026-11-02T10:00:00Z"), null, null, null),
        ),
        sets = listOf(
            WorkoutSet(1, 1, 1, 0, 120.0, 12, null, null, null, Instant.parse("2026-10-27T18:05:00.123456789Z")),
            WorkoutSet(2, 1, 1, 1, null, null, 45, 7, 1500, Instant.parse("2026-10-27T18:08:00Z")),
            WorkoutSet(3, 2, 3, 0, 0.0, 8, null, null, null, Instant.parse("2026-11-02T10:02:00Z")),
            WorkoutSet(4, 2, 3, 1, 20.0, 15, null, null, null, Instant.parse("2026-11-02T10:03:00Z"), SetType.WARMUP),
            WorkoutSet(5, 2, 3, 2, 30.0, 10, null, null, null, Instant.parse("2026-11-02T10:04:00Z"), SetType.DROP),
            WorkoutSet(6, 2, 3, 3, 30.0, 6, null, null, null, Instant.parse("2026-11-02T10:05:00Z"), SetType.FAILURE),
        ),
    )

    private val text: String get() = BackupCodec.encode(data, exportedAt)

    private fun decoded(json: String): BackupDecodeResult = BackupCodec.decode(json)

    private fun assertInvalid(json: String) {
        assertTrue("expected Invalid for: ${json.take(120)}", decoded(json) is BackupDecodeResult.Invalid)
    }

    private fun assertOk(json: String): BackupDecodeResult.Ok {
        val result = decoded(json)
        assertTrue("expected Ok, got $result", result is BackupDecodeResult.Ok)
        return result as BackupDecodeResult.Ok
    }

    // Round trip

    @Test
    fun `a realistic history comes back unchanged`() {
        assertEquals(BackupDecodeResult.Ok(data, exportedAt), decoded(text))
    }

    @Test
    fun `an empty history comes back unchanged`() {
        val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        assertEquals(BackupDecodeResult.Ok(empty, exportedAt), BackupCodec.decode(BackupCodec.encode(empty, exportedAt)))
    }

    @Test
    fun `ids beyond the double precision stay exact`() {
        val ok = assertOk(text)

        assertEquals(9_007_199_254_740_993L, ok.data.machines[1].id)
    }

    @Test
    fun `names and notes with quotes, newlines, tabs and unicode come back exactly`() {
        val ok = assertOk(text)

        assertEquals(data.machines[2].name, ok.data.machines[2].name)
        assertEquals(data.machines[2].setupNote, ok.data.machines[2].setupNote)
        assertEquals(data.sessions[0].note, ok.data.sessions[0].note)
        assertEquals(data.routines[1].name, ok.data.routines[1].name)
    }

    @Test
    fun `instants keep their nanoseconds`() {
        val ok = assertOk(text)

        assertEquals(Instant.parse("2026-10-27T18:05:00.123456789Z"), ok.data.sessions[0].startedAt)
    }

    @Test
    fun `the file names the app, the version, the export time and the weekdays by name`() {
        assertTrue(text.startsWith("{\"app\":\"boratreino\",\"version\":6"))
        assertTrue(text.contains("\"exportedAt\":\"2026-11-02T20:15:30.250Z\""))
        assertTrue(text.contains("\"weekdays\":[\"MONDAY\",\"WEDNESDAY\"]"))
        assertTrue(text.contains("\"trainingDays\":[\"MONDAY\",\"FRIDAY\"]"))
        assertTrue(text.contains("\"muscleGroup\":\"LEGS\""))
        assertTrue(text.contains("\"scheduleMode\":\"WEEKDAYS\""))
    }

    // Forward compatibility and optional fields

    @Test
    fun `unknown fields are ignored at the top and inside an object`() {
        val withExtras = text
            .replaceFirst("{", "{\"futureField\":{\"deep\":[1,2.5,true,null,\"x\"]},")
            .replaceFirst("\"archived\":false", "\"archived\":false,\"colour\":\"red\"")

        assertEquals(BackupDecodeResult.Ok(data, exportedAt), decoded(withExtras))
    }

    @Test
    fun `optional fields may be absent or null`() {
        val json = """
            {"app":"boratreino","version":1,
             "machines":[{"id":1,"name":"Leg press","muscleGroup":"LEGS","kind":"WEIGHT_REPS","stepKg":2.5,"archived":false}],
             "plans":[{"id":1,"scheduleMode":"IN_TURN"}],
             "routines":[],"items":[],
             "sessions":[{"id":4,"startedAt":"2026-11-02T10:00:00Z"}],
             "sets":[{"id":7,"sessionId":4,"machineId":1,"position":0,"loggedAt":"2026-11-02T10:01:00Z"}]}
        """.trimIndent()

        val ok = assertOk(json)

        assertNull(ok.exportedAt)
        assertNull(ok.data.machines.single().setupNote)
        assertNull(ok.data.machines.single().restSeconds)
        assertNull(ok.data.sessions.single().endedAt)
        assertNull(ok.data.sessions.single().routineId)
        assertNull(ok.data.sets.single().weightKg)
        assertNull(ok.data.sets.single().reps)
    }

    @Test
    fun `an explicit null exported time is accepted`() {
        val ok = assertOk(text.replace("\"exportedAt\":\"2026-11-02T20:15:30.250Z\"", "\"exportedAt\":null"))

        assertNull(ok.exportedAt)
    }

    @Test
    fun `unicode escapes and escaped slashes are read`() {
        val json = """
            {"app":"boratreino","version":1,"machines":[{"id":1,"name":"caf\u00e9 \/ \"x\"","muscleGroup":"LEGS","kind":"WEIGHT_REPS","stepKg":2.5,"archived":false}],
             "plans":[],"routines":[],"items":[],"sessions":[],"sets":[]}
        """.trimIndent()

        assertEquals("caf\u00e9 / \"x\"", assertOk(json).data.machines.single().name)
    }

    // Not a backup

    @Test
    fun `text that is not JSON is invalid`() {
        assertInvalid("not json")
        assertInvalid("")
        assertInvalid("   ")
        assertInvalid("{")
        assertInvalid("[]")
        assertInvalid("\"boratreino\"")
        assertInvalid("null")
    }

    @Test
    fun `a truncated backup is invalid`() {
        assertInvalid(text.take(text.length / 2))
        assertInvalid(text.dropLast(1))
    }

    @Test
    fun `a file from another app is invalid`() {
        assertInvalid(text.replace("\"app\":\"boratreino\"", "\"app\":\"podouvir\""))
        assertInvalid(text.replace("\"app\":\"boratreino\",", ""))
    }

    @Test
    fun `deeply nested garbage is invalid and does not throw`() {
        assertInvalid("[".repeat(100_000))
        assertInvalid("{\"a\":".repeat(10_000))
        assertInvalid("{\"app\":\"boratreino\",\"version\":1,\"x\":" + "[".repeat(5_000))
    }

    @Test
    fun `decode never throws on odd input`() {
        val odd = listOf(
            "-", "1e400", "\"\\u12\"", "\"\\q\"", "{\"app\":\"boratreino\",\"version\":1,\"machines\":[{\"id\":1e400}]}",
            "{\"app\":\"boratreino\",\"version\":1,\"machines\":\"x\"}", "true false",
        )
        for (json in odd) {
            val result = decoded(json)
            assertTrue("$json gave $result", result is BackupDecodeResult.Invalid)
        }
    }

    // Version

    @Test
    fun `version 0 is invalid`() {
        assertInvalid(text.replace("\"version\":6", "\"version\":0"))
    }

    @Test
    fun `a negative version is invalid`() {
        assertInvalid(text.replace("\"version\":6", "\"version\":-1"))
    }

    @Test
    fun `a missing, text or fractional version is invalid`() {
        assertInvalid(text.replace("\"version\":6,", ""))
        assertInvalid(text.replace("\"version\":6", "\"version\":\"1\""))
        assertInvalid(text.replace("\"version\":6", "\"version\":1.0"))
    }

    @Test
    fun `a newer version is too new, even when its fields are odd`() {
        assertEquals(BackupDecodeResult.TooNew(7), decoded(text.replace("\"version\":6", "\"version\":7")))
        assertEquals(
            BackupDecodeResult.TooNew(7),
            decoded("{\"app\":\"boratreino\",\"version\":7,\"machines\":\"odd\",\"sets\":42}"),
        )
        assertEquals(BackupDecodeResult.TooNew(99), decoded("{\"app\":\"boratreino\",\"version\":99}"))
    }

    // Shape and references

    @Test
    fun `a backup missing its lists is invalid`() {
        assertInvalid("{\"app\":\"boratreino\",\"version\":1}")
    }

    @Test
    fun `a whole number given as 5 0 is invalid`() {
        assertTrue(text.contains("\"targetSets\":3"))
        assertInvalid(text.replace("\"targetSets\":3", "\"targetSets\":3.0"))
    }

    @Test
    fun `a required field that is missing is invalid`() {
        assertTrue(text.contains("\"stepKg\":5.0"))
        assertInvalid(text.replace("\"stepKg\":5.0,", ""))
        assertInvalid(text.replace("\"loggedAt\":\"2026-11-02T10:02:00Z\"", "\"loggedAt\":null"))
    }

    @Test
    fun `an exported time that is not an instant is invalid`() {
        assertInvalid(text.replace("\"exportedAt\":\"2026-11-02T20:15:30.250Z\"", "\"exportedAt\":\"yesterday\""))
        assertInvalid(text.replace("\"exportedAt\":\"2026-11-02T20:15:30.250Z\"", "\"exportedAt\":12"))
    }

    @Test
    fun `a set whose session is missing is invalid`() {
        assertInvalid(BackupCodec.encode(data.copy(sets = data.sets + data.sets[0].copy(id = 50, sessionId = 99)), exportedAt))
    }

    @Test
    fun `a set whose machine is missing is invalid`() {
        assertInvalid(BackupCodec.encode(data.copy(sets = data.sets + data.sets[0].copy(id = 50, machineId = 99)), exportedAt))
    }

    @Test
    fun `a routine item whose routine is missing is invalid`() {
        val broken = data.items + data.items[0].copy(routineId = 99)
        assertInvalid(BackupCodec.encode(data.copy(items = broken), exportedAt))
    }

    @Test
    fun `a routine item whose machine is missing is invalid`() {
        val broken = data.items + data.items[0].copy(machineId = 99)
        assertInvalid(BackupCodec.encode(data.copy(items = broken), exportedAt))
    }

    @Test
    fun `a routine whose plan is missing is invalid`() {
        val broken = data.routines + data.routines[0].copy(id = 5, planId = 99)
        assertInvalid(BackupCodec.encode(data.copy(routines = broken), exportedAt))
    }

    @Test
    fun `a session whose routine is unknown is invalid`() {
        val broken = data.sessions + data.sessions[1].copy(id = 5, routineId = 99)
        assertInvalid(BackupCodec.encode(data.copy(sessions = broken), exportedAt))
    }

    // Model rules

    @Test
    fun `a blank machine name is invalid`() {
        assertInvalid(text.replace("\"name\":\"Leg press\"", "\"name\":\"   \""))
    }

    @Test
    fun `a negative number of reps is invalid`() {
        assertInvalid(text.replace("\"reps\":12", "\"reps\":-1"))
    }

    @Test
    fun `a session that ends before it starts is invalid`() {
        assertTrue(text.contains("\"endedAt\":\"2026-10-27T19:10:00.500Z\""))
        assertInvalid(text.replace("\"endedAt\":\"2026-10-27T19:10:00.500Z\"", "\"endedAt\":\"2026-10-27T17:00:00Z\""))
    }

    @Test
    fun `a routine item with no sets is invalid`() {
        assertInvalid(text.replace("\"targetSets\":3", "\"targetSets\":0"))
    }

    // Set types

    @Test
    fun `the format version is 6`() {
        assertEquals(6, BackupCodec.FORMAT_VERSION)
    }

    @Test
    fun `a version 2 file without training days imports with every day`() {
        val json = """
            {"app":"boratreino","version":2,
             "machines":[],
             "plans":[{"id":1,"scheduleMode":"IN_TURN"}],
             "routines":[{"id":1,"planId":1,"name":"Routine A","shortName":"A","position":0,"weekdays":[]}],
             "items":[],"sessions":[],"sets":[]}
        """.trimIndent()

        val plan = assertOk(json).data.plans.single()

        assertEquals(ScheduleMode.IN_TURN, plan.scheduleMode)
        assertEquals(emptySet<DayOfWeek>(), plan.trainingDays)
    }

    @Test
    fun `a version 1 plan without training days imports with every day`() {
        val json = """
            {"app":"boratreino","version":1,"machines":[],
             "plans":[{"id":1,"scheduleMode":"WEEKDAYS"}],
             "routines":[],"items":[],"sessions":[],"sets":[]}
        """.trimIndent()

        assertEquals(emptySet<DayOfWeek>(), assertOk(json).data.plans.single().trainingDays)
    }

    @Test
    fun `training days are read back by weekday, whatever their order in the file`() {
        val json = text.replace("\"trainingDays\":[\"MONDAY\",\"FRIDAY\"]", "\"trainingDays\":[\"SATURDAY\",\"MONDAY\",\"WEDNESDAY\"]")

        assertEquals(
            setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            assertOk(json).data.plans.single().trainingDays,
        )
    }

    @Test
    fun `an unknown training day is invalid`() {
        assertInvalid(text.replace("\"trainingDays\":[\"MONDAY\",\"FRIDAY\"]", "\"trainingDays\":[\"FUNDAY\"]"))
    }

    @Test
    fun `training days that are not a list of weekday names are invalid`() {
        assertInvalid(text.replace("\"trainingDays\":[\"MONDAY\",\"FRIDAY\"]", "\"trainingDays\":\"MONDAY\""))
        assertInvalid(text.replace("\"trainingDays\":[\"MONDAY\",\"FRIDAY\"]", "\"trainingDays\":[1]"))
    }

    @Test
    fun `every set type is written and read back`() {
        assertTrue(text.contains("\"type\":\"NORMAL\""))
        assertTrue(text.contains("\"type\":\"WARMUP\""))
        assertTrue(text.contains("\"type\":\"DROP\""))
        assertTrue(text.contains("\"type\":\"FAILURE\""))
        val types = assertOk(text).data.sets.map { it.type }
        assertEquals(
            listOf(SetType.NORMAL, SetType.NORMAL, SetType.NORMAL, SetType.WARMUP, SetType.DROP, SetType.FAILURE),
            types,
        )
    }

    private fun versionOneFile() = """
        {"app":"boratreino","version":1,
         "machines":[{"id":1,"name":"Leg press","muscleGroup":"LEGS","kind":"WEIGHT_REPS","stepKg":2.5,"archived":false}],
         "plans":[],"routines":[],"items":[],
         "sessions":[{"id":4,"startedAt":"2026-11-02T10:00:00Z"}],
         "sets":[{"id":7,"sessionId":4,"machineId":1,"position":0,"weightKg":40.0,"reps":10,"loggedAt":"2026-11-02T10:01:00Z"}]}
    """.trimIndent()

    @Test
    fun `a version 1 file without a type imports its sets as normal`() {
        val ok = assertOk(versionOneFile())

        assertEquals(SetType.NORMAL, ok.data.sets.single().type)
        assertEquals(40.0, ok.data.sets.single().weightKg!!, 0.0)
    }

    @Test
    fun `an unknown or null set type reads as normal`() {
        val withUnknown = versionOneFile()
            .replace("\"version\":1", "\"version\":2")
            .replace("\"position\":0,", "\"position\":0,\"type\":\"SUPERSET\",")
        assertEquals(SetType.NORMAL, assertOk(withUnknown).data.sets.single().type)

        val withNull = versionOneFile().replace("\"position\":0,", "\"position\":0,\"type\":null,")
        assertEquals(SetType.NORMAL, assertOk(withNull).data.sets.single().type)
    }

    @Test
    fun `a version 7 file is too new`() {
        assertEquals(BackupDecodeResult.TooNew(7), decoded(versionOneFile().replace("\"version\":1", "\"version\":7")))
    }

    @Test
    fun `a version 5 file is read`() {
        assertOk(versionOneFile().replace("\"version\":1", "\"version\":5"))
    }

    @Test
    fun `a version 4 file is read`() {
        assertOk(versionOneFile().replace("\"version\":1", "\"version\":4"))
    }

    @Test
    fun `a version 3 file is read`() {
        assertOk(versionOneFile().replace("\"version\":1", "\"version\":3"))
    }

    // Exercise library link (format version 5)

    @Test
    fun `a machine's library id comes back`() {
        val linked = data.copy(machines = data.machines.map { if (it.id == 1L) it.copy(libraryId = "Leg_Press") else it })

        assertEquals(BackupDecodeResult.Ok(linked, exportedAt), decoded(BackupCodec.encode(linked, exportedAt)))
    }

    @Test
    fun `a version 5 machine with a library id reads it`() {
        val json = versionOneFile()
            .replace("\"version\":1", "\"version\":5")
            .replace("\"archived\":false}", "\"archived\":false,\"libraryId\":\"Leg_Press\"}")

        assertEquals("Leg_Press", assertOk(json).data.machines.single().libraryId)
    }

    @Test
    fun `a machine without a library id, as in an older file, reads as none`() {
        assertNull(assertOk(versionOneFile().replace("\"version\":1", "\"version\":4")).data.machines.single().libraryId)
        assertNull(assertOk(versionOneFile().replace("\"version\":1", "\"version\":5")).data.machines.single().libraryId)
    }

    // Planned workouts (format version 4)

    private val planned = listOf(
        PlannedWorkout(LocalDate.of(2026, 11, 12), 1L),
        PlannedWorkout(LocalDate.of(2026, 11, 14), 2L),
    )

    @Test
    fun `planned workouts come back unchanged`() {
        val withPlans = data.copy(plannedWorkouts = planned)

        assertEquals(BackupDecodeResult.Ok(withPlans, exportedAt), decoded(BackupCodec.encode(withPlans, exportedAt)))
    }

    @Test
    fun `planned workouts are written with their date, routine and position`() {
        val json = BackupCodec.encode(data.copy(plannedWorkouts = planned), exportedAt)

        assertTrue(
            json.contains(
                "\"plannedWorkouts\":[{\"date\":\"2026-11-12\",\"routineId\":1,\"position\":0},{\"date\":\"2026-11-14\",\"routineId\":2,\"position\":0}]",
            ),
        )
    }

    @Test
    fun `planned positions come back, and are written sorted by date, then position, then routine`() {
        val unordered = listOf(
            PlannedWorkout(LocalDate.of(2026, 11, 12), 2L, 1),
            PlannedWorkout(LocalDate.of(2026, 11, 14), 2L, 0),
            PlannedWorkout(LocalDate.of(2026, 11, 12), 1L, 0),
        )
        val sorted = listOf(
            PlannedWorkout(LocalDate.of(2026, 11, 12), 1L, 0),
            PlannedWorkout(LocalDate.of(2026, 11, 12), 2L, 1),
            PlannedWorkout(LocalDate.of(2026, 11, 14), 2L, 0),
        )
        val json = BackupCodec.encode(data.copy(plannedWorkouts = unordered), exportedAt)

        assertTrue(
            json.contains(
                "\"plannedWorkouts\":[{\"date\":\"2026-11-12\",\"routineId\":1,\"position\":0},{\"date\":\"2026-11-12\",\"routineId\":2,\"position\":1},{\"date\":\"2026-11-14\",\"routineId\":2,\"position\":0}]",
            ),
        )
        assertEquals(BackupDecodeResult.Ok(data.copy(plannedWorkouts = sorted), exportedAt), decoded(json))
    }

    @Test
    fun `a version 5 file without planned positions reads every plan at position 0`() {
        val v5 = """
            {"app":"boratreino","version":5,
             "machines":[{"id":1,"name":"Leg press","muscleGroup":"LEGS","kind":"WEIGHT_REPS","stepKg":2.5,"archived":false}],
             "plans":[{"id":1,"scheduleMode":"IN_TURN","trainingDays":[]}],
             "routines":[{"id":1,"planId":1,"name":"Routine A","shortName":"A","position":0,"weekdays":[]},
                         {"id":2,"planId":1,"name":"Routine B","shortName":"B","position":1,"weekdays":[]}],
             "items":[],"sessions":[],"sets":[],
             "plannedWorkouts":[{"date":"2026-11-12","routineId":2},{"date":"2026-11-12","routineId":1},{"date":"2026-11-14","routineId":2}]}
        """.trimIndent()

        val ok = assertOk(v5)

        assertEquals(
            listOf(
                PlannedWorkout(LocalDate.of(2026, 11, 12), 2L, 0),
                PlannedWorkout(LocalDate.of(2026, 11, 12), 1L, 0),
                PlannedWorkout(LocalDate.of(2026, 11, 14), 2L, 0),
            ),
            ok.data.plannedWorkouts,
        )
    }

    @Test
    fun `a version 3 file without planned workouts imports with none`() {
        val v3 = """
            {"app":"boratreino","version":3,
             "machines":[{"id":1,"name":"Leg press","muscleGroup":"LEGS","kind":"WEIGHT_REPS","stepKg":2.5,"archived":false}],
             "plans":[{"id":1,"scheduleMode":"IN_TURN","trainingDays":[]}],
             "routines":[{"id":1,"planId":1,"name":"Routine A","shortName":"A","position":0,"weekdays":[]}],
             "items":[],"sessions":[],"sets":[]}
        """.trimIndent()

        val ok = assertOk(v3)

        assertEquals(emptyList<PlannedWorkout>(), ok.data.plannedWorkouts)
        assertEquals(1, ok.data.routines.size)
    }

    @Test
    fun `a planned workout whose routine is missing from the file is invalid`() {
        assertInvalid(BackupCodec.encode(data.copy(plannedWorkouts = listOf(PlannedWorkout(LocalDate.of(2026, 11, 12), 99L))), exportedAt))
    }

    @Test
    fun `a planned workout with a bad date is invalid`() {
        val json = BackupCodec.encode(data.copy(plannedWorkouts = planned), exportedAt)
        assertTrue(json.contains("\"date\":\"2026-11-12\""))

        assertInvalid(json.replace("\"date\":\"2026-11-12\"", "\"date\":\"12/11/2026\""))
        assertInvalid(json.replace("\"date\":\"2026-11-12\"", "\"date\":\"2026-02-30\""))
        assertInvalid(json.replace("\"date\":\"2026-11-12\"", "\"date\":20261112"))
    }

    @Test
    fun `several routines planned on the same day are valid`() {
        val sameDay = listOf(PlannedWorkout(LocalDate.of(2026, 11, 12), 1L), PlannedWorkout(LocalDate.of(2026, 11, 12), 2L))
        val withPlans = data.copy(plannedWorkouts = sameDay)

        assertEquals(BackupDecodeResult.Ok(withPlans, exportedAt), decoded(BackupCodec.encode(withPlans, exportedAt)))
    }

    @Test
    fun `the same routine planned twice on the same day is invalid`() {
        val twice = listOf(PlannedWorkout(LocalDate.of(2026, 11, 12), 1L), PlannedWorkout(LocalDate.of(2026, 11, 12), 1L))

        assertInvalid(BackupCodec.encode(data.copy(plannedWorkouts = twice), exportedAt))
    }
}

package com.boratreino.core.database

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Migration 1 -> 2 adds workout_sets.type; migration 2 -> 3 adds routine_plans.trainingDays; migration
 * 3 -> 4 adds planned_workouts; 4 -> 5 makes its key (date, routineId); 5 -> 6 adds machines.libraryId; 6 -> 7 adds
 * planned_workouts.position; 7 -> 8 adds body_measurements; 8 -> 9 adds sessions.isExtra; 9 -> 10 and 10 -> 11 add cardio
 * columns to workout_sets; 11 -> 12 rebuilds workout_sets without the console values and with a REAL intensity; 12 -> 13
 * deletes the sessions without sets. Needs schemas/.../2.json to 13.json, which Room writes on the first build with those
 * versions (13.json is written by CI, so the 12 -> 13 tests fail until it is committed).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BoraTreinoDatabase::class.java,
    )

    private val name = "migration-test.db"

    private fun SupportSQLiteDatabase.insertV1Data() {
        execSQL(
            "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived) " +
                "VALUES (1, 'Leg press', 'LEGS', 'WEIGHT_REPS', 5.0, NULL, NULL, 0)",
        )
        execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note) VALUES (1, 1000, NULL, NULL, NULL)")
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, loggedAt) " +
                "VALUES (7, 1, 1, 3, 120.5, 12, NULL, NULL, NULL, 123456)",
        )
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, loggedAt) " +
                "VALUES (8, 1, 1, 4, NULL, NULL, 600, 80, 2500, 123999)",
        )
    }

    @Test
    fun `migrating 1 to 2 keeps every set field and marks the sets normal`() {
        helper.createDatabase(name, 1).apply {
            insertV1Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)

        db.query("SELECT id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, loggedAt, type FROM workout_sets ORDER BY id").use { c ->
            assertEquals(2, c.count)

            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
            assertEquals(1L, c.getLong(1))
            assertEquals(1L, c.getLong(2))
            assertEquals(3, c.getInt(3))
            assertEquals(120.5, c.getDouble(4), 0.0)
            assertEquals(12, c.getInt(5))
            assertEquals(true, c.isNull(6))
            assertEquals(true, c.isNull(7))
            assertEquals(true, c.isNull(8))
            assertEquals(123456L, c.getLong(9))
            assertEquals("NORMAL", c.getString(10))

            c.moveToNext()
            assertEquals(8L, c.getLong(0))
            assertEquals(true, c.isNull(4))
            assertEquals(true, c.isNull(5))
            assertEquals(600, c.getInt(6))
            assertEquals(80, c.getInt(7))
            assertEquals(2500, c.getInt(8))
            assertEquals("NORMAL", c.getString(10))
        }
    }

    @Test
    fun `the migrated database accepts a warm-up set`() {
        helper.createDatabase(name, 1).apply {
            insertV1Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)

        val values = ContentValues().apply {
            put("sessionId", 1L)
            put("machineId", 1L)
            put("position", 5)
            put("loggedAt", 200000L)
            put("type", "WARMUP")
        }
        db.insert("workout_sets", 0, values)

        db.query("SELECT type FROM workout_sets WHERE position = 5").use { c ->
            c.moveToFirst()
            assertEquals("WARMUP", c.getString(0))
        }
    }

    @Test
    fun `a set inserted without a type gets normal`() {
        helper.createDatabase(name, 1).apply {
            insertV1Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)

        db.execSQL(
            "INSERT INTO workout_sets (sessionId, machineId, position, loggedAt) VALUES (1, 1, 9, 5)",
        )

        db.query("SELECT type FROM workout_sets WHERE position = 9").use { c ->
            c.moveToFirst()
            assertEquals("NORMAL", c.getString(0))
        }
    }

    private fun SupportSQLiteDatabase.insertV2Data() {
        execSQL("INSERT INTO routine_plans (id, scheduleMode) VALUES (1, 'WEEKDAYS')")
        execSQL(
            "INSERT INTO routines (id, planId, name, shortName, position, weekdays) " +
                "VALUES (1, 1, 'Treino A', 'A', 0, '1,3,5')",
        )
        execSQL(
            "INSERT INTO routines (id, planId, name, shortName, position, weekdays) " +
                "VALUES (2, 1, 'Treino B', 'B', 1, '2,4')",
        )
    }

    @Test
    fun `migrating 2 to 3 keeps the plan and its routines and sets trainingDays to empty`() {
        helper.createDatabase(name, 2).apply {
            insertV2Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)

        db.query("SELECT id, scheduleMode, trainingDays FROM routine_plans").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals("WEEKDAYS", c.getString(1))
            assertEquals("", c.getString(2))
        }

        db.query("SELECT id, planId, name, shortName, position, weekdays FROM routines ORDER BY id").use { c ->
            assertEquals(2, c.count)

            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals(1L, c.getLong(1))
            assertEquals("Treino A", c.getString(2))
            assertEquals("A", c.getString(3))
            assertEquals(0, c.getInt(4))
            assertEquals("1,3,5", c.getString(5))

            c.moveToNext()
            assertEquals(2L, c.getLong(0))
            assertEquals(1L, c.getLong(1))
            assertEquals("Treino B", c.getString(2))
            assertEquals("B", c.getString(3))
            assertEquals(1, c.getInt(4))
            assertEquals("2,4", c.getString(5))
        }
    }

    @Test
    fun `a plan inserted after migrating 2 to 3 without trainingDays gets empty`() {
        helper.createDatabase(name, 2).apply {
            insertV2Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)

        db.execSQL("INSERT INTO routine_plans (scheduleMode) VALUES ('IN_TURN')")

        db.query("SELECT trainingDays FROM routine_plans WHERE scheduleMode = 'IN_TURN'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("", c.getString(0))
        }
    }

    @Test
    fun `migrating 1 to 3 through both steps keeps the sets and gives plans empty trainingDays`() {
        helper.createDatabase(name, 1).apply {
            insertV1Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        db.query("SELECT id, weightKg, reps, seconds, type FROM workout_sets ORDER BY id").use { c ->
            assertEquals(2, c.count)

            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
            assertEquals(120.5, c.getDouble(1), 0.0)
            assertEquals(12, c.getInt(2))
            assertEquals("NORMAL", c.getString(4))

            c.moveToNext()
            assertEquals(8L, c.getLong(0))
            assertEquals(600, c.getInt(3))
            assertEquals("NORMAL", c.getString(4))
        }

        db.execSQL("INSERT INTO routine_plans (scheduleMode) VALUES ('IN_TURN')")

        db.query("SELECT trainingDays FROM routine_plans").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("", c.getString(0))
        }
    }

    private fun SupportSQLiteDatabase.insertV3Data() {
        execSQL("INSERT INTO routine_plans (id, scheduleMode, trainingDays) VALUES (1, 'IN_TURN', '')")
        execSQL(
            "INSERT INTO routines (id, planId, name, shortName, position, weekdays) " +
                "VALUES (1, 1, 'Treino A', 'A', 0, '')",
        )
        execSQL(
            "INSERT INTO routines (id, planId, name, shortName, position, weekdays) " +
                "VALUES (2, 1, 'Treino B', 'B', 1, '')",
        )
    }

    @Test
    fun `migrating 3 to 4 keeps the plan and routines and starts with no planned workouts`() {
        helper.createDatabase(name, 3).apply {
            insertV3Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4)

        db.query("SELECT COUNT(*) FROM routines").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM planned_workouts").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `the migrated database keeps one planned routine per date`() {
        helper.createDatabase(name, 3).apply {
            insertV3Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4)

        db.execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 1)")
        db.execSQL("INSERT OR REPLACE INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 2)")

        db.query("SELECT date, routineId FROM planned_workouts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("2026-11-12", c.getString(0))
            assertEquals(2L, c.getLong(1))
        }
    }

    @Test
    fun `deleting a routine of the migrated database removes its planned workouts`() {
        helper.createDatabase(name, 3).apply {
            insertV3Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 2)")
        db.execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-13', 1)")

        db.execSQL("DELETE FROM routines WHERE id = 2")

        db.query("SELECT date, routineId FROM planned_workouts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("2026-11-13", c.getString(0))
        }
    }

    // 4 -> 5: the planned workout's key becomes (date, routineId), so several routines can share a date.

    private fun SupportSQLiteDatabase.insertV4Data() {
        insertV3Data()
        execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 1)")
        execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-13', 2)")
    }

    @Test
    fun `migrating 4 to 5 keeps the planned workouts that were there`() {
        helper.createDatabase(name, 4).apply {
            insertV4Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5)

        db.query("SELECT date, routineId FROM planned_workouts ORDER BY date").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("2026-11-12", c.getString(0))
            assertEquals(1L, c.getLong(1))
            c.moveToNext()
            assertEquals("2026-11-13", c.getString(0))
            assertEquals(2L, c.getLong(1))
        }
    }

    @Test
    fun `after 4 to 5 two routines can be planned on the same date`() {
        helper.createDatabase(name, 4).apply {
            insertV4Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5)

        db.execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 2)")

        db.query("SELECT routineId FROM planned_workouts WHERE date = '2026-11-12' ORDER BY routineId").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            c.moveToNext()
            assertEquals(2L, c.getLong(0))
        }
    }

    @Test
    fun `after 4 to 5 the same routine is still planned only once on a date`() {
        helper.createDatabase(name, 4).apply {
            insertV4Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5)

        db.execSQL("INSERT OR IGNORE INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 1)")

        db.query("SELECT COUNT(*) FROM planned_workouts WHERE date = '2026-11-12'").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun `after 4 to 5 deleting a routine removes all its planned workouts and keeps the others`() {
        helper.createDatabase(name, 4).apply {
            insertV4Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 2)")

        db.execSQL("DELETE FROM routines WHERE id = 1")

        db.query("SELECT date, routineId FROM planned_workouts ORDER BY date").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("2026-11-12", c.getString(0))
            assertEquals(2L, c.getLong(1))
            c.moveToNext()
            assertEquals("2026-11-13", c.getString(0))
            assertEquals(2L, c.getLong(1))
        }
    }


    // 5 -> 6: machines get a library id, empty for every machine that was there.

    private fun SupportSQLiteDatabase.insertV5Data() {
        execSQL(
            "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived) " +
                "VALUES (1, 'Leg press', 'LEGS', 'WEIGHT_REPS', 5.0, NULL, NULL, 0)",
        )
        execSQL(
            "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived) " +
                "VALUES (2, 'Pull-ups', 'BACK', 'BODYWEIGHT_REPS', 2.5, 'Bar 2', 90, 1)",
        )
        execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note) VALUES (1, 1000, NULL, NULL, NULL)")
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, loggedAt, type) " +
                "VALUES (7, 1, 1, 0, 120.5, 12, NULL, NULL, NULL, 123456, 'NORMAL')",
        )
    }

    @Test
    fun `migrating 5 to 6 keeps the machines, their sets and their details`() {
        helper.createDatabase(name, 5).apply {
            insertV5Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6)

        db.query("SELECT id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived FROM machines ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals("Leg press", c.getString(1))
            assertEquals("LEGS", c.getString(2))
            assertEquals("WEIGHT_REPS", c.getString(3))
            assertEquals(5.0, c.getDouble(4), 0.0)
            assertEquals(true, c.isNull(5))
            assertEquals(true, c.isNull(6))
            assertEquals(0, c.getInt(7))
            c.moveToNext()
            assertEquals("Pull-ups", c.getString(1))
            assertEquals("Bar 2", c.getString(5))
            assertEquals(90, c.getInt(6))
            assertEquals(1, c.getInt(7))
        }
        db.query("SELECT COUNT(*) FROM workout_sets").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun `after 5 to 6 every existing machine has no library id`() {
        helper.createDatabase(name, 5).apply {
            insertV5Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6)

        db.query("SELECT COUNT(*) FROM machines WHERE libraryId IS NULL").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
    }

    @Test
    fun `after 5 to 6 a machine can carry a library id and one inserted without it has none`() {
        helper.createDatabase(name, 5).apply {
            insertV5Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6)

        db.execSQL("UPDATE machines SET libraryId = 'Leg_Press' WHERE id = 1")
        db.execSQL(
            "INSERT INTO machines (name, muscleGroup, kind, stepKg, archived) VALUES ('Home rig', 'CORE', 'WEIGHT_REPS', 2.5, 0)",
        )

        db.query("SELECT name, libraryId FROM machines ORDER BY id").use { c ->
            assertEquals(3, c.count)
            c.moveToFirst()
            assertEquals("Leg press", c.getString(0))
            assertEquals("Leg_Press", c.getString(1))
            c.moveToNext()
            assertEquals("Pull-ups", c.getString(0))
            assertEquals(true, c.isNull(1))
            c.moveToNext()
            assertEquals("Home rig", c.getString(0))
            assertEquals(true, c.isNull(1))
        }
    }

    // 6 -> 7: planned workouts get an explicit order inside their date; old rows get 0 and keep the routine order.

    private fun SupportSQLiteDatabase.insertV6Data() {
        insertV3Data()
        execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 2)")
        execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-12', 1)")
        execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-13', 1)")
    }

    @Test
    fun `migrating 6 to 7 keeps the planned workouts and gives each one position 0`() {
        helper.createDatabase(name, 6).apply {
            insertV6Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7)

        db.query("SELECT date, routineId, position FROM planned_workouts ORDER BY date, routineId").use { c ->
            assertEquals(3, c.count)
            c.moveToFirst()
            assertEquals("2026-11-12", c.getString(0))
            assertEquals(1L, c.getLong(1))
            assertEquals(0, c.getInt(2))
            c.moveToNext()
            assertEquals("2026-11-12", c.getString(0))
            assertEquals(2L, c.getLong(1))
            assertEquals(0, c.getInt(2))
            c.moveToNext()
            assertEquals("2026-11-13", c.getString(0))
            assertEquals(1L, c.getLong(1))
            assertEquals(0, c.getInt(2))
        }
    }

    @Test
    fun `after 6 to 7 planned workouts of one date fall back to routine id order`() {
        helper.createDatabase(name, 6).apply {
            insertV6Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7)

        db.query("SELECT routineId FROM planned_workouts WHERE date = '2026-11-12' ORDER BY position, routineId").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            c.moveToNext()
            assertEquals(2L, c.getLong(0))
        }
    }

    @Test
    fun `after 6 to 7 a planned workout inserted without a position gets 0 and an explicit position is kept`() {
        helper.createDatabase(name, 6).apply {
            insertV6Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7)

        db.execSQL("INSERT INTO planned_workouts (date, routineId) VALUES ('2026-11-14', 2)")
        db.execSQL("INSERT INTO planned_workouts (date, routineId, position) VALUES ('2026-11-14', 1, 5)")

        db.query("SELECT routineId, position FROM planned_workouts WHERE date = '2026-11-14' ORDER BY position").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(2L, c.getLong(0))
            assertEquals(0, c.getInt(1))
            c.moveToNext()
            assertEquals(1L, c.getLong(0))
            assertEquals(5, c.getInt(1))
        }
    }

    // 7 -> 8: body measurements table; existing data is untouched.

    @Test
    fun `migrating 7 to 8 creates an empty measurements table and keeps planned workouts`() {
        helper.createDatabase(name, 7).apply {
            insertV3Data()
            execSQL("INSERT INTO planned_workouts (date, routineId, position) VALUES ('2026-11-12', 2, 3)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 8, true, MIGRATION_7_8)

        db.query("SELECT COUNT(*) FROM body_measurements").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT date, routineId, position FROM planned_workouts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("2026-11-12", c.getString(0))
            assertEquals(2L, c.getLong(1))
            assertEquals(3, c.getInt(2))
        }
    }

    @Test
    fun `after 7 to 8 measurements get generated ids and keep their fields`() {
        helper.createDatabase(name, 7).close()
        val db = helper.runMigrationsAndValidate(name, 8, true, MIGRATION_7_8)

        db.execSQL("INSERT INTO body_measurements (date, type, valueCm) VALUES ('2026-11-12', 'WAIST', 82.5)")
        db.execSQL("INSERT INTO body_measurements (date, type, valueCm) VALUES ('2026-11-13', 'ARM', 35.0)")

        db.query("SELECT id, date, type, valueCm FROM body_measurements ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals("2026-11-12", c.getString(1))
            assertEquals("WAIST", c.getString(2))
            assertEquals(82.5, c.getDouble(3), 0.0)
            c.moveToNext()
            assertEquals(2L, c.getLong(0))
            assertEquals("ARM", c.getString(2))
            assertEquals(35.0, c.getDouble(3), 0.0)
        }
    }

    // 8 -> 9: sessions.isExtra (the 9.json schema is written by CI).

    @Test
    fun `migrating 8 to 9 keeps sessions and marks them as not extra`() {
        helper.createDatabase(name, 8).apply {
            insertV3Data()
            execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note) VALUES (1, 1000, 2000, 1, NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 9, true, MIGRATION_8_9)

        db.query("SELECT id, startedAt, isExtra FROM sessions").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals(1000L, c.getLong(1))
            assertEquals(0, c.getInt(2))
        }
    }

    @Test
    fun `after 8 to 9 a new session defaults to not extra and the flag can be set`() {
        helper.createDatabase(name, 8).close()
        val db = helper.runMigrationsAndValidate(name, 9, true, MIGRATION_8_9)

        db.execSQL("INSERT INTO sessions (startedAt, endedAt, routineId, note) VALUES (2000, 3000, NULL, NULL)")
        db.execSQL("INSERT INTO sessions (startedAt, endedAt, routineId, note, isExtra) VALUES (4000, 5000, NULL, NULL, 1)")

        db.query("SELECT isExtra FROM sessions ORDER BY startedAt").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
            c.moveToNext()
            assertEquals(1, c.getInt(0))
        }
    }

    // 9 -> 10: workout_sets.intensity (the 10.json schema is written by CI).

    @Test
    fun `migrating 9 to 10 keeps sets and leaves their intensity empty`() {
        helper.createDatabase(name, 9).apply {
            execSQL(
                "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived, libraryId) " +
                    "VALUES (1, 'Bike', 'CARDIO', 'CARDIO', 5.0, NULL, NULL, 0, NULL)",
            )
            execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (1, 1000, NULL, NULL, NULL, 0)")
            execSQL(
                "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, loggedAt, type) " +
                    "VALUES (8, 1, 1, 4, NULL, NULL, 2700, 520, 18400, 123999, 'NORMAL')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 10, true, MIGRATION_9_10)

        db.query("SELECT id, seconds, calories, distanceM, type, intensity FROM workout_sets").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(8L, c.getLong(0))
            assertEquals(2700, c.getInt(1))
            assertEquals(520, c.getInt(2))
            assertEquals(18400, c.getInt(3))
            assertEquals("NORMAL", c.getString(4))
            assertEquals(true, c.isNull(5))
        }
    }

    @Test
    fun `after 9 to 10 a new set can store an intensity or none`() {
        helper.createDatabase(name, 9).apply {
            execSQL(
                "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived, libraryId) " +
                    "VALUES (1, 'Bike', 'CARDIO', 'CARDIO', 5.0, NULL, NULL, 0, NULL)",
            )
            execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (1, 1000, NULL, NULL, NULL, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 10, true, MIGRATION_9_10)

        db.execSQL("INSERT INTO workout_sets (sessionId, machineId, position, loggedAt, intensity) VALUES (1, 1, 1, 5, 12)")
        db.execSQL("INSERT INTO workout_sets (sessionId, machineId, position, loggedAt) VALUES (1, 1, 2, 6)")

        db.query("SELECT intensity FROM workout_sets ORDER BY position").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(12, c.getInt(0))
            c.moveToNext()
            assertEquals(true, c.isNull(0))
        }
    }

    // 10 -> 11: workout_sets.watts / heartRate / wattsPerKg / tss / intensityFactor (the 11.json schema is written by CI).

    @Test
    fun `migrating 10 to 11 keeps sets and leaves the console values empty`() {
        helper.createDatabase(name, 10).apply {
            execSQL(
                "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived, libraryId) " +
                    "VALUES (1, 'Bike', 'CARDIO', 'CARDIO', 5.0, NULL, NULL, 0, NULL)",
            )
            execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (1, 1000, NULL, NULL, NULL, 0)")
            execSQL(
                "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, loggedAt, type, intensity) " +
                    "VALUES (8, 1, 1, 4, NULL, NULL, 2700, 520, 18400, 123999, 'NORMAL', 12)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 11, true, MIGRATION_10_11)

        db.query("SELECT id, seconds, calories, distanceM, intensity, watts, heartRate, wattsPerKg, tss, intensityFactor FROM workout_sets").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(8L, c.getLong(0))
            assertEquals(2700, c.getInt(1))
            assertEquals(520, c.getInt(2))
            assertEquals(18400, c.getInt(3))
            assertEquals(12, c.getInt(4))
            for (column in 5..9) assertEquals(true, c.isNull(column))
        }
    }

    @Test
    fun `after 10 to 11 a new set can store the console values or none`() {
        helper.createDatabase(name, 10).apply {
            execSQL(
                "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived, libraryId) " +
                    "VALUES (1, 'Bike', 'CARDIO', 'CARDIO', 5.0, NULL, NULL, 0, NULL)",
            )
            execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (1, 1000, NULL, NULL, NULL, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 11, true, MIGRATION_10_11)

        db.execSQL(
            "INSERT INTO workout_sets (sessionId, machineId, position, loggedAt, watts, heartRate, wattsPerKg, tss, intensityFactor) " +
                "VALUES (1, 1, 1, 5, 160, 142, 2.3, 48, 0.79)",
        )
        db.execSQL("INSERT INTO workout_sets (sessionId, machineId, position, loggedAt) VALUES (1, 1, 2, 6)")

        db.query("SELECT watts, heartRate, wattsPerKg, tss, intensityFactor FROM workout_sets ORDER BY position").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(160, c.getInt(0))
            assertEquals(142, c.getInt(1))
            assertEquals(2.3, c.getDouble(2), 0.0001)
            assertEquals(48, c.getInt(3))
            assertEquals(0.79, c.getDouble(4), 0.0001)
            c.moveToNext()
            for (column in 0..4) assertEquals(true, c.isNull(column))
        }
    }

    // 11 -> 12: workout_sets rebuilt without the console values; intensity becomes REAL (the 12.json schema is written by CI).

    private fun SupportSQLiteDatabase.insertV11Data() {
        execSQL(
            "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived, libraryId) " +
                "VALUES (1, 'Bike', 'CARDIO', 'CARDIO', 5.0, NULL, NULL, 0, NULL)",
        )
        execSQL(
            "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived, libraryId) " +
                "VALUES (2, 'Leg press', 'LEGS', 'WEIGHT_REPS', 5.0, 'seat 4', 90, 0, 'leg_press')",
        )
        execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (1, 1000, 2000, NULL, NULL, 0)")
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, " +
                "loggedAt, type, intensity, watts, heartRate, wattsPerKg, tss, intensityFactor) " +
                "VALUES (8, 1, 1, 1, NULL, NULL, 2700, 520, 18400, 123999, 'NORMAL', 12, 160, 142, 2.3, 48, 0.79)",
        )
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, " +
                "loggedAt, type, intensity) VALUES (9, 1, 1, 2, NULL, NULL, 600, NULL, NULL, 124999, 'NORMAL', 250)",
        )
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, " +
                "loggedAt, type) VALUES (10, 1, 2, 1, 120.5, 12, NULL, NULL, NULL, 125999, 'WARMUP')",
        )
    }

    @Test
    fun `migrating 11 to 12 keeps every set and carries intensity over as a decimal`() {
        helper.createDatabase(name, 11).apply {
            insertV11Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 12, true, MIGRATION_11_12)

        db.query(
            "SELECT id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, loggedAt, type, " +
                "intensity, typeof(intensity) FROM workout_sets ORDER BY id",
        ).use { c ->
            assertEquals(3, c.count)
            c.moveToFirst()
            assertEquals(8L, c.getLong(0))
            assertEquals(1L, c.getLong(1))
            assertEquals(1L, c.getLong(2))
            assertEquals(1, c.getInt(3))
            assertEquals(true, c.isNull(4))
            assertEquals(true, c.isNull(5))
            assertEquals(2700, c.getInt(6))
            assertEquals(520, c.getInt(7))
            assertEquals(18400, c.getInt(8))
            assertEquals(123999L, c.getLong(9))
            assertEquals("NORMAL", c.getString(10))
            assertEquals(12.0, c.getDouble(11), 0.0)
            assertEquals("real", c.getString(12))

            c.moveToNext()
            assertEquals(9L, c.getLong(0))
            assertEquals(600, c.getInt(6))
            assertEquals(true, c.isNull(7))
            assertEquals(true, c.isNull(11)) // 250 is above the new maximum of 100

            c.moveToNext()
            assertEquals(10L, c.getLong(0))
            assertEquals(2L, c.getLong(2))
            assertEquals(120.5, c.getDouble(4), 0.0)
            assertEquals(12, c.getInt(5))
            assertEquals(true, c.isNull(6))
            assertEquals(125999L, c.getLong(9))
            assertEquals("WARMUP", c.getString(10))
            assertEquals(true, c.isNull(11))
        }
    }

    @Test
    fun `after 11 to 12 the console columns are gone and intensity is REAL`() {
        helper.createDatabase(name, 11).apply {
            insertV11Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 12, true, MIGRATION_11_12)

        val columns = LinkedHashMap<String, String>()
        db.query("PRAGMA table_info(`workout_sets`)").use { c ->
            val nameIndex = c.getColumnIndexOrThrow("name")
            val typeIndex = c.getColumnIndexOrThrow("type")
            while (c.moveToNext()) columns[c.getString(nameIndex)] = c.getString(typeIndex)
        }
        assertEquals(
            listOf(
                "id", "sessionId", "machineId", "position", "weightKg", "reps", "seconds", "calories", "distanceM",
                "loggedAt", "type", "intensity",
            ),
            columns.keys.toList(),
        )
        assertEquals("REAL", columns["intensity"])
        db.query("SELECT name FROM sqlite_master WHERE name = 'workout_sets_new'").use { c -> assertEquals(0, c.count) }
    }

    @Test
    fun `after 11 to 12 the indices and foreign keys of workout_sets are intact`() {
        helper.createDatabase(name, 11).apply {
            insertV11Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 12, true, MIGRATION_11_12)

        val indices = mutableSetOf<String>()
        db.query("PRAGMA index_list(`workout_sets`)").use { c ->
            val nameIndex = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) indices += c.getString(nameIndex)
        }
        assertEquals(true, "index_workout_sets_sessionId" in indices)
        assertEquals(true, "index_workout_sets_machineId" in indices)

        val keys = mutableMapOf<String, Triple<String, String, String>>()
        db.query("PRAGMA foreign_key_list(`workout_sets`)").use { c ->
            val table = c.getColumnIndexOrThrow("table")
            val from = c.getColumnIndexOrThrow("from")
            val to = c.getColumnIndexOrThrow("to")
            val onDelete = c.getColumnIndexOrThrow("on_delete")
            while (c.moveToNext()) keys[c.getString(table)] = Triple(c.getString(from), c.getString(to), c.getString(onDelete))
        }
        assertEquals(Triple("sessionId", "id", "CASCADE"), keys["sessions"])
        assertEquals(Triple("machineId", "id", "RESTRICT"), keys["machines"])

        db.query("PRAGMA foreign_key_check(`workout_sets`)").use { c -> assertEquals(0, c.count) }

        // Deleting the session removes its sets (cascade still works on the rebuilt table).
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM sessions WHERE id = 1")
        db.query("SELECT COUNT(*) FROM workout_sets").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `after 11 to 12 a new set stores a decimal intensity and ids keep counting up`() {
        helper.createDatabase(name, 11).apply {
            insertV11Data()
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 12, true, MIGRATION_11_12)

        db.execSQL("INSERT INTO workout_sets (sessionId, machineId, position, loggedAt, seconds, intensity) VALUES (1, 1, 3, 7, 1200, 0.79)")
        db.execSQL("INSERT INTO workout_sets (sessionId, machineId, position, loggedAt, seconds) VALUES (1, 1, 4, 8, 600)")

        db.query("SELECT id, intensity, type FROM workout_sets WHERE position >= 3 AND machineId = 1 ORDER BY position").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(11L, c.getLong(0))
            assertEquals(0.79, c.getDouble(1), 0.0)
            assertEquals("NORMAL", c.getString(2))
            c.moveToNext()
            assertEquals(12L, c.getLong(0))
            assertEquals(true, c.isNull(1))
        }
    }

    @Test
    fun `migrating 1 to 12 through every step keeps the sets`() {
        helper.createDatabase(name, 1).apply {
            insertV1Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(
            name, 12, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
        )

        db.query("SELECT id, weightKg, reps, seconds, calories, distanceM, type, intensity FROM workout_sets ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
            assertEquals(120.5, c.getDouble(1), 0.0)
            assertEquals(12, c.getInt(2))
            assertEquals("NORMAL", c.getString(6))
            assertEquals(true, c.isNull(7))
            c.moveToNext()
            assertEquals(8L, c.getLong(0))
            assertEquals(600, c.getInt(3))
            assertEquals(80, c.getInt(4))
            assertEquals(2500, c.getInt(5))
            assertEquals(true, c.isNull(7))
        }
    }

    // 12 -> 13: sessions without sets are deleted (the 13.json schema is written by CI).

    private fun SupportSQLiteDatabase.insertV12Data() {
        insertV3Data()
        execSQL(
            "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived, libraryId) " +
                "VALUES (1, 'Leg press', 'LEGS', 'WEIGHT_REPS', 5.0, 'seat 4', 90, 0, 'leg_press')",
        )
        execSQL(
            "INSERT INTO machines (id, name, muscleGroup, kind, stepKg, setupNote, restSeconds, archived, libraryId) " +
                "VALUES (2, 'Bike', 'CARDIO', 'CARDIO', 5.0, NULL, NULL, 0, NULL)",
        )
        // 1: closed, with sets, with routine and note. 2: closed, empty. 3: open, empty. 4: extra, closed, with a set.
        execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (1, 1000, 2000, 1, 'good one', 0)")
        execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (2, 3000, 3000, 2, NULL, 0)")
        execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (3, 5000, NULL, NULL, NULL, 0)")
        execSQL("INSERT INTO sessions (id, startedAt, endedAt, routineId, note, isExtra) VALUES (4, 7000, 8000, 2, NULL, 1)")
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, " +
                "loggedAt, type, intensity) VALUES (10, 1, 1, 0, 120.5, 12, NULL, NULL, NULL, 1500, 'WARMUP', NULL)",
        )
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, " +
                "loggedAt, type, intensity) VALUES (11, 1, 1, 1, 130.0, 10, NULL, NULL, NULL, 1600, 'NORMAL', NULL)",
        )
        execSQL(
            "INSERT INTO workout_sets (id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, " +
                "loggedAt, type, intensity) VALUES (12, 4, 2, 0, NULL, NULL, 2700, 520, 18400, 7500, 'NORMAL', 0.79)",
        )
    }

    @Test
    fun `migrating 12 to 13 keeps a closed session with sets with every column`() {
        helper.createDatabase(name, 12).apply {
            insertV12Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 13, true, MIGRATION_12_13)

        db.query("SELECT id, startedAt, endedAt, routineId, note, isExtra FROM sessions WHERE id = 1").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals(1000L, c.getLong(1))
            assertEquals(2000L, c.getLong(2))
            assertEquals(1L, c.getLong(3))
            assertEquals("good one", c.getString(4))
            assertEquals(0, c.getInt(5))
        }
    }

    @Test
    fun `migrating 12 to 13 deletes closed and open sessions without sets`() {
        helper.createDatabase(name, 12).apply {
            insertV12Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 13, true, MIGRATION_12_13)

        db.query("SELECT id FROM sessions ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            c.moveToNext()
            assertEquals(4L, c.getLong(0))
        }
        db.query("SELECT COUNT(*) FROM sessions WHERE endedAt IS NULL").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `migrating 12 to 13 keeps an extra session with sets`() {
        helper.createDatabase(name, 12).apply {
            insertV12Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 13, true, MIGRATION_12_13)

        db.query("SELECT startedAt, endedAt, routineId, isExtra FROM sessions WHERE id = 4").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(7000L, c.getLong(0))
            assertEquals(8000L, c.getLong(1))
            assertEquals(2L, c.getLong(2))
            assertEquals(1, c.getInt(3))
        }
    }

    @Test
    fun `migrating 12 to 13 leaves every set untouched`() {
        helper.createDatabase(name, 12).apply {
            insertV12Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 13, true, MIGRATION_12_13)

        db.query(
            "SELECT id, sessionId, machineId, position, weightKg, reps, seconds, calories, distanceM, loggedAt, type, " +
                "intensity FROM workout_sets ORDER BY id",
        ).use { c ->
            assertEquals(3, c.count)
            c.moveToFirst()
            assertEquals(10L, c.getLong(0))
            assertEquals(1L, c.getLong(1))
            assertEquals(1L, c.getLong(2))
            assertEquals(0, c.getInt(3))
            assertEquals(120.5, c.getDouble(4), 0.0)
            assertEquals(12, c.getInt(5))
            assertEquals(true, c.isNull(6))
            assertEquals(1500L, c.getLong(9))
            assertEquals("WARMUP", c.getString(10))
            assertEquals(true, c.isNull(11))
            c.moveToNext()
            assertEquals(11L, c.getLong(0))
            assertEquals(130.0, c.getDouble(4), 0.0)
            assertEquals(10, c.getInt(5))
            assertEquals("NORMAL", c.getString(10))
            c.moveToNext()
            assertEquals(12L, c.getLong(0))
            assertEquals(4L, c.getLong(1))
            assertEquals(2L, c.getLong(2))
            assertEquals(true, c.isNull(4))
            assertEquals(2700, c.getInt(6))
            assertEquals(520, c.getInt(7))
            assertEquals(18400, c.getInt(8))
            assertEquals(0.79, c.getDouble(11), 1e-9)
        }
    }

    @Test
    fun `after 12 to 13 a new session gets an id after the deleted ones`() {
        helper.createDatabase(name, 12).apply {
            insertV12Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 13, true, MIGRATION_12_13)

        db.execSQL("INSERT INTO sessions (startedAt, endedAt, routineId, note, isExtra) VALUES (9000, NULL, NULL, NULL, 0)")
        db.query("SELECT MAX(id) FROM sessions").use { c ->
            c.moveToFirst()
            assertEquals(5L, c.getLong(0))
        }
    }

    @Test
    fun `migrating 1 to 13 through every step keeps the session with sets`() {
        helper.createDatabase(name, 1).apply {
            insertV1Data()
            close()
        }

        val db = helper.runMigrationsAndValidate(
            name, 13, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
        )

        db.query("SELECT id, endedAt FROM sessions").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals(true, c.isNull(1))
        }
        db.query("SELECT id FROM workout_sets ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
            c.moveToNext()
            assertEquals(8L, c.getLong(0))
        }
    }
}

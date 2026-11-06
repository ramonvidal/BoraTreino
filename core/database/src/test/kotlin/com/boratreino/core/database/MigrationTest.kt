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
 * Migration 1 -> 2 adds workout_sets.type; migration 2 -> 3 adds routine_plans.trainingDays. Needs
 * schemas/.../2.json and 3.json, which Room writes on the first build with those versions (the schemas
 * folder is a test asset).
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
}

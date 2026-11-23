package com.boratreino.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2: sets get a type (NORMAL, WARMUP, DROP, FAILURE); every existing set is NORMAL. */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN type TEXT NOT NULL DEFAULT 'NORMAL'")
    }
}

/** Version 3: the plan gets the weekdays the owner trains (comma string, empty = every day). */
internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routine_plans ADD COLUMN trainingDays TEXT NOT NULL DEFAULT ''")
    }
}

/** Version 4: workouts planned ahead for a date (one routine per date; version 5 lifts that). */
internal val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `planned_workouts` (`date` TEXT NOT NULL, `routineId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`), FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_workouts_routineId` ON `planned_workouts` (`routineId`)")
    }
}

/** Version 5: several workouts may be planned on one date; the key becomes (date, routineId). */
internal val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `planned_workouts_new` (`date` TEXT NOT NULL, `routineId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`, `routineId`), FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("INSERT INTO `planned_workouts_new` (`date`, `routineId`) SELECT `date`, `routineId` FROM `planned_workouts`")
        db.execSQL("DROP TABLE `planned_workouts`")
        db.execSQL("ALTER TABLE `planned_workouts_new` RENAME TO `planned_workouts`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_workouts_routineId` ON `planned_workouts` (`routineId`)")
    }
}

/** Version 6: machines remember the exercise-library entry they came from (null for hand-made ones). */
internal val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE machines ADD COLUMN libraryId TEXT")
    }
}

/** Version 7: planned workouts of the same date have an explicit order. */
internal val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE planned_workouts ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 8: body measurements (tape measurements by date and type, in centimetres). */
internal val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `type` TEXT NOT NULL, `valueCm` REAL NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measurements_type_date` ON `body_measurements` (`type`, `date`)")
    }
}

/** Version 9: sessions can be flagged as extra workouts, which do not advance the rotation. */
internal val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN isExtra INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 10: cardio sets can carry an intensity (the console's number); null for every earlier set. */
internal val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN intensity INTEGER")
    }
}

/** Version 11: cardio sets can carry the bike console's watts, heart rate, watts/kg, TSS and IF; null for every earlier set. */
internal val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN watts INTEGER")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN heartRate INTEGER")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN wattsPerKg REAL")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN tss INTEGER")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN intensityFactor REAL")
    }
}

/**
 * Version 12: cardio is back to distance, time, calories and intensity. `workout_sets` is rebuilt without the
 * bike console's watts, heartRate, wattsPerKg, tss and intensityFactor, and `intensity` becomes REAL (the
 * console shows decimals such as 0.79). Every other column and row is kept; whole intensities carry over as
 * REAL (12 -> 12.0), values above 100 (the new maximum) become NULL. Indices and foreign keys are recreated
 * exactly as Room generates them.
 */
internal val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_sets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, `machineId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`weightKg` REAL, `reps` INTEGER, `seconds` INTEGER, `calories` INTEGER, `distanceM` INTEGER, " +
                "`loggedAt` INTEGER NOT NULL, `type` TEXT NOT NULL DEFAULT 'NORMAL', `intensity` REAL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`machineId`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "INSERT INTO `workout_sets_new` (`id`, `sessionId`, `machineId`, `position`, `weightKg`, `reps`, " +
                "`seconds`, `calories`, `distanceM`, `loggedAt`, `type`, `intensity`) " +
                "SELECT `id`, `sessionId`, `machineId`, `position`, `weightKg`, `reps`, `seconds`, `calories`, " +
                "`distanceM`, `loggedAt`, `type`, " +
                "CASE WHEN `intensity` IS NULL OR `intensity` < 0 OR `intensity` > 100 THEN NULL " +
                "ELSE CAST(`intensity` AS REAL) END FROM `workout_sets`",
        )
        db.execSQL("DROP TABLE `workout_sets`")
        db.execSQL("ALTER TABLE `workout_sets_new` RENAME TO `workout_sets`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_sessionId` ON `workout_sets` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_machineId` ON `workout_sets` (`machineId`)")
    }
}

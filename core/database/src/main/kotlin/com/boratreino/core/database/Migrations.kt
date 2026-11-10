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

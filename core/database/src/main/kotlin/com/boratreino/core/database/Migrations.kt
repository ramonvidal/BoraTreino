package com.boratreino.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2: sets get a type (NORMAL, WARMUP, DROP, FAILURE); every existing set is NORMAL. */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN type TEXT NOT NULL DEFAULT 'NORMAL'")
    }
}

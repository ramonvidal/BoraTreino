package com.boratreino.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** Schemas are exported to core/database/schemas; every version bump needs a migration and a migration test. */
@Database(
    entities = [
        MachineEntity::class,
        RoutinePlanEntity::class,
        RoutineEntity::class,
        RoutineItemEntity::class,
        SessionEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class BoraTreinoDatabase : RoomDatabase() {
    abstract fun machineDao(): MachineDao
    abstract fun sessionDao(): SessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun routineDao(): RoutineDao

    companion object {
        const val NAME = "boratreino.db"
    }
}

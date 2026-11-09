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
        PlannedWorkoutEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
internal abstract class BoraTreinoDatabase : RoomDatabase() {
    abstract fun machineDao(): MachineDao
    abstract fun sessionDao(): SessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun routineDao(): RoutineDao
    abstract fun plannedWorkoutDao(): PlannedWorkoutDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "boratreino.db"
    }
}

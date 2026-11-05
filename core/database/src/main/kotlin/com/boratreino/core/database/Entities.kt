package com.boratreino.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Instants are epoch millis, enums are stored by name, weekdays as a comma string.

@Entity(tableName = "machines")
internal data class MachineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val kind: String,
    val stepKg: Double,
    val setupNote: String?,
    val restSeconds: Int?,
    val archived: Boolean,
)

@Entity(tableName = "routine_plans")
internal data class RoutinePlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleMode: String,
)

@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(
            entity = RoutinePlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
internal data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val name: String,
    val shortName: String,
    val position: Int,
    /** Comma-separated DayOfWeek names; empty when not scheduled by weekday. */
    val weekdays: String,
)

@Entity(
    tableName = "routine_items",
    primaryKeys = ["routineId", "machineId"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("machineId")],
)
internal data class RoutineItemEntity(
    val routineId: Long,
    val machineId: Long,
    val position: Int,
    val targetSets: Int,
    val repsMin: Int,
    val repsMax: Int,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineId"), Index("startedAt")],
)
internal data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    /** Null while the session is open. */
    val endedAt: Long?,
    val routineId: Long?,
    val note: String?,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("machineId")],
)
internal data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val machineId: Long,
    val position: Int,
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val calories: Int?,
    val distanceM: Int?,
    val loggedAt: Long,
    /** SetType name; NORMAL for sets logged before set types existed. */
    @ColumnInfo(defaultValue = "'NORMAL'") val type: String = "NORMAL",
)

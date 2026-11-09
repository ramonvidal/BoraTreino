package com.boratreino.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Whole-table reads, deletes and inserts (ids kept) for backup export and import. */
@Dao
internal interface BackupDao {
    @Query("SELECT * FROM machines ORDER BY id ASC")
    suspend fun machines(): List<MachineEntity>

    @Query("SELECT * FROM routine_plans ORDER BY id ASC")
    suspend fun plans(): List<RoutinePlanEntity>

    @Query("SELECT * FROM routines ORDER BY id ASC")
    suspend fun routines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_items ORDER BY routineId ASC, position ASC")
    suspend fun items(): List<RoutineItemEntity>

    @Query("SELECT * FROM sessions ORDER BY id ASC")
    suspend fun sessions(): List<SessionEntity>

    @Query("SELECT * FROM workout_sets ORDER BY id ASC")
    suspend fun sets(): List<WorkoutSetEntity>

    @Query("SELECT * FROM planned_workouts ORDER BY date ASC, routineId ASC")
    suspend fun plannedWorkouts(): List<PlannedWorkoutEntity>

    @Query("DELETE FROM planned_workouts")
    suspend fun deletePlannedWorkouts()

    @Query("DELETE FROM workout_sets")
    suspend fun deleteSets()

    @Query("DELETE FROM sessions")
    suspend fun deleteSessions()

    @Query("DELETE FROM routine_items")
    suspend fun deleteItems()

    @Query("DELETE FROM routines")
    suspend fun deleteRoutines()

    @Query("DELETE FROM routine_plans")
    suspend fun deletePlans()

    @Query("DELETE FROM machines")
    suspend fun deleteMachines()

    @Insert
    suspend fun insertMachines(machines: List<MachineEntity>)

    @Insert
    suspend fun insertPlans(plans: List<RoutinePlanEntity>)

    @Insert
    suspend fun insertRoutines(routines: List<RoutineEntity>)

    @Insert
    suspend fun insertItems(items: List<RoutineItemEntity>)

    @Insert
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Insert
    suspend fun insertPlannedWorkouts(planned: List<PlannedWorkoutEntity>)
}

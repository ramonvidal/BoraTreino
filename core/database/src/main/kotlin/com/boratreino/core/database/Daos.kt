package com.boratreino.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MachineDao {
    @Query("SELECT * FROM machines WHERE :includeArchived = 1 OR archived = 0 ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(includeArchived: Boolean): Flow<List<MachineEntity>>

    @Query("SELECT COUNT(*) FROM machines WHERE id = :id")
    suspend fun count(id: Long): Int

    @Query("DELETE FROM machines WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM machines")
    suspend fun allNow(): List<MachineEntity>

    @Query("SELECT * FROM machines WHERE id = :id")
    fun observe(id: Long): Flow<MachineEntity?>

    /** Not archived, most recently logged first; machines without sets are left out. */
    @Query(
        "SELECT machines.* FROM machines " +
            "INNER JOIN (SELECT machineId, MAX(loggedAt) AS lastAt, MAX(id) AS lastId FROM workout_sets GROUP BY machineId) AS recent " +
            "ON recent.machineId = machines.id " +
            "WHERE machines.archived = 0 " +
            "ORDER BY recent.lastAt DESC, recent.lastId DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE TRIM(name) = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): MachineEntity?

    @Insert
    suspend fun insert(machine: MachineEntity): Long

    @Update
    suspend fun update(machine: MachineEntity)

    @Query("UPDATE machines SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}

@Dao
internal interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC, id DESC LIMIT 1")
    fun observeOpen(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC, id DESC LIMIT 1")
    suspend fun open(): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE endedAt IS NULL AND id <> :exceptId")
    suspend fun openCountExcept(exceptId: Long): Int

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun setEndedAt(id: Long, endedAt: Long?)

    @Query("SELECT * FROM sessions ORDER BY startedAt ASC, id ASC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE startedAt >= :start AND startedAt < :end ORDER BY startedAt ASC, id ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<SessionEntity>>

    @Query("SELECT startedAt FROM sessions ORDER BY startedAt ASC")
    fun observeStarts(): Flow<List<Long>>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sessions SET routineId = :routineId WHERE id = :id")
    suspend fun setRoutineId(id: Long, routineId: Long?)

    /** Routine of the most recent finished session that had one. */
    @Query("SELECT routineId FROM sessions WHERE routineId IS NOT NULL AND endedAt IS NOT NULL ORDER BY startedAt DESC, id DESC LIMIT 1")
    fun observeLastRoutineId(): Flow<Long?>

    @Query("SELECT routineId FROM sessions WHERE routineId IS NOT NULL AND endedAt IS NOT NULL ORDER BY startedAt DESC, id DESC LIMIT 1")
    suspend fun lastRoutineId(): Long?
}

@Dao
internal interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY machineId ASC, position ASC, id ASC")
    fun observeForSession(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId AND machineId = :machineId ORDER BY position ASC, id ASC")
    fun observeForSessionMachine(sessionId: Long, machineId: Long): Flow<List<WorkoutSetEntity>>

    /** The machine's sets in its most recent session other than [excludeSessionId]. */
    @Query(
        "SELECT * FROM workout_sets WHERE machineId = :machineId AND sessionId = (" +
            "SELECT sessions.id FROM sessions " +
            "INNER JOIN workout_sets ON workout_sets.sessionId = sessions.id " +
            "WHERE workout_sets.machineId = :machineId AND sessions.id <> :excludeSessionId " +
            "ORDER BY sessions.startedAt DESC, sessions.id DESC LIMIT 1) " +
            "ORDER BY position ASC, id ASC",
    )
    fun observeLastTime(machineId: Long, excludeSessionId: Long): Flow<List<WorkoutSetEntity>>

    /** The machine's sets in its last [limit] sessions other than [excludeSessionId], most recent session first. */
    @Query(
        "SELECT workout_sets.* FROM workout_sets " +
            "INNER JOIN sessions ON sessions.id = workout_sets.sessionId " +
            "WHERE workout_sets.machineId = :machineId AND workout_sets.sessionId IN (" +
            "SELECT s.id FROM sessions AS s WHERE s.id <> :excludeSessionId AND EXISTS (" +
            "SELECT 1 FROM workout_sets AS w WHERE w.sessionId = s.id AND w.machineId = :machineId) " +
            "ORDER BY s.startedAt DESC, s.id DESC LIMIT :limit) " +
            "ORDER BY sessions.startedAt DESC, sessions.id DESC, workout_sets.position ASC, workout_sets.id ASC",
    )
    fun observeLastSessions(machineId: Long, excludeSessionId: Long, limit: Int): Flow<List<WorkoutSetEntity>>

    /** Sets of sessions that started in [start, end). */
    @Query(
        "SELECT workout_sets.* FROM workout_sets " +
            "INNER JOIN sessions ON sessions.id = workout_sets.sessionId " +
            "WHERE sessions.startedAt >= :start AND sessions.startedAt < :end",
    )
    fun observeStartedBetween(start: Long, end: Long): Flow<List<WorkoutSetEntity>>

    /** The machine's sets in its most recent session that started before session [sessionId]'s. */
    @Query(
        "SELECT * FROM workout_sets WHERE machineId = :machineId AND sessionId = (" +
            "SELECT s.id FROM sessions AS s, sessions AS cur " +
            "WHERE cur.id = :sessionId AND s.id <> cur.id " +
            "AND (s.startedAt < cur.startedAt OR (s.startedAt = cur.startedAt AND s.id < cur.id)) " +
            "AND EXISTS (SELECT 1 FROM workout_sets AS w WHERE w.sessionId = s.id AND w.machineId = :machineId) " +
            "ORDER BY s.startedAt DESC, s.id DESC LIMIT 1) " +
            "ORDER BY position ASC, id ASC",
    )
    suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSetEntity>

    @Insert
    suspend fun insertAll(sets: List<WorkoutSetEntity>)

    @Query("SELECT * FROM workout_sets ORDER BY loggedAt ASC, id ASC")
    fun observeAll(): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE machineId = :machineId")
    suspend fun forMachine(machineId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE machineId IN (:machineIds) AND loggedAt < :before")
    suspend fun forMachinesBefore(machineIds: List<Long>, before: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY loggedAt ASC, id ASC")
    suspend fun forSession(sessionId: Long): List<WorkoutSetEntity>

    @Query("SELECT COUNT(*) FROM workout_sets WHERE machineId = :machineId")
    suspend fun countAllForMachine(machineId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_sets WHERE sessionId = :sessionId AND machineId = :machineId")
    suspend fun countForMachine(sessionId: Long, machineId: Long): Int

    @Query("SELECT MAX(loggedAt) FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun lastLoggedAt(sessionId: Long): Long?

    @Query("SELECT COUNT(*) FROM workout_sets WHERE sessionId = :sessionId AND type <> 'WARMUP'")
    suspend fun count(sessionId: Long): Int

    @Query("SELECT COUNT(DISTINCT machineId) FROM workout_sets WHERE sessionId = :sessionId AND type <> 'WARMUP'")
    suspend fun machineCount(sessionId: Long): Int

    @Insert
    suspend fun insert(set: WorkoutSetEntity): Long

    @Update
    suspend fun update(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
internal interface RoutineDao {
    @Query("SELECT * FROM routine_plans ORDER BY id ASC LIMIT 1")
    fun observePlan(): Flow<RoutinePlanEntity?>

    @Query("SELECT * FROM routine_plans ORDER BY id ASC LIMIT 1")
    suspend fun plan(): RoutinePlanEntity?

    @Insert
    suspend fun insertPlan(plan: RoutinePlanEntity): Long

    @Query("UPDATE routine_plans SET scheduleMode = :mode WHERE id = :planId")
    suspend fun setScheduleMode(planId: Long, mode: String)

    @Query("UPDATE routine_plans SET trainingDays = :days WHERE id = :planId")
    suspend fun setTrainingDays(planId: Long, days: String)

    @Query("SELECT * FROM routines WHERE planId = :planId ORDER BY position ASC, id ASC")
    fun observeRoutines(planId: Long): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE planId = :planId ORDER BY position ASC, id ASC")
    suspend fun routines(planId: Long): List<RoutineEntity>

    @Query("SELECT * FROM routines ORDER BY position ASC, id ASC")
    fun observeAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY position ASC, id ASC")
    suspend fun allRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeRoutine(id: Long): Flow<RoutineEntity?>

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("UPDATE routines SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("SELECT * FROM routine_items ORDER BY routineId ASC, position ASC")
    fun observeAllItems(): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_items WHERE routineId = :routineId ORDER BY position ASC")
    fun observeItems(routineId: Long): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_items WHERE routineId = :routineId ORDER BY position ASC")
    suspend fun items(routineId: Long): List<RoutineItemEntity>

    /** Names of the routines that contain the machine, by routine position. */
    @Query(
        "SELECT routines.name FROM routines " +
            "INNER JOIN routine_items ON routine_items.routineId = routines.id " +
            "WHERE routine_items.machineId = :machineId ORDER BY routines.position ASC, routines.id ASC",
    )
    suspend fun routineNamesWithMachine(machineId: Long): List<String>

    @Query("DELETE FROM routine_items WHERE machineId = :machineId")
    suspend fun deleteItemsOfMachine(machineId: Long)

    @Query("DELETE FROM routine_items WHERE routineId = :routineId")
    suspend fun deleteItems(routineId: Long)

    @Insert
    suspend fun insertItems(items: List<RoutineItemEntity>)
}

@Dao
internal interface PlannedWorkoutDao {
    @Query("SELECT * FROM planned_workouts ORDER BY date ASC, position ASC, routineId ASC")
    fun observeAll(): Flow<List<PlannedWorkoutEntity>>

    @Query("SELECT routineId FROM planned_workouts WHERE date = :date")
    suspend fun routineIdsOn(date: String): List<Long>

    @Query("SELECT MAX(position) FROM planned_workouts WHERE date = :date")
    suspend fun maxPosition(date: String): Int?

    @Query("UPDATE planned_workouts SET position = :position WHERE date = :date AND routineId = :routineId")
    suspend fun setPosition(date: String, routineId: Long, position: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(planned: PlannedWorkoutEntity)

    @Query("DELETE FROM planned_workouts WHERE date = :date AND routineId = :routineId")
    suspend fun delete(date: String, routineId: Long)
}

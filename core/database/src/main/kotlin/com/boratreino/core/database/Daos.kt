package com.boratreino.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MachineDao {
    @Query("SELECT * FROM machines WHERE :includeArchived = 1 OR archived = 0 ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(includeArchived: Boolean): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE id = :id")
    fun observe(id: Long): Flow<MachineEntity?>

    /** Not archived, most recently logged first; machines without sets are left out. */
    @Query(
        "SELECT machines.* FROM machines " +
            "INNER JOIN (SELECT machineId, MAX(loggedAt) AS lastAt, MAX(id) AS lastId FROM workout_sets GROUP BY machineId) AS last " +
            "ON last.machineId = machines.id " +
            "WHERE machines.archived = 0 " +
            "ORDER BY last.lastAt DESC, last.lastId DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE name = :name COLLATE NOCASE LIMIT 1")
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

    @Query("SELECT COUNT(*) FROM workout_sets WHERE sessionId = :sessionId AND machineId = :machineId")
    suspend fun countForMachine(sessionId: Long, machineId: Long): Int

    @Query("SELECT MAX(loggedAt) FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun lastLoggedAt(sessionId: Long): Long?

    @Query("SELECT COUNT(*) FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    @Query("SELECT COUNT(DISTINCT machineId) FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun machineCount(sessionId: Long): Int

    @Insert
    suspend fun insert(set: WorkoutSetEntity): Long

    @Update
    suspend fun update(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun delete(id: Long)
}

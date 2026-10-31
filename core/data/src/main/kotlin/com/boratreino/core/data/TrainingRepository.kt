package com.boratreino.core.data

import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow

interface TrainingRepository {
    fun observeOpenSession(): Flow<OpenSession?>
    /** Null when the machine doesn't exist. */
    fun observeMachineLog(machineId: Long): Flow<MachineLog?>
    /** Closes the open session if it is stale (SessionRules.isStale at the current time). */
    suspend fun closeStaleSession()
    /**
     * Closes a stale open session first; opens a new one if none is open; writes the set
     * (position = sets of this machine already in the session); returns it. One transaction.
     */
    suspend fun logSet(machineId: Long, draft: SetDraft): WorkoutSet
    suspend fun updateSet(set: WorkoutSet)
    suspend fun deleteSet(set: WorkoutSet)
    /** Undo of delete: re-inserts the set with its id. */
    suspend fun restoreSet(set: WorkoutSet)
    /** Closes the open session now; null if none is open. */
    suspend fun finishSession(): SessionSummary?
    /** Undo of finish: endedAt = null, only if no other session is open. */
    suspend fun reopenSession(sessionId: Long)
}

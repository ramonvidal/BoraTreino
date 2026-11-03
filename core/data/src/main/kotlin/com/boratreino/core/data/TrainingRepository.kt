package com.boratreino.core.data

import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow

interface TrainingRepository {
    fun observeOpenSession(): Flow<OpenSession?>
    /** Routines, the suggested one, the current choice and the active routine with its items. */
    fun observeTodayPlan(): Flow<TodayPlan>
    /**
     * Sets the choice (kept in memory; back to Suggested when a session ends). If a session is
     * open, also sets its routine: none for Free, the suggested routine for Suggested.
     */
    suspend fun chooseRoutine(choice: RoutineChoice)
    /** Null when the machine doesn't exist. Fills target and suggestion from the active routine. */
    fun observeMachineLog(machineId: Long): Flow<MachineLog?>
    /** Closes the open session if it is stale (SessionRules.isStale at the current time). */
    suspend fun closeStaleSession()
    /**
     * Closes a stale open session first; opens a new one if none is open; writes the set
     * (position = sets of this machine already in the session); returns it with the record it
     * set, if any. A newly opened session gets the routine of the current choice. One transaction.
     */
    suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet
    suspend fun updateSet(set: WorkoutSet)
    suspend fun deleteSet(set: WorkoutSet)
    /** Undo of delete: re-inserts the set with its id. */
    suspend fun restoreSet(set: WorkoutSet)
    /** Closes the open session now; null if none is open. The summary includes the record count. */
    suspend fun finishSession(): SessionSummary?
    /** Undo of finish: endedAt = null, only if no other session is open. */
    suspend fun reopenSession(sessionId: Long)
    /** Hides the machine from today's ficha for this training only (kept in memory). */
    suspend fun skipMachineToday(machineId: Long)
    suspend fun unskipMachineToday(machineId: Long)
}

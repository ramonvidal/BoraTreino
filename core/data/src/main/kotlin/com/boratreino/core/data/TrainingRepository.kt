package com.boratreino.core.data

import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow

/** What the one-tap repeat would log: machines and sets (for "same as last time"). */
data class RepeatOffer(val routineId: Long, val machineCount: Int, val setCount: Int)

/** What a repeat wrote, enough for [TrainingRepository.undoRepeat]. */
data class RepeatOutcome(val sessionId: Long, val sessionCreated: Boolean, val setIds: List<Long>, val machineCount: Int)

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
    /** Same as [logSet] for a cardio machine: seconds / calories / distance instead of weight and reps; `record` is null. */
    suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet
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
    /**
     * Non-null when a ficha is active, its routine has an earlier session (the latest one that started
     * before the open session, or before now) and at least one ficha machine (skipped ones excluded) has
     * no sets yet today; counts are for [RepeatMode.SAME].
     */
    fun observeRepeatOffer(): Flow<RepeatOffer?>
    /**
     * Copies the previous session of the active routine into the open session (opened if none, as
     * [logSet] does, closing a stale one first), for ficha machines without sets yet. [RepeatMode.SUGGESTED]
     * uses the progression suggestion where one exists. No records, no rest timer. Null when there is
     * nothing to repeat. One transaction.
     */
    suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome?
    /**
     * Closes a stale session first, opens one if none is open (routine of the current choice, as [logSet]), and writes [sets]
     * as WARMUP sets (the drafts' type is ignored) in one transaction: positions continue per machine, loggedAt = now + index
     * seconds. No records, no rest timer. Undo with [undoRepeat].
     */
    suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome
    /** Deletes the sets of [outcome]; deletes the session too if the repeat created it and it is now empty. */
    suspend fun undoRepeat(outcome: RepeatOutcome)
    /**
     * What "update the routine with this training" would change for a finished (or any) session; null when the
     * session has no routine, the routine is gone, or nothing would change.
     */
    suspend fun routineUpdateOffer(sessionId: Long): RoutineUpdate?
}

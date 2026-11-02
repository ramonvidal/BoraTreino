package com.boratreino.core.data

import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface HistoryRepository {
    /** Sessions whose startedAt falls in [month] (Clock's zone), grouped by date, by startedAt. */
    fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>>
    /** The day's sessions by startedAt. */
    fun observeDay(date: LocalDate): Flow<List<SessionDetail>>
    fun observeSession(sessionId: Long): Flow<SessionDetail?>
    /** Every date with at least one session. */
    fun observeTrainingDates(): Flow<Set<LocalDate>>
    /**
     * Creates a closed session on that day (CalendarRules.addedSessionStart, endedAt = start) and
     * returns its id. Today with a session open: returns the open one's id instead.
     */
    suspend fun addSession(date: LocalDate, routineId: Long?): Long
    suspend fun setSessionRoutine(sessionId: Long, routineId: Long?)
    /**
     * Adds a set to any session: position = the machine's sets in it; loggedAt = the session's last
     * set + 1 minute, or its start. A closed session stays closed (endedAt moves to the new loggedAt if later).
     */
    suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet
    /** The machine's sets in its most recent session that started before [sessionId]'s (for prefill). */
    suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet>
    /** Deletes the session and its sets; returns what Undo needs, or null if it didn't exist. */
    suspend fun deleteSession(sessionId: Long): SessionDetail?
    /** Undo of delete: re-inserts the session and its sets with their ids, in one transaction. */
    suspend fun restoreSession(detail: SessionDetail)
}

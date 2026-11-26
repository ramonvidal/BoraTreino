package com.boratreino.core.data

import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.RepeatMode
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
     * Writes the first set of a new session on [date], session and set in one transaction (a session exists only
     * while it has sets). The session is closed and starts at CalendarRules.addedSessionStart (endedAt = start,
     * the set's loggedAt = start). Today with a session open: the set goes into the open session instead, as
     * [addSet] does, and [routineId] / [isExtra] are ignored. Returns the saved set (its sessionId is the session's).
     */
    suspend fun addSessionWithSet(date: LocalDate, routineId: Long?, isExtra: Boolean, machineId: Long, draft: SetDraft): WorkoutSet
    /** Same as [addSessionWithSet] for a cardio machine (rules of [addCardioSet]). */
    suspend fun addSessionWithCardio(date: LocalDate, routineId: Long?, isExtra: Boolean, machineId: Long, draft: CardioDraft): WorkoutSet
    /**
     * Same as [repeatLastSession] for a session not written yet: the session on [date] (as [addSessionWithSet])
     * and its sets in one transaction. Nothing is written, and the result is empty, when [routineId] is null, the
     * routine has no session that started before the new one, or nothing would be added. Today with a session
     * open: repeats into the open session ([repeatLastSession] on it). The sets' sessionId is the session's.
     */
    suspend fun addSessionRepeating(date: LocalDate, routineId: Long?, isExtra: Boolean, mode: RepeatMode): List<WorkoutSet>
    suspend fun setSessionRoutine(sessionId: Long, routineId: Long?)

    /** Flags the session as an extra workout (it does not advance the rotation) or not. */
    suspend fun setSessionExtra(sessionId: Long, extra: Boolean)
    /**
     * Adds a set to any session: position = the machine's sets in it; loggedAt = the session's last
     * set + 1 minute, or its start. A closed session stays closed (endedAt moves to the new loggedAt if later).
     */
    suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet
    /** Same rules as [addSet], for a cardio machine. */
    suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet
    /** The machine's sets in its most recent session that started before [sessionId]'s (for prefill). */
    suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet>
    /**
     * [lastTimeBefore] for a session not written yet on [date]: the machine's sets, in position order, in its most
     * recent session that started before the time such a session would start (today with a session open: before
     * the open session's start, as that is where the set would go; else CalendarRules.addedSessionStart).
     */
    suspend fun lastTimeBeforeDate(date: LocalDate, machineId: Long): List<WorkoutSet>
    /**
     * Closed sessions (endedAt set) that started within the last [sinceDays] days (Clock's zone),
     * as Health Connect exports; sessions with nothing to export are omitted. Re-emits when
     * sessions, sets, machines or routines change.
     */
    fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>>
    /** Every session id, to find the deleted ones. */
    fun observeSessionIds(): Flow<Set<Long>>
    /** Deletes the session and its sets; returns what Undo needs, or null if it didn't exist. */
    suspend fun deleteSession(sessionId: Long): SessionDetail?
    /** Undo of delete: re-inserts the session and its sets with their ids, in one transaction. */
    suspend fun restoreSession(detail: SessionDetail)
    /**
     * Fills a session that has a routine from the last session of that routine that started before it:
     * routine machines without sets in this session get the previous sets (see RepeatSessionRules).
     * Positions continue; loggedAt = the session's last set + 1 minute (or its start) plus one second
     * per set; a closed session stays closed (endedAt moves later if needed). Returns the new sets,
     * empty if there is no routine, no earlier session or nothing to add.
     */
    suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet>
}

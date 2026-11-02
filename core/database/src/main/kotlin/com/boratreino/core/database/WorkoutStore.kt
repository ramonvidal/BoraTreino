package com.boratreino.core.database

import com.boratreino.core.model.Machine
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Machines, sessions and sets, in model types only. Room stays behind this interface. */
interface WorkoutStore {
    // Machines

    /** By name, case-insensitive. Archived ones only when [includeArchived]. */
    fun observeMachines(includeArchived: Boolean): Flow<List<Machine>>
    fun observeMachine(id: Long): Flow<Machine?>
    /** Not archived, most recently logged first; machines without sets are left out. */
    fun observeRecentMachines(limit: Int): Flow<List<Machine>>
    /** Id 0 inserts, else updates. Returns the id. */
    suspend fun saveMachine(machine: Machine): Long
    /**
     * Inserts [machine] (id ignored) unless one with the same name (case-insensitive) exists; in
     * that case that one is unarchived. Returns the id either way. One transaction.
     */
    suspend fun addMachineOrUnarchive(machine: Machine): Long
    suspend fun setMachineArchived(id: Long, archived: Boolean)

    // Sessions and sets

    /** The open session (no end time), or null. */
    fun observeOpenSession(): Flow<Session?>
    suspend fun openSession(): Session?
    /** All sets of a session, grouped by machine, in position order. */
    fun observeSessionSets(sessionId: Long): Flow<List<WorkoutSet>>
    /** One machine's sets in one session, in position order. */
    fun observeMachineSets(sessionId: Long, machineId: Long): Flow<List<WorkoutSet>>
    /** The machine's sets in its most recent session other than [excludeSessionId] (use -1 for none). */
    fun observeLastTimeSets(machineId: Long, excludeSessionId: Long): Flow<List<WorkoutSet>>

    /** Time of the session's latest set, or null when it has none. */
    suspend fun lastSetAt(sessionId: Long): Instant?
    /** Sets of [machineId] already in the session. */
    suspend fun setCount(sessionId: Long, machineId: Long): Int
    /** Machine and set counts of a session. */
    suspend fun summary(sessionId: Long): SessionSummary

    /** Inserts a new session (id ignored) and returns its id. */
    suspend fun insertSession(session: Session): Long
    suspend fun setSessionEnd(sessionId: Long, endedAt: Instant?)
    /** True when an open session other than [sessionId] exists. */
    suspend fun hasOtherOpenSession(sessionId: Long): Boolean

    /** Inserts the set. A set with id 0 gets a new id; otherwise it keeps its id (undo of delete). Returns the id. */
    suspend fun insertSet(set: WorkoutSet): Long
    suspend fun updateSet(set: WorkoutSet)
    suspend fun deleteSet(setId: Long)

    /** Sets the session's routine (null = none). */
    suspend fun setSessionRoutine(sessionId: Long, routineId: Long?)
    /** Routine of the most recent finished session that had one, or null. */
    fun observeLastRoutineId(): Flow<Long?>
    suspend fun lastRoutineId(): Long?

    // History

    /**
     * The machine's sets in its last [limit] sessions other than [excludeSessionId] (-1 for none),
     * one list per session, most recent session first, each in position order. Sessions without
     * sets of this machine are left out.
     */
    fun observeLastSessionSets(machineId: Long, excludeSessionId: Long, limit: Int): Flow<List<List<WorkoutSet>>>
    /** Every set ever logged on the machine, any session. */
    suspend fun machineSets(machineId: Long): List<WorkoutSet>
    /** Sets on any of [machineIds] logged strictly before [before]. */
    suspend fun machinesSetsBefore(machineIds: List<Long>, before: Instant): List<WorkoutSet>
    /** All sets of a session in logged order. */
    suspend fun sessionSets(sessionId: Long): List<WorkoutSet>

    // Routines

    /** The single plan, or null before first use. */
    fun observePlan(): Flow<RoutinePlan?>
    suspend fun plan(): RoutinePlan?
    /** Returns the plan, creating it (IN_TURN) when there is none. */
    suspend fun ensurePlan(): RoutinePlan
    suspend fun setScheduleMode(planId: Long, mode: ScheduleMode)
    /** The plan's routines by position. */
    fun observeRoutines(planId: Long): Flow<List<Routine>>
    suspend fun routines(planId: Long): List<Routine>
    fun observeRoutine(id: Long): Flow<Routine?>
    /** Items of every routine, by routine then position. */
    fun observeAllRoutineItems(): Flow<List<RoutineItem>>
    /** One routine's items by position. */
    fun observeRoutineItems(routineId: Long): Flow<List<RoutineItem>>
    /** Inserts a new routine (id ignored) and returns its id. */
    suspend fun insertRoutine(routine: Routine): Long
    suspend fun updateRoutine(routine: Routine)
    suspend fun setRoutinePosition(id: Long, position: Int)
    /** Deletes the routine and its items; sessions keep their sets and lose the routine link. */
    suspend fun deleteRoutine(id: Long)
    /** Replaces the routine's items with [items] (stored as given). */
    suspend fun replaceRoutineItems(routineId: Long, items: List<RoutineItem>)

    /** Runs [block] as one database transaction. */
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

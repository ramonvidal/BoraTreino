package com.boratreino.core.data

import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

interface RoutineRepository {
    /** Creates the single plan (IN_TURN) on first use. */
    fun observePlan(): Flow<RoutinePlanWithRoutines>
    fun observeRoutine(id: Long): Flow<RoutineWithItems?>
    /** The last rotation routine: routine of the latest finished session whose routine is in rotation; null when none. */
    fun observeLastRoutineId(): Flow<Long?>
    /** Routine id -> start time of its latest finished session; routines never trained are absent. */
    fun observeLastTrained(): Flow<Map<Long, Instant>>
    suspend fun setScheduleMode(mode: ScheduleMode)
    /** The weekdays the owner trains on the current plan (IN_TURN); empty = every day. */
    suspend fun setTrainingDays(days: Set<DayOfWeek>)
    /**
     * Id 0 inserts at the end (position = count); returns the id. Name and short name are trimmed.
     * The items are replaced, stored in list order. One transaction.
     */
    suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long
    /** Sessions keep their sets; their routine link is cleared. */
    suspend fun deleteRoutine(id: Long)
    /** Swaps positions with the neighbour; no-op at the ends. */
    suspend fun moveRoutine(id: Long, up: Boolean)
    /** Moves the machine one step up or down in the routine; positions persisted in one transaction; no-op at the ends. */
    suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean)
    /** New routine "<name> copy" with the same items, at the next position; returns its id (0 if the source is gone). */
    suspend fun duplicateRoutine(id: Long): Long
    /** New empty routine "Routine <letter>" (first unused A, B, C... short name) at the next position; returns its id. */
    suspend fun createRoutine(): Long
    /** Appends the machine with the default target; no-op when it is already there or the routine is gone. */
    suspend fun addMachine(routineId: Long, machineId: Long)
    /** Removes the machine from the routine; returns the removed item (for Undo) or null. */
    suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem?
    /** Undo of [removeMachine]: puts the item back at its old position. */
    suspend fun restoreItem(item: RoutineItem)

    /**
     * Applies [com.boratreino.core.model.RoutineUpdateRules.plan] for the session to the routine in one
     * transaction; returns what was done, with `previousItems` for [undoRoutineUpdate]. An empty update
     * when the routine or session is gone or nothing changes.
     */
    suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): RoutineUpdate
    /** Undo of [updateRoutineFromSession]: puts [previousItems] back (items of machines since deleted are dropped). */
    suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<RoutineItem>)

    /** Workouts planned ahead, ordered by date, then planned position, then routine. */
    fun observePlanned(): Flow<List<PlannedWorkout>>
    /** Adds the routine to [date]'s plans (several per date); ignored when already planned or the routine does not exist. */
    suspend fun planWorkout(date: LocalDate, routineId: Long)
    /** Sets the order of the routines planned on [date] to [routineIds] (others untouched, unknown ids ignored). */
    suspend fun reorderPlanned(date: LocalDate, routineIds: List<Long>)
    /** Removes [routineId]'s plan on [date]; other routines planned that day stay. */
    suspend fun unplanWorkout(date: LocalDate, routineId: Long)
}

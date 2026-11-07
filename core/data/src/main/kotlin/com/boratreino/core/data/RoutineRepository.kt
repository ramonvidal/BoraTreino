package com.boratreino.core.data

import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate

interface RoutineRepository {
    /** Creates the single plan (IN_TURN) on first use. */
    fun observePlan(): Flow<RoutinePlanWithRoutines>
    fun observeRoutine(id: Long): Flow<RoutineWithItems?>
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

    /** Workouts planned ahead, ordered by date. */
    fun observePlanned(): Flow<List<PlannedWorkout>>
    /** Plans the routine on [date], replacing what was planned there; ignored when the routine does not exist. */
    suspend fun planWorkout(date: LocalDate, routineId: Long)
    suspend fun unplanWorkout(date: LocalDate)
}

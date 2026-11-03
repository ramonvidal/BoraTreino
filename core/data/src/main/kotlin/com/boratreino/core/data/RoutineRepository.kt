package com.boratreino.core.data

import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    /** Creates the single plan (IN_TURN) on first use. */
    fun observePlan(): Flow<RoutinePlanWithRoutines>
    fun observeRoutine(id: Long): Flow<RoutineWithItems?>
    suspend fun setScheduleMode(mode: ScheduleMode)
    /**
     * Id 0 inserts at the end (position = count); returns the id. Name and short name are trimmed.
     * The items are replaced, stored in list order. One transaction.
     */
    suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long
    /** Sessions keep their sets; their routine link is cleared. */
    suspend fun deleteRoutine(id: Long)
    /** Swaps positions with the neighbour; no-op at the ends. */
    suspend fun moveRoutine(id: Long, up: Boolean)
    /** Appends the machine with the default target; no-op when it is already there or the routine is gone. */
    suspend fun addMachine(routineId: Long, machineId: Long)
    /** Removes the machine from the routine; returns the removed item (for Undo) or null. */
    suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem?
    /** Undo of [removeMachine]: puts the item back at its old position. */
    suspend fun restoreItem(item: RoutineItem)
}

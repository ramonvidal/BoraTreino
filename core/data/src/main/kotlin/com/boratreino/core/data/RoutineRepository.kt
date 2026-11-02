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
}

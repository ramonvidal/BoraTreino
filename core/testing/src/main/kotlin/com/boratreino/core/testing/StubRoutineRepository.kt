package com.boratreino.core.testing

import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * A [RoutineRepository] for tests: [plan] (plan 1, in turn, no routines by default), no routine trained yet,
 * nothing planned; writes change nothing. Subclass it and override what a test needs.
 *
 * Defaults: [saveRoutine] returns the routine's id, [duplicateRoutine] and [createRoutine] 0, [removeMachine]
 * null, [updateRoutineFromSession] an empty update.
 */
open class StubRoutineRepository(private val plan: RoutinePlanWithRoutines = EMPTY_PLAN) : RoutineRepository {
    override fun observePlan(): Flow<RoutinePlanWithRoutines> = flowOf(plan)
    override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = flowOf(plan.routines.firstOrNull { it.routine.id == id })
    override fun observeLastRoutineId(): Flow<Long?> = flowOf(null)
    override fun observeLastTrained(): Flow<Map<Long, Instant>> = flowOf(emptyMap())
    override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
    override suspend fun setTrainingDays(days: Set<DayOfWeek>) = Unit
    override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = routine.id
    override suspend fun deleteRoutine(id: Long) = Unit
    override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
    override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit
    override suspend fun duplicateRoutine(id: Long): Long = 0L
    override suspend fun createRoutine(): Long = 0L
    override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
    override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? = null
    override suspend fun restoreItem(item: RoutineItem) = Unit
    override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): RoutineUpdate =
        RoutineUpdate(emptyList(), emptyList())
    override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<RoutineItem>) = Unit
    override fun observePlanned(): Flow<List<PlannedWorkout>> = flowOf(emptyList())
    override suspend fun planWorkout(date: LocalDate, routineId: Long) = Unit
    override suspend fun reorderPlanned(date: LocalDate, routineIds: List<Long>) = Unit
    override suspend fun unplanWorkout(date: LocalDate, routineId: Long) = Unit

    companion object {
        /** Plan 1, in turn, every day, no routines. */
        val EMPTY_PLAN: RoutinePlanWithRoutines = RoutinePlanWithRoutines(RoutinePlan(1L, ScheduleMode.IN_TURN), emptyList())
    }
}

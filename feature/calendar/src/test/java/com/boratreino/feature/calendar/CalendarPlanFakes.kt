package com.boratreino.feature.calendar

import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Routines and planned workouts in memory, kept the way the repository keeps them: several routines per
 * date, unique per (date, routine), ordered by date, then position, then routine id; a new plan goes to the end
 * of its date's order and a planned routine that does not exist is not stored. The calls are recorded for assertions.
 */
internal class PlanRoutines(private val routines: List<Routine> = emptyList()) : RoutineRepository {
    override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): com.boratreino.core.model.RoutineUpdate =
        com.boratreino.core.model.RoutineUpdate(emptyList(), emptyList())
    override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<com.boratreino.core.model.RoutineItem>) {}

    val plannedFlow = MutableStateFlow<List<PlannedWorkout>>(emptyList())
    val planCalls = mutableListOf<Pair<LocalDate, Long>>()
    val unplanCalls = mutableListOf<Pair<LocalDate, Long>>()
    val reorderCalls = mutableListOf<Pair<LocalDate, List<Long>>>()

    private val plan = RoutinePlan(1L, ScheduleMode.IN_TURN)

    override fun observePlan(): Flow<RoutinePlanWithRoutines> =
        flowOf(RoutinePlanWithRoutines(plan, routines.map { RoutineWithItems(it, emptyList()) }))

    override fun observeRoutine(id: Long): Flow<RoutineWithItems?> =
        flowOf(routines.firstOrNull { it.id == id }?.let { RoutineWithItems(it, emptyList()) })

    override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
    override suspend fun setTrainingDays(days: Set<DayOfWeek>) = Unit
    override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = 0L
    override suspend fun deleteRoutine(id: Long) = Unit
    override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
    override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit
    override suspend fun duplicateRoutine(id: Long): Long = 0L
    override suspend fun createRoutine(): Long = 0L
    override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
    override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? = null
    override suspend fun restoreItem(item: RoutineItem) = Unit

    override fun observePlanned(): Flow<List<PlannedWorkout>> = plannedFlow

    override suspend fun planWorkout(date: LocalDate, routineId: Long) {
        planCalls += date to routineId
        if (routines.none { it.id == routineId }) return
        val current = plannedFlow.value
        if (current.any { it.date == date && it.routineId == routineId }) return
        val next = current.filter { it.date == date }.maxOfOrNull { it.position }?.plus(1) ?: 0
        plannedFlow.value = (current + PlannedWorkout(date, routineId, next)).sortedWith(PLANNED_ORDER)
    }

    override suspend fun reorderPlanned(date: LocalDate, routineIds: List<Long>) {
        reorderCalls += date to routineIds
        val current = plannedFlow.value
        val plannedIds = current.filter { it.date == date }.map { it.routineId }.toSet()
        val order = routineIds.distinct().filter { it in plannedIds }
        plannedFlow.value = current
            .map { p -> if (p.date == date && p.routineId in order) p.copy(position = order.indexOf(p.routineId)) else p }
            .sortedWith(PLANNED_ORDER)
    }

    override suspend fun unplanWorkout(date: LocalDate, routineId: Long) {
        unplanCalls += date to routineId
        plannedFlow.value = plannedFlow.value.filterNot { it.date == date && it.routineId == routineId }
    }
}

/** Planned workouts by date, then position, then routine: the order the repository reads them in. */
private val PLANNED_ORDER = compareBy<PlannedWorkout>({ it.date }, { it.position }, { it.routineId })

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
 * Routines and planned workouts in memory, kept the way the repository keeps them: one routine per
 * date, and a planned routine that does not exist is not stored. The calls are recorded for assertions.
 */
internal class PlanRoutines(private val routines: List<Routine> = emptyList()) : RoutineRepository {
    val plannedFlow = MutableStateFlow<List<PlannedWorkout>>(emptyList())
    val planCalls = mutableListOf<Pair<LocalDate, Long>>()
    val unplanCalls = mutableListOf<LocalDate>()

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
        plannedFlow.value = plannedFlow.value.filterNot { it.date == date } + PlannedWorkout(date, routineId)
    }

    override suspend fun unplanWorkout(date: LocalDate) {
        unplanCalls += date
        plannedFlow.value = plannedFlow.value.filterNot { it.date == date }
    }
}

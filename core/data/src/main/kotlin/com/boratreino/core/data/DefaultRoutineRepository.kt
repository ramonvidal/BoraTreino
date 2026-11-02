package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DefaultRoutineRepository @Inject constructor(
    private val store: WorkoutStore,
) : RoutineRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePlan(): Flow<RoutinePlanWithRoutines> = flow {
        store.ensurePlan()
        emitAll(
            store.observePlan().filterNotNull().flatMapLatest { plan ->
                combine(store.observeRoutines(plan.id), store.observeAllRoutineItems()) { routines, items ->
                    val byRoutine = items.groupBy { it.routineId }
                    RoutinePlanWithRoutines(
                        plan = plan,
                        routines = routines.map { r ->
                            RoutineWithItems(r, byRoutine[r.id].orEmpty().sortedBy { it.position })
                        },
                    )
                }
            },
        )
    }

    override fun observeRoutine(id: Long): Flow<RoutineWithItems?> =
        combine(store.observeRoutine(id), store.observeRoutineItems(id)) { routine, items ->
            routine?.let { RoutineWithItems(it, items) }
        }

    override suspend fun setScheduleMode(mode: ScheduleMode) {
        store.inTransaction {
            val plan = store.ensurePlan()
            store.setScheduleMode(plan.id, mode)
        }
    }

    override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long =
        store.inTransaction {
            val plan = store.ensurePlan()
            val clean = routine.copy(name = routine.name.trim(), shortName = routine.shortName.trim())
            val id = if (routine.id == 0L) {
                store.insertRoutine(
                    clean.copy(planId = plan.id, position = store.routines(plan.id).size),
                )
            } else {
                // The stored plan and position win, so a caller can't move a routine out of the plan.
                val stored = store.routines(plan.id).firstOrNull { it.id == routine.id }
                    ?: throw IllegalArgumentException("No routine with id ${routine.id}")
                store.updateRoutine(clean.copy(planId = stored.planId, position = stored.position))
                routine.id
            }
            store.replaceRoutineItems(
                id,
                items.mapIndexed { index, item -> item.copy(routineId = id, position = index) },
            )
            id
        }

    override suspend fun deleteRoutine(id: Long) {
        store.inTransaction {
            val plan = store.plan() ?: return@inTransaction
            store.deleteRoutine(id)
            // Close the gap so positions stay 0..n-1 and "position = count" stays free.
            store.routines(plan.id).forEachIndexed { index, r ->
                if (r.position != index) store.setRoutinePosition(r.id, index)
            }
        }
    }

    override suspend fun moveRoutine(id: Long, up: Boolean) {
        store.inTransaction {
            val plan = store.plan() ?: return@inTransaction
            val ordered = store.routines(plan.id)
            val index = ordered.indexOfFirst { it.id == id }
            val other = if (up) index - 1 else index + 1
            if (index < 0 || other !in ordered.indices) return@inTransaction
            // Renumber so ties or gaps can't make the swap a no-op.
            val moved = ordered.toMutableList().apply { add(other, removeAt(index)) }
            moved.forEachIndexed { i, r -> if (r.position != i) store.setRoutinePosition(r.id, i) }
        }
    }
}

package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.NextRoutineRules
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineEditRules
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.RoutineUpdateRules
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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

    /** The last rotation routine: routine of the latest finished session whose routine is in rotation. */
    override fun observeLastRoutineId(): Flow<Long?> = flow {
        store.ensurePlan()
        emitAll(
            store.observePlan().filterNotNull().flatMapLatest { plan ->
                combine(store.observeSessions(), store.observeRoutines(plan.id)) { sessions, routines ->
                    NextRoutineRules.lastRotationRoutineId(sessions, routines)
                }
            },
        )
    }.distinctUntilChanged()

    override fun observeLastTrained(): Flow<Map<Long, Instant>> = store.observeSessions().map { sessions ->
        val latest = HashMap<Long, Instant>()
        sessions.forEach { s ->
            val id = s.routineId ?: return@forEach
            if (s.endedAt == null) return@forEach
            val seen = latest[id]
            if (seen == null || s.startedAt > seen) latest[id] = s.startedAt
        }
        latest
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

    override suspend fun setTrainingDays(days: Set<DayOfWeek>) {
        store.inTransaction {
            val plan = store.ensurePlan()
            store.setTrainingDays(plan.id, days)
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

    override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) {
        store.inTransaction {
            val items = store.routineItems(routineId)
            val updated = RoutineEditRules.moved(items, machineId, up)
            if (updated != items) store.replaceRoutineItems(routineId, updated)
        }
    }

    override suspend fun duplicateRoutine(id: Long): Long =
        store.inTransaction {
            val plan = store.ensurePlan()
            val source = store.routines(plan.id).firstOrNull { it.id == id } ?: return@inTransaction 0L
            val newId = store.insertRoutine(
                source.copy(
                    id = 0,
                    name = "${source.name} copy",
                    position = store.routines(plan.id).size,
                ),
            )
            val items = store.routineItems(id)
            store.replaceRoutineItems(newId, items.map { it.copy(routineId = newId) })
            newId
        }

    override suspend fun createRoutine(): Long =
        store.inTransaction {
            val plan = store.ensurePlan()
            val existing = store.routines(plan.id)
            val letter = RoutineEditRules.nextLetter(existing.map { it.shortName })
            store.insertRoutine(
                Routine(
                    id = 0,
                    planId = plan.id,
                    name = "Routine $letter",
                    shortName = letter,
                    position = existing.size,
                    weekdays = emptySet(),
                ),
            )
        }

    override suspend fun addMachine(routineId: Long, machineId: Long) {
        store.inTransaction {
            if (store.allRoutines().none { it.id == routineId }) return@inTransaction
            val items = store.routineItems(routineId)
            val updated = RoutineEditRules.withMachine(routineId, items, machineId)
            if (updated != items) store.replaceRoutineItems(routineId, updated)
        }
    }

    override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? =
        store.inTransaction {
            val items = store.routineItems(routineId)
            val removed = items.firstOrNull { it.machineId == machineId } ?: return@inTransaction null
            store.replaceRoutineItems(routineId, RoutineEditRules.withoutMachine(items, machineId))
            removed
        }

    override suspend fun restoreItem(item: RoutineItem) {
        store.inTransaction {
            // The routine or the machine may be gone since; a foreign key would refuse the item.
            if (store.allRoutines().none { it.id == item.routineId }) return@inTransaction
            if (store.machines().none { it.id == item.machineId }) return@inTransaction
            val items = store.routineItems(item.routineId)
            store.replaceRoutineItems(item.routineId, RoutineEditRules.withItemBack(items, item))
        }
    }

    override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): RoutineUpdate =
        store.inTransaction {
            val none = RoutineUpdate(emptyList(), emptyList())
            if (store.allRoutines().none { it.id == routineId }) return@inTransaction none
            if (store.session(sessionId) == null) return@inTransaction none
            val items = store.routineItems(routineId)
            val machineIds = store.machines().map { it.id }.toSet()
            val sets = store.sessionSets(sessionId).filter { it.machineId in machineIds }
            val plan = RoutineUpdateRules.plan(items, sets) ?: return@inTransaction none
            store.replaceRoutineItems(routineId, RoutineUpdateRules.apply(routineId, items, plan))
            plan.copy(previousItems = items)
        }

    override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<RoutineItem>) {
        store.inTransaction {
            if (store.allRoutines().none { it.id == routineId }) return@inTransaction
            val machineIds = store.machines().map { it.id }.toSet()
            val kept = previousItems.filter { it.machineId in machineIds }
                .mapIndexed { i, item -> item.copy(routineId = routineId, position = i) }
            store.replaceRoutineItems(routineId, kept)
        }
    }

    override fun observePlanned(): Flow<List<PlannedWorkout>> = store.observePlannedWorkouts()

    override suspend fun planWorkout(date: LocalDate, routineId: Long) {
        store.inTransaction {
            if (store.allRoutines().none { it.id == routineId }) return@inTransaction
            store.setPlannedWorkout(date, routineId)
        }
    }

    override suspend fun reorderPlanned(date: LocalDate, routineIds: List<Long>) {
        store.reorderPlannedWorkouts(date, routineIds)
    }

    override suspend fun unplanWorkout(date: LocalDate, routineId: Long) {
        store.clearPlannedWorkout(date, routineId)
    }
}

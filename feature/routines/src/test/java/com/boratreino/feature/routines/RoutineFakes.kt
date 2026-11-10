package com.boratreino.feature.routines

import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

/** An in-memory routine store that records every call, in order, in [events]. */
internal class FakeRoutineRepository(
    initial: List<RoutineWithItems> = emptyList(),
    initialPlan: RoutinePlan = RoutinePlan(id = 1L, scheduleMode = ScheduleMode.IN_TURN),
) : RoutineRepository {
    override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): com.boratreino.core.model.RoutineUpdate =
        com.boratreino.core.model.RoutineUpdate(emptyList(), emptyList())
    override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<com.boratreino.core.model.RoutineItem>) {}

    val store = MutableStateFlow(initial)
    val events = mutableListOf<String>()
    val saved = mutableListOf<Pair<Routine, List<RoutineItem>>>()
    val created = mutableListOf<Long>()
    val duplicated = mutableListOf<Long>()
    val deleted = mutableListOf<Long>()
    val moved = mutableListOf<Pair<Long, Boolean>>()
    val trainingDayCalls = mutableListOf<Set<DayOfWeek>>()
    private var nextId = 100L
    private val plan = MutableStateFlow(initialPlan)

    override fun observePlan(): Flow<RoutinePlanWithRoutines> =
        combine(plan, store) { current, routines -> RoutinePlanWithRoutines(current, routines) }

    override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = store.map { list -> list.firstOrNull { it.routine.id == id } }

    override suspend fun setScheduleMode(mode: ScheduleMode) = Unit

    override suspend fun setTrainingDays(days: Set<DayOfWeek>) {
        events += "trainingDays"
        trainingDayCalls += days
        plan.value = plan.value.copy(trainingDays = days)
    }

    override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long {
        events += "save"
        saved += routine to items
        val entry = RoutineWithItems(routine, items)
        store.value = if (store.value.any { it.routine.id == routine.id }) {
            store.value.map { if (it.routine.id == routine.id) entry else it }
        } else {
            store.value + entry
        }
        return routine.id
    }

    override suspend fun deleteRoutine(id: Long) {
        events += "delete"
        deleted += id
        store.value = store.value.filter { it.routine.id != id }
    }

    override suspend fun moveRoutine(id: Long, up: Boolean) {
        events += "move"
        moved += id to up
    }

    override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit

    override suspend fun duplicateRoutine(id: Long): Long {
        events += "duplicate"
        duplicated += id
        return nextId++
    }

    override suspend fun createRoutine(): Long {
        events += "create"
        val id = nextId++
        created += id
        val routine = Routine(id = id, planId = 1L, name = "Routine A", shortName = "A", position = store.value.size, weekdays = emptySet())
        store.value = store.value + RoutineWithItems(routine, emptyList())
        return id
    }

    override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
    override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? = null
    override fun observePlanned(): kotlinx.coroutines.flow.Flow<List<com.boratreino.core.model.PlannedWorkout>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun planWorkout(date: java.time.LocalDate, routineId: Long) = Unit
    override suspend fun unplanWorkout(date: java.time.LocalDate, routineId: Long) = Unit
    override suspend fun reorderPlanned(date: java.time.LocalDate, routineIds: List<Long>) = Unit
    override suspend fun restoreItem(item: RoutineItem) = Unit
}

/** A fixed gym; every other call is not needed by the routines view models. */
internal class FakeMachineRepository(private val all: List<Machine> = emptyList()) : MachineRepository {
    override suspend fun addFromLibrary(exercise: com.boratreino.core.model.LibraryExercise, portuguese: Boolean): Long = error("not used by this test")
    override fun observeLibraryIdsInUse(): kotlinx.coroutines.flow.Flow<Set<String>> = kotlinx.coroutines.flow.flowOf(emptySet())

    override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(all)
    override fun observeMachine(id: Long): Flow<Machine?> = flowOf(all.firstOrNull { it.id == id })
    override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
    override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
    override suspend fun save(machine: Machine): Long = machine.id
    override suspend fun usage(machineId: Long): MachineUsage = error("not used")
    override suspend fun delete(machineId: Long): Boolean = false
    override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
    override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
    override suspend fun setArchived(id: Long, archived: Boolean) = Unit
}

internal fun fakeMachine(id: Long, name: String, group: MuscleGroup = MuscleGroup.LEGS, kind: MachineKind = MachineKind.WEIGHT_REPS) =
    Machine(
        id = id,
        name = name,
        muscleGroup = group,
        kind = kind,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

internal fun fakeRoutine(id: Long, name: String, shortName: String, position: Int = 0) =
    Routine(id = id, planId = 1L, name = name, shortName = shortName, position = position, weekdays = emptySet())

internal fun fakeItem(routineId: Long, machineId: Long, position: Int, sets: Int = 3, min: Int = 8, max: Int = 12) =
    RoutineItem(routineId = routineId, machineId = machineId, position = position, targetSets = sets, repsMin = min, repsMax = max)

/** The exercise library from a list, for the picker and the machine editor. */
internal class FakeExerciseLibrary(private val exercises: List<com.boratreino.core.model.LibraryExercise> = emptyList()) :
    com.boratreino.core.data.ExerciseLibraryRepository {
    override suspend fun all(): List<com.boratreino.core.model.LibraryExercise> = exercises
    override suspend fun byId(id: String): com.boratreino.core.model.LibraryExercise? = exercises.firstOrNull { it.id == id }
}

/** The picker rows of one muscle group's starters that are not added yet. */
internal fun pickerGroupOf(group: MuscleGroup, starters: List<StarterMachine>): PickerGroup =
    PickerGroup(group, starters.map { PickerRow(key = "s:" + it.name, name = it.name, group = group, starter = it) })

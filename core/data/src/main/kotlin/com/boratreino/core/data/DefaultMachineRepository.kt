package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.ExerciseLibraryRules
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.KindConversionRules
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.StarterMachines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DefaultMachineRepository @Inject constructor(
    private val store: WorkoutStore,
) : MachineRepository {
    override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> =
        store.observeMachines(includeArchived)

    override fun observeMachine(id: Long): Flow<Machine?> = store.observeMachine(id)

    override fun observeRecentMachines(limit: Int): Flow<List<Machine>> =
        store.observeRecentMachines(limit)

    override fun observeStartersNotAdded(): Flow<List<StarterMachine>> =
        store.observeMachines(includeArchived = true).map { machines ->
            StarterMachines.all.filter { starter -> machines.none { StarterMachines.matches(starter, it.name) } }
        }

    override suspend fun save(machine: Machine): Long {
        val clean = machine.copy(name = machine.name.trim())
        if (clean.id == 0L) return store.saveMachine(clean)
        return store.inTransaction {
            val stored = store.machines().firstOrNull { it.id == clean.id }
            check(stored == null || stored.kind == clean.kind || store.machineSets(clean.id).isEmpty()) {
                "Machine ${clean.id} has sets; change its kind with saveConvertingSets"
            }
            store.saveMachine(clean)
        }
    }

    override suspend fun kindConversions(machineId: Long): Map<MachineKind, KindConversion> {
        val stored = store.machines().firstOrNull { it.id == machineId } ?: return emptyMap()
        return KindConversionRules.conversions(stored.kind, store.machineSets(machineId))
    }

    override suspend fun saveConvertingSets(machine: Machine, factor: Int?): Boolean {
        val clean = machine.copy(name = machine.name.trim())
        return store.inTransaction {
            val stored = store.machines().firstOrNull { it.id == clean.id }
            if (stored == null) {
                store.saveMachine(clean)
                return@inTransaction true
            }
            val sets = store.machineSets(clean.id)
            val converted = try {
                KindConversionRules.convert(sets, stored.kind, clean.kind, factor)
            } catch (e: IllegalArgumentException) {
                return@inTransaction false
            }
            converted.zip(sets).forEach { (new, old) -> if (new != old) store.updateSet(new) }
            store.saveMachine(clean)
            true
        }
    }

    override suspend fun usage(machineId: Long): MachineUsage =
        MachineUsage(
            setCount = store.machineSetCount(machineId),
            routineNames = store.routineNamesWithMachine(machineId),
        )

    override suspend fun delete(machineId: Long): Boolean = store.deleteMachine(machineId)

    override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long =
        store.inTransaction { addOne(starter, portuguese) }

    override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> =
        store.inTransaction { starters.map { addOne(it, portuguese) } }

    /** Call inside a transaction. */
    private suspend fun addOne(starter: StarterMachine, portuguese: Boolean): Long {
        val existing = store.machines().firstOrNull { StarterMachines.matches(starter, it.name) }
        if (existing != null) {
            if (existing.archived) store.setMachineArchived(existing.id, false)
            return existing.id
        }
        return store.saveMachine(
            Machine(
                id = 0,
                name = starter.displayName(portuguese).trim(),
                muscleGroup = starter.muscleGroup,
                kind = starter.kind,
                stepKg = starter.stepKg,
                setupNote = null,
                restSeconds = null,
                archived = false,
                libraryId = ExerciseLibraryRules.idForStarter(starter.name),
            ),
        )
    }

    override suspend fun addFromLibrary(exercise: LibraryExercise, portuguese: Boolean): Long =
        store.inTransaction {
            val name = exercise.displayName(portuguese).trim()
            val existing = store.machines().firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
            if (existing != null) {
                if (existing.archived) store.setMachineArchived(existing.id, false)
                existing.id
            } else {
                store.saveMachine(
                    Machine(
                        id = 0,
                        name = name,
                        muscleGroup = exercise.muscleGroup,
                        kind = exercise.kind,
                        stepKg = exercise.stepKg,
                        setupNote = null,
                        restSeconds = null,
                        archived = false,
                        libraryId = exercise.id,
                    ),
                )
            }
        }

    override fun observeLibraryIdsInUse(): Flow<Set<String>> =
        store.observeMachines(includeArchived = false).map { machines ->
            machines.mapNotNullTo(HashSet()) { it.libraryId }
        }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        store.setMachineArchived(id, archived)
    }
}

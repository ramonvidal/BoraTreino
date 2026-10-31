package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.Machine
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.StarterMachines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
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
            val names = machines.mapTo(HashSet()) { it.name.trim().lowercase(Locale.ROOT) }
            StarterMachines.all.filter { it.name.trim().lowercase(Locale.ROOT) !in names }
        }

    override suspend fun save(machine: Machine): Long = store.saveMachine(machine)

    override suspend fun addStarter(starter: StarterMachine): Long =
        store.addMachineOrUnarchive(
            Machine(
                id = 0,
                name = starter.name,
                muscleGroup = starter.muscleGroup,
                kind = starter.kind,
                stepKg = starter.stepKg,
                setupNote = null,
                restSeconds = null,
                archived = false,
            ),
        )

    override suspend fun setArchived(id: Long, archived: Boolean) {
        store.setMachineArchived(id, archived)
    }
}

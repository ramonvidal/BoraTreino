package com.boratreino.core.testing

import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.StarterMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A [MachineRepository] for tests: [machines] are the stored machines (archived ones are not filtered out),
 * every write succeeds without changing anything. Subclass it and override what a test needs.
 *
 * Defaults: no recent machines, no starters left, no library ids in use; [save] returns the machine's id,
 * [saveConvertingSets] true, [delete] false, [usage] no sets and no routines, the add functions id 0 / no ids.
 */
open class StubMachineRepository(private val machines: List<Machine> = emptyList()) : MachineRepository {
    override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(machines)
    override fun observeMachine(id: Long): Flow<Machine?> = flowOf(machines.firstOrNull { it.id == id })
    override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
    override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
    override suspend fun save(machine: Machine): Long = machine.id
    override suspend fun kindConversions(machineId: Long): Map<MachineKind, KindConversion> = emptyMap()
    override suspend fun saveConvertingSets(machine: Machine, factor: Int?): Boolean = true
    override suspend fun usage(machineId: Long): MachineUsage = MachineUsage(0, emptyList())
    override suspend fun delete(machineId: Long): Boolean = false
    override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
    override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
    override suspend fun addFromLibrary(exercise: LibraryExercise, portuguese: Boolean): Long = 0L
    override fun observeLibraryIdsInUse(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun setArchived(id: Long, archived: Boolean) = Unit
}

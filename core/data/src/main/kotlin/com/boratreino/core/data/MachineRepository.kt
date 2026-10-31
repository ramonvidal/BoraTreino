package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.StarterMachine
import kotlinx.coroutines.flow.Flow

interface MachineRepository {
    /** By name, case-insensitive. */
    fun observeMachines(includeArchived: Boolean = false): Flow<List<Machine>>
    fun observeMachine(id: Long): Flow<Machine?>
    /** Not archived, by last set descending; machines never used are left out. */
    fun observeRecentMachines(limit: Int = 8): Flow<List<Machine>>
    /** Starters whose name (case-insensitive) matches no stored machine, archived included. */
    fun observeStartersNotAdded(): Flow<List<StarterMachine>>
    /** Id 0 inserts, else updates. Returns the id. */
    suspend fun save(machine: Machine): Long
    /** Inserts the starter; if the name already exists, unarchives that machine and returns its id. */
    suspend fun addStarter(starter: StarterMachine): Long
    suspend fun setArchived(id: Long, archived: Boolean)
}

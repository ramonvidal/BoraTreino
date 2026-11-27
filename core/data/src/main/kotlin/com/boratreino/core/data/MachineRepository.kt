package com.boratreino.core.data

import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.StarterMachine
import kotlinx.coroutines.flow.Flow

interface MachineRepository {
    /** By name, case-insensitive. */
    fun observeMachines(includeArchived: Boolean = false): Flow<List<Machine>>
    fun observeMachine(id: Long): Flow<Machine?>
    /** Not archived, by last set descending; machines never used are left out. */
    fun observeRecentMachines(limit: Int = 8): Flow<List<Machine>>
    /** Starters that no stored machine (archived included) matches by either name. */
    fun observeStartersNotAdded(): Flow<List<StarterMachine>>
    /**
     * Id 0 inserts, else updates. Returns the id. Throws IllegalStateException when the stored kind differs and the
     * machine has sets (use [saveConvertingSets]).
     */
    suspend fun save(machine: Machine): Long
    /** KindConversionRules.conversions(stored kind, all its sets). Empty map for an unknown id. */
    suspend fun kindConversions(machineId: Long): Map<MachineKind, KindConversion>
    /**
     * In one transaction: reads the stored machine and its sets, converts every set with
     * KindConversionRules.convert(sets, stored.kind, machine.kind, factor), updates them, then saves [machine].
     * Returns false and changes nothing when the conversion is NotOffered or the factor is missing or out of range.
     * Unchanged and Free conversions take factor = null. No Undo.
     */
    suspend fun saveConvertingSets(machine: Machine, factor: Int?): Boolean
    /** Logged sets and the names of the routines containing the machine (by routine position). */
    suspend fun usage(machineId: Long): MachineUsage
    /**
     * Removes the machine from every routine and deletes it, in one transaction. Returns false,
     * changing nothing, when it has logged sets or doesn't exist.
     */
    suspend fun delete(machineId: Long): Boolean
    /**
     * Inserts the starter under its Portuguese name when [portuguese], else the English one. If a
     * stored machine already matches either name, that one is unarchived and its id returned.
     */
    suspend fun addStarter(starter: StarterMachine, portuguese: Boolean = false): Long
    /** [addStarter] for each, in one transaction; ids in the same order. */
    suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean = false): List<Long>
    /**
     * Creates the machine for a library exercise (name = its display name in the language, group / kind / step from
     * the exercise, libraryId set). A machine with the same name (case-insensitive, archived too) is unarchived and
     * its id returned instead; no duplicate.
     */
    suspend fun addFromLibrary(exercise: LibraryExercise, portuguese: Boolean = false): Long
    /** Library ids of the non-archived machines that came from the library, for "already added" marks. */
    fun observeLibraryIdsInUse(): Flow<Set<String>>
    suspend fun setArchived(id: Long, archived: Boolean)
}

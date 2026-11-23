package com.boratreino.feature.today

import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Read-only machine list for the machine view model (it reads the routine's other exercises from it). */
internal class FakeMachineLibrary(private val all: List<Machine> = emptyList()) : MachineRepository {
    override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> =
        flowOf(all.filter { includeArchived || !it.archived })

    override fun observeMachine(id: Long): Flow<Machine?> = flowOf(all.firstOrNull { it.id == id })
    override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
    override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
    override suspend fun save(machine: Machine): Long = error("not used by the machine screen")
    override suspend fun usage(machineId: Long): MachineUsage = error("not used by the machine screen")
    override suspend fun delete(machineId: Long): Boolean = error("not used by the machine screen")
    override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = error("not used by the machine screen")
    override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> =
        error("not used by the machine screen")
    override suspend fun addFromLibrary(exercise: LibraryExercise, portuguese: Boolean): Long =
        error("not used by the machine screen")
    override fun observeLibraryIdsInUse(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun setArchived(id: Long, archived: Boolean) = error("not used by the machine screen")
}

/** Today's plan with no routine: what the machine view model sees when nothing is planned. */
internal fun noRoutineTodayPlan(): TodayPlan = TodayPlan(
    mode = ScheduleMode.IN_TURN,
    routines = emptyList(),
    suggested = null,
    choice = RoutineChoice.Free,
    active = null,
    restDay = false,
)

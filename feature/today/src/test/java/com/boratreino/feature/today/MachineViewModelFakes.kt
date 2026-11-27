package com.boratreino.feature.today

import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.testing.StubMachineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Read-only machine list for the machine view model (it reads the routine's other exercises from it). */
internal class FakeMachineLibrary(private val all: List<Machine> = emptyList()) : StubMachineRepository(all) {
    override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> =
        flowOf(all.filter { includeArchived || !it.archived })
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

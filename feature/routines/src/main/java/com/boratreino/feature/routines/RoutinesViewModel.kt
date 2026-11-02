package com.boratreino.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The machines of one muscle group, by name. */
internal data class MachineGroup(val muscleGroup: MuscleGroup, val machines: List<Machine>)

/** The starter machines of one muscle group. */
internal data class StarterGroup(val muscleGroup: MuscleGroup, val starters: List<StarterMachine>)

/** One routine in the tab's list: its badge and name, and how many machines it holds. */
internal data class RoutineRow(val routine: Routine, val machineCount: Int)

/**
 * What the Routines tab shows. [machineGroups] are the active machines; [archived] is the
 * folded list; [starterGroups] are the starters not added yet ("Add from the list").
 */
internal data class RoutinesUiState(
    val routines: List<RoutineRow> = emptyList(),
    val scheduleMode: ScheduleMode = ScheduleMode.IN_TURN,
    val machineGroups: List<MachineGroup> = emptyList(),
    val archived: List<Machine> = emptyList(),
    val starterGroups: List<StarterGroup> = emptyList(),
    val archivedExpanded: Boolean = false,
    val startersExpanded: Boolean = false,
) {
    val isEmpty: Boolean get() = machineGroups.isEmpty() && archived.isEmpty()
}

internal data class RoutinesCallbacks(
    val onNewMachine: () -> Unit = {},
    val onEditMachine: (machineId: Long) -> Unit = {},
    val onAddStarter: (StarterMachine) -> Unit = {},
    val onToggleArchived: () -> Unit = {},
    val onToggleStarters: () -> Unit = {},
    val onNewRoutine: () -> Unit = {},
    val onOpenRoutine: (routineId: Long) -> Unit = {},
    val onMoveRoutine: (routineId: Long, up: Boolean) -> Unit = { _, _ -> },
    val onScheduleModeChange: (ScheduleMode) -> Unit = {},
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val machines: MachineRepository,
    private val routines: RoutineRepository,
) : ViewModel() {
    private val folds = MutableStateFlow(Pair(false, false)) // archived, starters

    internal val uiState: StateFlow<RoutinesUiState> = combine(
        machines.observeMachines(includeArchived = true),
        machines.observeStartersNotAdded(),
        folds,
        routines.observePlan(),
    ) { all, starters, (archivedOpen, startersOpen), plan ->
        RoutinesUiState(
            routines = plan.routines.map { RoutineRow(it.routine, it.items.size) },
            scheduleMode = plan.plan.scheduleMode,
            machineGroups = all.filter { !it.archived }
                .groupBy { it.muscleGroup }
                .toSortedMap()
                .map { (group, list) -> MachineGroup(group, list) },
            archived = all.filter { it.archived },
            starterGroups = starters.groupBy { it.muscleGroup }
                .toSortedMap()
                .map { (group, list) -> StarterGroup(group, list) },
            archivedExpanded = archivedOpen,
            startersExpanded = startersOpen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    internal fun toggleArchived() = folds.update { it.copy(first = !it.first) }

    internal fun toggleStarters() = folds.update { it.copy(second = !it.second) }

    /** Adds a starter machine; it shows up in the list as the database updates. */
    internal fun addStarter(starter: StarterMachine) {
        viewModelScope.launch { machines.addStarter(starter) }
    }

    internal fun moveRoutine(id: Long, up: Boolean) {
        viewModelScope.launch { routines.moveRoutine(id, up) }
    }

    internal fun setScheduleMode(mode: ScheduleMode) {
        viewModelScope.launch { routines.setScheduleMode(mode) }
    }
}

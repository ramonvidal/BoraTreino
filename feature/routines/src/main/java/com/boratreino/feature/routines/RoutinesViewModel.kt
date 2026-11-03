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
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.settings.AppLanguage
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
internal data class RoutineRow(
    val routine: Routine,
    val machineCount: Int,
    /** The machines' names in routine order, for the grey line on the card. */
    val machineNames: List<String> = emptyList(),
)

/** The question shown when the owner starts deleting a machine. */
internal sealed interface DeleteDialog {
    /** No logged sets: deleting is safe. [routineNames] are the routines it will leave. */
    data class Confirm(val machine: Machine, val routineNames: List<String>) : DeleteDialog

    /** Logged sets exist: deleting would erase history, so archiving is offered instead. */
    data class ArchiveInstead(val machine: Machine, val setCount: Int) : DeleteDialog
}

/**
 * What the Routines tab shows, top to bottom: "Your gym" ([machineGroups], [gymCount], the
 * folded [starterGroups] with search and selection, [archived]), then the routines.
 * [selectedStarters] holds English names; [portuguese] picks which name a starter shows.
 */
internal data class RoutinesUiState(
    val routines: List<RoutineRow> = emptyList(),
    val scheduleMode: ScheduleMode = ScheduleMode.IN_TURN,
    val machineGroups: List<MachineGroup> = emptyList(),
    val archived: List<Machine> = emptyList(),
    val starterGroups: List<StarterGroup> = emptyList(),
    val archivedExpanded: Boolean = false,
    val startersExpanded: Boolean = false,
    val startersSearch: String = "",
    val selectedStarters: Set<String> = emptySet(),
    val gymCount: Int = 0,
    val deleteDialog: DeleteDialog? = null,
    /** The routine the owner is about to delete (confirm dialog), if any. */
    val routineDelete: Routine? = null,
    val portuguese: Boolean = false,
    /** False until the database has answered once, so the tab does not flash the wrong page. */
    val loaded: Boolean = true,
) {
    val isEmpty: Boolean get() = machineGroups.isEmpty() && archived.isEmpty()
}

internal data class RoutinesCallbacks(
    val onNewMachine: () -> Unit = {},
    val onEditMachine: (machineId: Long) -> Unit = {},
    val onToggleArchived: () -> Unit = {},
    val onToggleStarters: () -> Unit = {},
    val onNewRoutine: () -> Unit = {},
    val onOpenRoutine: (routineId: Long) -> Unit = {},
    val onMoveRoutine: (routineId: Long, up: Boolean) -> Unit = { _, _ -> },
    val onDuplicateRoutine: (routineId: Long) -> Unit = {},
    val onDeleteRoutine: (routineId: Long) -> Unit = {},
    val onConfirmDeleteRoutine: () -> Unit = {},
    val onDismissDeleteRoutine: () -> Unit = {},
    val onScheduleModeChange: (ScheduleMode) -> Unit = {},
    val onSearchStarters: (String) -> Unit = {},
    val onToggleStarter: (StarterMachine) -> Unit = {},
    val onAddSelectedStarters: () -> Unit = {},
    val onDeleteMachine: (Machine) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onArchiveInstead: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
)

/** What the owner changed on this screen, kept apart from what the database says. */
private data class Local(
    val archivedOpen: Boolean = false,
    /** null = follow the default: open only while the gym has no machines. */
    val startersOpen: Boolean? = null,
    val search: String = "",
    val selected: Set<String> = emptySet(),
    val dialog: DeleteDialog? = null,
    val routineDelete: Routine? = null,
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val machines: MachineRepository,
    private val routines: RoutineRepository,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val portuguese = AppLanguage.current() == AppLanguage.BAIANES

    internal val uiState: StateFlow<RoutinesUiState> = combine(
        machines.observeMachines(includeArchived = true),
        machines.observeStartersNotAdded(),
        local,
        routines.observePlan(),
    ) { all, starters, local, plan ->
        val active = all.filter { !it.archived }
        val names = all.associate { it.id to it.name }
        RoutinesUiState(
            routines = plan.routines.map { r ->
                RoutineRow(
                    routine = r.routine,
                    machineCount = r.items.size,
                    machineNames = r.items.mapNotNull { item -> names[item.machineId] },
                )
            },
            scheduleMode = plan.plan.scheduleMode,
            machineGroups = active.groupBy { it.muscleGroup }
                .toSortedMap()
                .map { (group, list) -> MachineGroup(group, list) },
            archived = all.filter { it.archived },
            starterGroups = StarterMachines.search(local.search, starters, portuguese)
                .groupBy { it.muscleGroup }
                .toSortedMap()
                .map { (group, list) -> StarterGroup(group, list) },
            archivedExpanded = local.archivedOpen,
            startersExpanded = local.startersOpen ?: active.isEmpty(),
            startersSearch = local.search,
            selectedStarters = local.selected,
            gymCount = active.size,
            deleteDialog = local.dialog,
            routineDelete = local.routineDelete,
            portuguese = portuguese,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState(portuguese = portuguese, loaded = false))

    internal fun toggleArchived() = local.update { it.copy(archivedOpen = !it.archivedOpen) }

    internal fun toggleStarters() {
        val open = uiState.value.startersExpanded
        local.update { it.copy(startersOpen = !open) }
    }

    internal fun searchStarters(query: String) = local.update { it.copy(search = query) }

    internal fun toggleStarter(starter: StarterMachine) = local.update {
        it.copy(selected = if (starter.name in it.selected) it.selected - starter.name else it.selected + starter.name)
    }

    /** Adds every ticked starter in one go and clears the selection. */
    internal fun addSelectedStarters() {
        val chosen = local.value.selected
        if (chosen.isEmpty()) return
        val starters = StarterMachines.all.filter { it.name in chosen }
        local.update { it.copy(selected = emptySet()) }
        viewModelScope.launch { machines.addStarters(starters, portuguese) }
    }

    /** Looks at the machine's history first and decides which question to ask. */
    internal fun deleteMachine(machine: Machine) {
        viewModelScope.launch {
            val usage = machines.usage(machine.id)
            val dialog = if (usage.canDelete) {
                DeleteDialog.Confirm(machine, usage.routineNames)
            } else {
                DeleteDialog.ArchiveInstead(machine, usage.setCount)
            }
            local.update { it.copy(dialog = dialog) }
        }
    }

    internal fun confirmDelete() {
        val dialog = local.value.dialog as? DeleteDialog.Confirm ?: return
        local.update { it.copy(dialog = null) }
        viewModelScope.launch { machines.delete(dialog.machine.id) }
    }

    internal fun archiveInstead() {
        val dialog = local.value.dialog as? DeleteDialog.ArchiveInstead ?: return
        local.update { it.copy(dialog = null) }
        viewModelScope.launch { machines.setArchived(dialog.machine.id, true) }
    }

    internal fun dismissDelete() = local.update { it.copy(dialog = null) }

    internal fun moveRoutine(id: Long, up: Boolean) {
        viewModelScope.launch { routines.moveRoutine(id, up) }
    }

    /** Creates a routine right away (never an empty, unsaved one) and hands its id to [onCreated]. */
    internal fun createRoutine(onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(routines.createRoutine()) }
    }

    internal fun duplicateRoutine(id: Long) {
        viewModelScope.launch { routines.duplicateRoutine(id) }
    }

    internal fun requestDeleteRoutine(id: Long) {
        val routine = uiState.value.routines.firstOrNull { it.routine.id == id }?.routine ?: return
        local.update { it.copy(routineDelete = routine) }
    }

    internal fun confirmDeleteRoutine() {
        val routine = local.value.routineDelete ?: return
        local.update { it.copy(routineDelete = null) }
        viewModelScope.launch { routines.deleteRoutine(routine.id) }
    }

    internal fun dismissDeleteRoutine() = local.update { it.copy(routineDelete = null) }

    internal fun setScheduleMode(mode: ScheduleMode) {
        viewModelScope.launch { routines.setScheduleMode(mode) }
    }
}

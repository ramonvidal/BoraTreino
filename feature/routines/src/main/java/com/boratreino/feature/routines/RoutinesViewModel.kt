package com.boratreino.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.ExerciseLibraryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.ExerciseLibraryRules
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LibraryFilter
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.StarterLibraryLinks
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.settings.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The machines of one muscle group, by name. */
internal data class MachineGroup(val muscleGroup: MuscleGroup, val machines: List<Machine>)

/**
 * One row of the exercise picker: a starter or a library exercise. [key] is unique across both
 * ("s:" + starter name, "l:" + library id). [libraryId] is set when there are instructions to show.
 * [added] rows are marked and cannot be selected.
 */
internal data class PickerRow(
    val key: String,
    val name: String,
    val group: MuscleGroup,
    val starter: StarterMachine? = null,
    val libraryId: String? = null,
    val equipment: LibraryEquipment? = null,
    val added: Boolean = false,
)

/** The picker rows of one muscle group. */
internal data class PickerGroup(val muscleGroup: MuscleGroup, val rows: List<PickerRow>)

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
 * folded exercise picker ([pickerGroups] with [filter] and selection, [archived]), then the
 * routines. [selectedStarters] holds English starter names, [selectedLibrary] library ids;
 * [portuguese] picks which name an entry shows. [pickerGroups] is empty while the picker is folded.
 */
internal data class RoutinesUiState(
    val routines: List<RoutineRow> = emptyList(),
    val scheduleMode: ScheduleMode = ScheduleMode.IN_TURN,
    /** Weekdays the owner trains on in "In turn" mode; empty = every day. */
    val trainingDays: Set<DayOfWeek> = emptySet(),
    val machineGroups: List<MachineGroup> = emptyList(),
    val archived: List<Machine> = emptyList(),
    val pickerGroups: List<PickerGroup> = emptyList(),
    /** True when there is anything to pick from at all (so the folded card is shown). */
    val pickerAvailable: Boolean = false,
    val filter: LibraryFilter = LibraryFilter(),
    val detail: LibraryDetail? = null,
    val archivedExpanded: Boolean = false,
    val startersExpanded: Boolean = false,
    val selectedStarters: Set<String> = emptySet(),
    val selectedLibrary: Set<String> = emptySet(),
    val gymCount: Int = 0,
    val deleteDialog: DeleteDialog? = null,
    /** The routine the owner is about to delete (confirm dialog), if any. */
    val routineDelete: Routine? = null,
    val portuguese: Boolean = false,
    /** False until the database has answered once, so the tab does not flash the wrong page. */
    val loaded: Boolean = true,
) {
    val isEmpty: Boolean get() = machineGroups.isEmpty() && archived.isEmpty()
    val pickerCount: Int get() = pickerGroups.sumOf { it.rows.size }
    val selectedCount: Int get() = selectedStarters.size + selectedLibrary.size
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
    val onTrainingDaysChange: (Set<DayOfWeek>) -> Unit = {},
    val onSearchStarters: (String) -> Unit = {},
    val onToggleStarter: (StarterMachine) -> Unit = {},
    val onToggleLibrary: (libraryId: String) -> Unit = {},
    val onAddSelectedStarters: () -> Unit = {},
    val onGroupFilter: (MuscleGroup?) -> Unit = {},
    val onEquipmentFilter: (LibraryEquipment?) -> Unit = {},
    val onLevelFilter: (LibraryLevel?) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onShowDetail: (PickerRow) -> Unit = {},
    val onDismissDetail: () -> Unit = {},
    val onAddDetail: () -> Unit = {},
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
    val filter: LibraryFilter = LibraryFilter(),
    val selected: Set<String> = emptySet(),
    val selectedLibrary: Set<String> = emptySet(),
    val detail: DetailRef? = null,
    val dialog: DeleteDialog? = null,
    val routineDelete: Routine? = null,
)

/** Which exercise the "How to do it" dialog is open for; [starterName] when it came from a starter row. */
private data class DetailRef(val libraryId: String, val starterName: String?)

/** The library with what the picker needs from it, worked out once off the main thread. */
private class PickerLibrary(
    val exercises: List<LibraryExercise>,
    val byId: Map<String, LibraryExercise>,
    /** For each library id, the English names of the starters that are the same exercise. */
    val starterNamesById: Map<String, Set<String>>,
)

/** What the picker needs from the database, as one value so the screen's combine stays under five flows. */
private class PickerSource(val notAdded: List<StarterMachine>, val inUse: Set<String>, val library: PickerLibrary)

private fun normalize(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

private val EmptyLibrary = PickerLibrary(emptyList(), emptyMap(), emptyMap())

/** Starter names by normalised English / Portuguese name; static, so built once. */
private val starterNameIndex: Map<String, List<String>> by lazy {
    buildMap<String, MutableList<String>> {
        StarterMachines.all.forEach { s ->
            getOrPut(normalize(s.name)) { mutableListOf() }.add(s.name)
            getOrPut(normalize(s.namePt)) { mutableListOf() }.add(s.name)
        }
    }
}

private fun buildPickerLibrary(exercises: List<LibraryExercise>): PickerLibrary {
    val reverseLinks = StarterLibraryLinks.ids.entries.groupBy({ it.value }, { it.key })
    val names = HashMap<String, Set<String>>()
    exercises.forEach { e ->
        val set = buildSet {
            reverseLinks[e.id]?.let { addAll(it) }
            starterNameIndex[normalize(e.name)]?.let { addAll(it) }
            starterNameIndex[normalize(e.namePt)]?.let { addAll(it) }
        }
        if (set.isNotEmpty()) names[e.id] = set
    }
    return PickerLibrary(exercises, exercises.associateBy { it.id }, names)
}

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val machines: MachineRepository,
    private val routines: RoutineRepository,
    private val library: ExerciseLibraryRepository,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val portuguese = AppLanguage.current() == AppLanguage.BAIANES

    /** The library, empty until it has loaded so the tab never waits for it. */
    private val pickerSource: Flow<PickerSource> = combine(
        machines.observeStartersNotAdded(),
        machines.observeLibraryIdsInUse(),
        flow { emit(buildPickerLibrary(library.all())) }.onStart { emit(EmptyLibrary) },
    ) { notAdded, inUse, lib -> PickerSource(notAdded, inUse, lib) }

    internal val uiState: StateFlow<RoutinesUiState> = combine(
        machines.observeMachines(includeArchived = true),
        pickerSource,
        local,
        routines.observePlan(),
    ) { all, picker, local, plan ->
        val active = all.filter { !it.archived }
        val names = all.associate { it.id to it.name }
        val expanded = local.startersOpen ?: active.isEmpty()
        val activeNames = if (expanded || local.detail != null) active.mapTo(HashSet()) { normalize(it.name) } else emptySet()
        val notAddedNames = picker.notAdded.mapTo(HashSet()) { it.name }
        fun libraryAdded(e: LibraryExercise): Boolean {
            val twins = picker.library.starterNamesById[e.id].orEmpty()
            return e.id in picker.inUse ||
                normalize(e.name) in activeNames ||
                normalize(e.namePt) in activeNames ||
                (twins.isNotEmpty() && twins.none { it in notAddedNames })
        }
        val detail = local.detail?.let { ref ->
            picker.library.byId[ref.libraryId]?.let { e ->
                LibraryDetail(
                    exercise = e,
                    starterName = ref.starterName,
                    added = if (ref.starterName != null) ref.starterName !in notAddedNames else libraryAdded(e),
                )
            }
        }
        RoutinesUiState(
            routines = plan.routines.map { r ->
                RoutineRow(
                    routine = r.routine,
                    machineCount = r.items.size,
                    machineNames = r.items.mapNotNull { item -> names[item.machineId] },
                )
            },
            scheduleMode = plan.plan.scheduleMode,
            trainingDays = plan.plan.trainingDays,
            machineGroups = active.groupBy { it.muscleGroup }
                .toSortedMap()
                .map { (group, list) -> MachineGroup(group, list) },
            archived = all.filter { it.archived },
            pickerGroups = if (expanded) pickerRows(picker, local.filter, ::libraryAdded) else emptyList(),
            pickerAvailable = picker.notAdded.isNotEmpty() || picker.library.exercises.isNotEmpty(),
            filter = local.filter,
            detail = detail,
            archivedExpanded = local.archivedOpen,
            startersExpanded = expanded,
            selectedStarters = local.selected,
            selectedLibrary = local.selectedLibrary,
            gymCount = active.size,
            deleteDialog = local.dialog,
            routineDelete = local.routineDelete,
            portuguese = portuguese,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState(portuguese = portuguese, loaded = false))

    /**
     * Starters not yet added plus library exercises, both through the same filter, by muscle group
     * then name. A library exercise that is the same as a starter still on offer is left out (the
     * starter row shows it, with its picture).
     */
    private fun pickerRows(picker: PickerSource, filter: LibraryFilter, libraryAdded: (LibraryExercise) -> Boolean): List<PickerGroup> {
        val lib = picker.library
        val notAddedNames = picker.notAdded.mapTo(HashSet()) { it.name }
        val starterRows = StarterMachines.search(filter.query, picker.notAdded, portuguese)
            .filter { filter.group == null || it.muscleGroup == filter.group }
            .mapNotNull { starter ->
                val linked = StarterLibraryLinks.ids[starter.name]?.let { lib.byId[it] }
                val matches = (filter.equipment == null || linked?.equipment == filter.equipment) &&
                    (filter.level == null || linked?.level == filter.level)
                if (!matches) {
                    null
                } else {
                    PickerRow(
                        key = "s:" + starter.name,
                        name = starter.displayName(portuguese),
                        group = starter.muscleGroup,
                        starter = starter,
                        libraryId = linked?.id,
                        equipment = linked?.equipment,
                    )
                }
            }
        val candidates = lib.exercises.filter { e ->
            lib.starterNamesById[e.id].orEmpty().none { it in notAddedNames }
        }
        val libraryRows = ExerciseLibraryRules.filter(candidates, filter, portuguese).map { e ->
            PickerRow(
                key = "l:" + e.id,
                name = e.displayName(portuguese),
                group = e.muscleGroup,
                libraryId = e.id,
                equipment = e.equipment,
                added = libraryAdded(e),
            )
        }
        return (starterRows + libraryRows)
            .sortedWith(compareBy<PickerRow>({ it.group }, { normalize(it.name) }))
            .groupBy { it.group }
            .map { (group, rows) -> PickerGroup(group, rows) }
    }

    internal fun toggleArchived() = local.update { it.copy(archivedOpen = !it.archivedOpen) }

    internal fun toggleStarters() {
        val open = uiState.value.startersExpanded
        local.update { it.copy(startersOpen = !open) }
    }

    internal fun searchStarters(query: String) = local.update { it.copy(filter = it.filter.copy(query = query)) }

    internal fun setGroupFilter(group: MuscleGroup?) = local.update { it.copy(filter = it.filter.copy(group = group)) }

    internal fun setEquipmentFilter(equipment: LibraryEquipment?) =
        local.update { it.copy(filter = it.filter.copy(equipment = equipment)) }

    internal fun setLevelFilter(level: LibraryLevel?) = local.update { it.copy(filter = it.filter.copy(level = level)) }

    /** Clears the three chips and keeps what was typed in the search box. */
    internal fun clearFilters() = local.update {
        it.copy(filter = it.filter.copy(group = null, equipment = null, level = null))
    }

    internal fun toggleLibrary(libraryId: String) = local.update {
        it.copy(
            selectedLibrary = if (libraryId in it.selectedLibrary) it.selectedLibrary - libraryId else it.selectedLibrary + libraryId,
        )
    }

    internal fun showDetail(row: PickerRow) {
        val id = row.libraryId ?: return
        local.update { it.copy(detail = DetailRef(id, row.starter?.name)) }
    }

    internal fun dismissDetail() = local.update { it.copy(detail = null) }

    /** Adds just the exercise in the open "How to do it" dialog, then closes it. */
    internal fun addDetail() {
        val ref = local.value.detail ?: return
        local.update { it.copy(detail = null) }
        viewModelScope.launch {
            val starter = ref.starterName?.let { name -> StarterMachines.all.firstOrNull { it.name == name } }
            if (starter != null) {
                machines.addStarter(starter, portuguese)
            } else {
                library.byId(ref.libraryId)?.let { machines.addFromLibrary(it, portuguese) }
            }
        }
    }

    internal fun toggleStarter(starter: StarterMachine) = local.update {
        it.copy(selected = if (starter.name in it.selected) it.selected - starter.name else it.selected + starter.name)
    }

    /** Adds every ticked starter and library exercise and clears the selection. */
    internal fun addSelectedStarters() {
        val chosen = local.value.selected
        val chosenLibrary = local.value.selectedLibrary
        if (chosen.isEmpty() && chosenLibrary.isEmpty()) return
        val starters = StarterMachines.all.filter { it.name in chosen }
        local.update { it.copy(selected = emptySet(), selectedLibrary = emptySet()) }
        viewModelScope.launch {
            if (starters.isNotEmpty()) machines.addStarters(starters, portuguese)
            chosenLibrary.forEach { id -> library.byId(id)?.let { machines.addFromLibrary(it, portuguese) } }
        }
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

    internal fun setTrainingDays(days: Set<DayOfWeek>) {
        viewModelScope.launch { routines.setTrainingDays(days) }
    }
}

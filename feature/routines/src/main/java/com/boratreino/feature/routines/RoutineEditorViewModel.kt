package com.boratreino.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.LoggingRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A machine in the routine being edited, with its name for display. */
internal data class RoutineEditorItem(
    val machineName: String,
    val item: RoutineItem,
    /** Only picks the thumbnail's placeholder; the picture itself is found by name. */
    val muscleGroup: MuscleGroup = MuscleGroup.LEGS,
    /** Cardio machines have no sets x reps target. */
    val kind: MachineKind = MachineKind.WEIGHT_REPS,
    /** The library exercise the machine came from; gives it a picture when its name is not a starter's. */
    val libraryId: String? = null,
)

/**
 * The routine editor's form. Every change is saved at once, so there is no Save state.
 * [available] are the machines (not archived) that can still be added, by muscle group.
 * [targetMachineId] is the machine whose sets / reps sheet is open. [done] turns true once
 * the screen should leave (back, duplicate, delete, or the routine is gone).
 */
internal data class RoutineEditorUiState(
    val loading: Boolean = true,
    val routineId: Long = 0,
    val name: String = "",
    val shortName: String = "",
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** Unused: the schedule choice is per routine now; kept so old callers compile. */
    val showWeekdays: Boolean = false,
    val items: List<RoutineEditorItem> = emptyList(),
    val available: List<MachineGroup> = emptyList(),
    val pickerOpen: Boolean = false,
    val targetMachineId: Long? = null,
    val confirmDelete: Boolean = false,
    val done: Boolean = false,
)

internal data class RoutineEditorCallbacks(
    val onBack: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onShortNameChange: (String) -> Unit = {},
    val onToggleWeekday: (DayOfWeek) -> Unit = {},
    val onOpenPicker: () -> Unit = {},
    val onClosePicker: () -> Unit = {},
    val onAddMachine: (Machine) -> Unit = {},
    val onMoveItem: (index: Int, up: Boolean) -> Unit = { _, _ -> },
    val onRemoveItem: (index: Int) -> Unit = {},
    val onOpenTarget: (machineId: Long) -> Unit = {},
    val onCloseTarget: () -> Unit = {},
    val onSetsStep: (index: Int, up: Boolean) -> Unit = { _, _ -> },
    val onRepsMinStep: (index: Int, up: Boolean) -> Unit = { _, _ -> },
    val onRepsMaxStep: (index: Int, up: Boolean) -> Unit = { _, _ -> },
    val onDuplicate: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
)

private const val MAX_TARGET_SETS = 10
private const val SAVE_DELAY_MS = 400L

/** What gets stored; two equal drafts mean there is nothing new to save. */
private data class Draft(
    val name: String,
    val shortName: String,
    val weekdays: Set<DayOfWeek>,
    val items: List<RoutineItem>,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val routines: RoutineRepository,
    machines: MachineRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    /** 0 only for a caller that opened the editor without an id: a routine is then created first. */
    private var routineId: Long = savedState.get<Long>(ROUTINE_ID_ARG)?.takeIf { it != NEW_ROUTINE } ?: 0L

    /** The stored routine, kept so a save carries over its plan and position. */
    private var planId = 0L
    private var position = 0

    /** The last name / short name that were not blank: a blank field never reaches the database. */
    private var goodName = ""
    private var goodShortName = ""

    private var lastSaved: Draft? = null
    private var deleted = false
    private val saveLock = Mutex()

    /** Survives this view model: the last save, when the screen is already gone, must finish. */
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val form = MutableStateFlow(RoutineEditorUiState(loading = true, routineId = routineId))
    private val allMachines = machines.observeMachines(includeArchived = true)

    internal val uiState: StateFlow<RoutineEditorUiState> = combine(form, allMachines) { s, all ->
        val used = s.items.map { it.item.machineId }.toSet()
        s.copy(
            available = all.filter { !it.archived && it.id !in used }
                .groupBy { it.muscleGroup }
                .toSortedMap()
                .map { (group, list) -> MachineGroup(group, list) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        viewModelScope.launch {
            if (routineId == 0L) {
                routineId = routines.createRoutine()
                savedState[ROUTINE_ID_ARG] = routineId
            }
            val plan = routines.observePlan().first()
            val known = allMachines.first().associateBy { it.id }
            planId = plan.plan.id
            val found = routines.observeRoutine(routineId).first()
            if (found == null) {
                deleted = true
                form.update { it.copy(loading = false, done = true) }
            } else {
                position = found.routine.position
                goodName = found.routine.name
                goodShortName = found.routine.shortName
                form.update {
                    it.copy(
                        loading = false,
                        routineId = routineId,
                        name = found.routine.name,
                        shortName = found.routine.shortName,
                        weekdays = found.routine.weekdays,
                        items = found.items.map { item ->
                            RoutineEditorItem(
                                machineName = known[item.machineId]?.name.orEmpty(),
                                item = item,
                                muscleGroup = known[item.machineId]?.muscleGroup ?: MuscleGroup.LEGS,
                                kind = known[item.machineId]?.kind ?: MachineKind.WEIGHT_REPS,
                                libraryId = known[item.machineId]?.libraryId,
                            )
                        },
                    )
                }
                lastSaved = draftOf(form.value)
                startAutoSave()
            }
        }
    }

    /** Saves ~400 ms after the last change. */
    private fun startAutoSave() {
        viewModelScope.launch {
            form.filter { !it.loading }
                .map { draftOf(it) }
                .debounce(SAVE_DELAY_MS)
                .collect { save() }
        }
    }

    private fun draftOf(s: RoutineEditorUiState) = Draft(
        name = s.name.trim().ifBlank { goodName },
        shortName = s.shortName.trim().ifBlank { goodShortName },
        weekdays = s.weekdays,
        items = s.items.mapIndexed { i, entry -> entry.item.copy(routineId = routineId, position = i) },
    )

    /** Writes the form if it differs from what is stored. A blank name keeps the last good one. */
    private suspend fun save() {
        saveLock.withLock {
            if (deleted) return
            val s = form.value
            if (s.loading) return
            if (s.name.isNotBlank()) goodName = s.name.trim()
            if (s.shortName.isNotBlank()) goodShortName = s.shortName.trim()
            val draft = draftOf(s)
            if (draft == lastSaved) return
            val routine = Routine(
                id = routineId,
                planId = planId,
                name = draft.name,
                shortName = draft.shortName,
                position = position,
                weekdays = draft.weekdays,
            )
            routines.saveRoutine(routine, draft.items)
            lastSaved = draft
        }
    }

    internal fun setName(value: String) = form.update { it.copy(name = value) }
    internal fun setShortName(value: String) = form.update { it.copy(shortName = value.take(3)) }

    internal fun toggleWeekday(day: DayOfWeek) =
        form.update { it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day) }

    internal fun openPicker() = form.update { it.copy(pickerOpen = true) }
    internal fun closePicker() = form.update { it.copy(pickerOpen = false) }

    internal fun openTarget(machineId: Long) = form.update { it.copy(targetMachineId = machineId) }
    internal fun closeTarget() = form.update { it.copy(targetMachineId = null) }

    /** Adds [machine] at the end with the default target 3 x 8-12. */
    internal fun addMachine(machine: Machine) = form.update {
        val item = RoutineItem(
            routineId = routineId,
            machineId = machine.id,
            position = it.items.size,
            targetSets = 3,
            repsMin = 8,
            repsMax = 12,
        )
        it.copy(items = it.items + RoutineEditorItem(machine.name, item, machine.muscleGroup, machine.kind), pickerOpen = false)
    }

    internal fun moveItem(index: Int, up: Boolean) = form.update {
        val to = if (up) index - 1 else index + 1
        if (index !in it.items.indices || to !in it.items.indices) {
            it
        } else {
            val list = it.items.toMutableList()
            list[index] = list[to].also { list[to] = list[index] }
            it.copy(items = list.renumbered())
        }
    }

    internal fun removeItem(index: Int) =
        form.update { if (index in it.items.indices) it.copy(items = (it.items - it.items[index]).renumbered()) else it }

    internal fun stepSets(index: Int, up: Boolean) = changeItem(index) {
        it.copy(targetSets = (it.targetSets + if (up) 1 else -1).coerceIn(1, MAX_TARGET_SETS))
    }

    /** Minimum reps: 1..repsMax, so min never passes max. */
    internal fun stepRepsMin(index: Int, up: Boolean) = changeItem(index) {
        it.copy(repsMin = (it.repsMin + if (up) 1 else -1).coerceIn(1, it.repsMax))
    }

    /** Maximum reps: repsMin..MAX_REPS, so max never drops below min. */
    internal fun stepRepsMax(index: Int, up: Boolean) = changeItem(index) {
        it.copy(repsMax = (it.repsMax + if (up) 1 else -1).coerceIn(it.repsMin, LoggingRules.MAX_REPS))
    }

    private fun changeItem(index: Int, change: (RoutineItem) -> RoutineItem) = form.update {
        if (index !in it.items.indices) {
            it
        } else {
            val list = it.items.toMutableList()
            list[index] = list[index].copy(item = change(list[index].item))
            it.copy(items = list)
        }
    }

    private fun List<RoutineEditorItem>.renumbered() =
        mapIndexed { i, entry -> entry.copy(item = entry.item.copy(position = i)) }

    /** The back arrow: writes anything pending, then leaves. */
    internal fun back() {
        viewModelScope.launch {
            save()
            form.update { it.copy(done = true) }
        }
    }

    /** Writes anything pending, makes "<name> copy" and leaves; the copy shows in the list. */
    internal fun duplicate() {
        viewModelScope.launch {
            save()
            routines.duplicateRoutine(routineId)
            form.update { it.copy(done = true) }
        }
    }

    internal fun requestDelete() = form.update { it.copy(confirmDelete = true) }
    internal fun dismissDelete() = form.update { it.copy(confirmDelete = false) }

    internal fun confirmDelete() {
        viewModelScope.launch {
            saveLock.withLock { deleted = true }
            routines.deleteRoutine(routineId)
            form.update { it.copy(confirmDelete = false, done = true) }
        }
    }

    override fun onCleared() {
        // System back or swipe: the debounce may still be waiting, so write it now.
        flushScope.launch { save() }
        super.onCleared()
    }
}

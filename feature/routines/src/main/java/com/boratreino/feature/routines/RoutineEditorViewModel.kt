package com.boratreino.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.LoggingRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.NextRoutineRules
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.ScheduleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A machine in the routine being edited, with its name for display. */
internal data class RoutineEditorItem(
    val machineName: String,
    val item: RoutineItem,
    /** Only picks the thumbnail's placeholder; the picture itself is found by name. */
    val muscleGroup: MuscleGroup = MuscleGroup.LEGS,
)

/**
 * The routine editor's form. [routineId] is 0 for a new routine. [available] are the
 * machines (not archived) that can still be added, by muscle group. [done] turns true once
 * a save or delete has been written; the screen then leaves.
 */
internal data class RoutineEditorUiState(
    val loading: Boolean = true,
    val routineId: Long = 0,
    val name: String = "",
    val shortName: String = "",
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** Weekday chips show only in "On weekdays" mode. */
    val showWeekdays: Boolean = false,
    val items: List<RoutineEditorItem> = emptyList(),
    val available: List<MachineGroup> = emptyList(),
    val pickerOpen: Boolean = false,
    val confirmDelete: Boolean = false,
    val nameTouched: Boolean = false,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = routineId == 0L
    val nameError: Boolean get() = nameTouched && name.isBlank()
    val canSave: Boolean get() = !loading && name.isNotBlank() && shortName.isNotBlank()
}

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
    val onSetsStep: (index: Int, up: Boolean) -> Unit = { _, _ -> },
    val onRepsMinStep: (index: Int, up: Boolean) -> Unit = { _, _ -> },
    val onRepsMaxStep: (index: Int, up: Boolean) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
)

private const val MAX_TARGET_SETS = 10

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val routines: RoutineRepository,
    machines: MachineRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val routineId: Long = savedState.get<Long>(ROUTINE_ID_ARG)?.takeIf { it != NEW_ROUTINE } ?: 0L

    /** The stored routine, kept so Save carries over its plan and position. */
    private var planId = 0L
    private var position = 0

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
            val plan = routines.observePlan().first()
            val known = allMachines.first().associateBy { it.id }
            planId = plan.plan.id
            val showWeekdays = plan.plan.scheduleMode == ScheduleMode.WEEKDAYS
            if (routineId == 0L) {
                form.update {
                    it.copy(
                        loading = false,
                        shortName = NextRoutineRules.nextShortName(plan.routines.map { r -> r.routine }),
                        showWeekdays = showWeekdays,
                    )
                }
            } else {
                val found = routines.observeRoutine(routineId).first()
                if (found == null) {
                    form.update { it.copy(loading = false, done = true) }
                } else {
                    position = found.routine.position
                    form.update {
                        it.copy(
                            loading = false,
                            name = found.routine.name,
                            shortName = found.routine.shortName,
                            weekdays = found.routine.weekdays,
                            showWeekdays = showWeekdays,
                            items = found.items.map { item -> RoutineEditorItem(
                                    machineName = known[item.machineId]?.name.orEmpty(),
                                    item = item,
                                    muscleGroup = known[item.machineId]?.muscleGroup ?: MuscleGroup.LEGS,
                                ) },
                        )
                    }
                }
            }
        }
    }

    internal fun setName(value: String) = form.update { it.copy(name = value, nameTouched = true) }
    internal fun setShortName(value: String) = form.update { it.copy(shortName = value.take(3)) }

    internal fun toggleWeekday(day: DayOfWeek) =
        form.update { it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day) }

    internal fun openPicker() = form.update { it.copy(pickerOpen = true) }
    internal fun closePicker() = form.update { it.copy(pickerOpen = false) }

    /** Adds [machine] at the end with the default target 3 x 8-12. */
    internal fun addMachine(machine: Machine) = form.update {
        val item = RoutineItem(
            routineId = it.routineId,
            machineId = machine.id,
            position = it.items.size,
            targetSets = 3,
            repsMin = 8,
            repsMax = 12,
        )
        it.copy(items = it.items + RoutineEditorItem(machine.name, item, machine.muscleGroup), pickerOpen = false)
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

    internal fun save() {
        val s = form.value
        val routine = try {
            Routine(
                id = s.routineId,
                planId = planId,
                name = s.name.trim(),
                shortName = s.shortName.trim(),
                position = position,
                weekdays = s.weekdays,
            )
        } catch (e: IllegalArgumentException) {
            // The model's own rules (blank name) say no: show the inline error instead of crashing.
            form.update { it.copy(nameTouched = true) }
            return
        }
        val items = s.items.mapIndexed { i, entry -> entry.item.copy(routineId = s.routineId, position = i) }
        viewModelScope.launch {
            routines.saveRoutine(routine, items)
            form.update { it.copy(done = true) }
        }
    }

    internal fun requestDelete() = form.update { if (it.isNew) it else it.copy(confirmDelete = true) }
    internal fun dismissDelete() = form.update { it.copy(confirmDelete = false) }

    internal fun confirmDelete() {
        if (routineId == 0L) return
        viewModelScope.launch {
            routines.deleteRoutine(routineId)
            form.update { it.copy(confirmDelete = false, done = true) }
        }
    }
}

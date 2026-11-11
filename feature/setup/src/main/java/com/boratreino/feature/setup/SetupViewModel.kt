package com.boratreino.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RoutineEditRules
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Sex
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.settings.BodySettings
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

internal const val SETUP_STEPS = 3

/** The starter exercises of one muscle group, for step 1. */
internal data class SetupGroup(val muscleGroup: MuscleGroup, val starters: List<StarterMachine>)

/** An exercise added to the gym in step 1, offered as a checklist row in step 2. */
internal data class SetupExercise(val machineId: Long, val name: String, val starter: StarterMachine)

/**
 * What the setup shows. [step] is 0-based. [selected] holds English starter names (step 1);
 * [chosen] are the exercises already added to the gym; [unticked] the ones left out of the routine (step 2).
 * [routineName] is null while the owner has not typed (the screen shows the default name).
 */
internal data class SetupUiState(
    val step: Int = 0,
    val query: String = "",
    val groups: List<SetupGroup> = emptyList(),
    val selected: Set<String> = emptySet(),
    val chosen: List<SetupExercise> = emptyList(),
    val unticked: Set<Long> = emptySet(),
    val routineName: String? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val mascotSex: Sex? = null,
    val portuguese: Boolean = false,
    /** True while a step is being written, so a second tap does nothing. */
    val busy: Boolean = false,
) {
    val isLastStep: Boolean get() = step == SETUP_STEPS - 1
}

internal data class SetupCallbacks(
    val onQuery: (String) -> Unit = {},
    val onToggleStarter: (StarterMachine) -> Unit = {},
    val onToggleExercise: (machineId: Long) -> Unit = {},
    val onRoutineName: (String) -> Unit = {},
    val onToggleDay: (DayOfWeek) -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onSkip: () -> Unit = {},
)

/** What the owner has done so far, kept apart from what the database and settings say. */
private data class Local(
    val step: Int = 0,
    val query: String = "",
    val selected: Set<String> = emptySet(),
    val chosen: List<SetupExercise> = emptyList(),
    val unticked: Set<Long> = emptySet(),
    val routineName: String? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val busy: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val machines: MachineRepository,
    private val routines: RoutineRepository,
    body: BodySettings,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val portuguese = AppLanguage.current() == AppLanguage.BAIANES

    /** The routine created by step 2, so going back and forward again updates it instead of adding another. */
    private var createdRoutineId: Long? = null

    internal val uiState: StateFlow<SetupUiState> = combine(local, body.profile) { local, profile ->
        SetupUiState(
            step = local.step,
            query = local.query,
            groups = StarterMachines.search(local.query, StarterMachines.all)
                .groupBy { it.muscleGroup }
                .toSortedMap()
                .map { (group, list) -> SetupGroup(group, list.sortedBy { it.displayName(portuguese).lowercase() }) },
            selected = local.selected,
            chosen = local.chosen,
            unticked = local.unticked,
            routineName = local.routineName,
            days = local.days,
            mascotSex = profile?.sex,
            portuguese = portuguese,
            busy = local.busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState(portuguese = portuguese))

    internal fun setQuery(query: String) = local.update { it.copy(query = query) }

    internal fun toggleStarter(starter: StarterMachine) = local.update {
        it.copy(selected = if (starter.name in it.selected) it.selected - starter.name else it.selected + starter.name)
    }

    internal fun toggleExercise(machineId: Long) = local.update {
        it.copy(unticked = if (machineId in it.unticked) it.unticked - machineId else it.unticked + machineId)
    }

    internal fun setRoutineName(name: String) = local.update { it.copy(routineName = name) }

    internal fun toggleDay(day: DayOfWeek) = local.update {
        it.copy(days = if (day in it.days) it.days - day else it.days + day)
    }

    internal fun back() = local.update { if (it.busy || it.step == 0) it else it.copy(step = it.step - 1) }

    /**
     * Confirms the current step (writing it) and moves on; on the last step calls [onDone].
     * [defaultRoutineName] is used when the name field is blank. A second call while writing is ignored.
     */
    internal fun next(defaultRoutineName: String, onDone: () -> Unit) {
        val current = local.value
        if (current.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                when (current.step) {
                    0 -> confirmGym(current)
                    1 -> confirmRoutine(current, defaultRoutineName)
                    else -> {
                        routines.setTrainingDays(current.days)
                        onDone()
                        return@launch
                    }
                }
                local.update { it.copy(step = (it.step + 1).coerceAtMost(SETUP_STEPS - 1)) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    /** Adds the ticked starters to the gym (an exercise already there is reused, never duplicated). */
    private suspend fun confirmGym(current: Local) {
        val starters = StarterMachines.all.filter { it.name in current.selected }
        val ids = if (starters.isEmpty()) emptyList() else machines.addStarters(starters, portuguese)
        val added = starters.zip(ids).map { (starter, id) -> SetupExercise(id, starter.displayName(portuguese), starter) }
        local.update { it.copy(chosen = added, unticked = it.unticked.filterTo(HashSet()) { id -> added.any { e -> e.machineId == id } }) }
    }

    /** Creates the routine with the ticked exercises, or updates the one created before. Nothing without exercises. */
    private suspend fun confirmRoutine(current: Local, defaultName: String) {
        val ticked = current.chosen.filter { it.machineId !in current.unticked }
        if (ticked.isEmpty()) return
        val id = createdRoutineId ?: routines.createRoutine().also { createdRoutineId = it }
        val routine = routines.observePlan().first().routines.firstOrNull { it.routine.id == id }?.routine ?: return
        val name = current.routineName?.trim().orEmpty().ifEmpty { defaultName }
        val items = ticked.fold(emptyList<RoutineItem>()) { items, exercise -> RoutineEditRules.withMachine(id, items, exercise.machineId) }
        routines.saveRoutine(routine.copy(name = name), items)
    }
}

package com.boratreino.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal data class MachineGroup(val group: MuscleGroup, val machines: List<Machine>)

internal data class StarterGroup(val group: MuscleGroup, val starters: List<StarterMachine>)

/**
 * [recent] up to 8, then [groups] (the other machines by muscle group), then the [starterGroups]
 * under "More machines", folded unless [moreExpanded].
 */
internal data class PickerUiState(
    val loaded: Boolean = false,
    val recent: List<Machine> = emptyList(),
    val groups: List<MachineGroup> = emptyList(),
    val starterGroups: List<StarterGroup> = emptyList(),
    val moreExpanded: Boolean = false,
)

private const val RECENT_LIMIT = 8

@HiltViewModel
class PickerViewModel @Inject constructor(
    private val machines: MachineRepository,
) : ViewModel() {

    /** The user's choice for the "More machines" fold; null = open only while there are no machines of their own. */
    private val moreOverride = MutableStateFlow<Boolean?>(null)

    internal val state: StateFlow<PickerUiState> = combine(
        machines.observeMachines(),
        machines.observeRecentMachines(RECENT_LIMIT),
        machines.observeStartersNotAdded(),
        moreOverride,
    ) { all, recent, starters, override ->
        val recentIds = recent.map { it.id }.toSet()
        val others = all.filter { it.id !in recentIds }
        PickerUiState(
            loaded = true,
            recent = recent,
            groups = MuscleGroup.entries.mapNotNull { g ->
                others.filter { it.muscleGroup == g }.takeIf { it.isNotEmpty() }?.let { MachineGroup(g, it) }
            },
            starterGroups = MuscleGroup.entries.mapNotNull { g ->
                starters.filter { it.muscleGroup == g }.takeIf { it.isNotEmpty() }?.let { StarterGroup(g, it) }
            },
            moreExpanded = override ?: all.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState())

    internal fun toggleMore() {
        moreOverride.value = !state.value.moreExpanded
    }

    /** Adds the starter machine (or unarchives the one with its name) and returns its id. */
    internal suspend fun addStarter(starter: StarterMachine): Long = machines.addStarter(starter)
}

package com.boratreino.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.RoutineEditRules
import com.boratreino.core.settings.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
    /** True when starter names are shown in Portuguese. */
    val portuguese: Boolean = false,
    /** "Add to the ficha?" question for the machine just chosen; null = none. */
    val fichaPrompt: FichaPrompt? = null,
)

/** Asks whether [machine] should join the active routine [routineName]. */
internal data class FichaPrompt(val machine: Machine, val routineName: String)

private const val RECENT_LIMIT = 8

@HiltViewModel
class PickerViewModel @Inject constructor(
    private val machines: MachineRepository,
    private val training: TrainingRepository,
    private val routines: RoutineRepository,
) : ViewModel() {

    private val portuguese: Boolean get() = AppLanguage.current() == AppLanguage.BAIANES

    /** The question on screen, with the routine it would add to. */
    private val prompt = MutableStateFlow<Pair<FichaPrompt, Long>?>(null)

    /** The user's choice for the "More machines" fold; null = open only while there are no machines of their own. */
    private val moreOverride = MutableStateFlow<Boolean?>(null)

    internal val state: StateFlow<PickerUiState> = combine(
        machines.observeMachines(),
        machines.observeRecentMachines(RECENT_LIMIT),
        machines.observeStartersNotAdded(),
        moreOverride,
        prompt,
    ) { all, recent, starters, override, asking ->
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
            portuguese = portuguese,
            fichaPrompt = asking?.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState())

    internal fun toggleMore() {
        moreOverride.value = !state.value.moreExpanded
    }

    /** Adds the starter machine (or unarchives the one with its name) and returns its id. */
    internal suspend fun addStarter(starter: StarterMachine): Long = machines.addStarter(starter, portuguese)

    /**
     * A machine was chosen. Returns true when the "Add to the ficha?" question is now showing (the screen then
     * waits for the answer), false when the machine can open straight away. A Free training never asks.
     */
    internal suspend fun requestOpen(machineId: Long): Boolean {
        try {
            val plan = training.observeTodayPlan().first()
            val active = plan.active ?: return false
            if (!RoutineEditRules.shouldOfferAdding(active, plan.skipped, machineId)) return false
            val inSession = training.observeOpenSession().first()?.machines.orEmpty().any { it.machine.id == machineId }
            if (inSession) return false
            val machine = machines.observeMachine(machineId).first() ?: return false
            prompt.value = FichaPrompt(machine, active.routine.name) to active.routine.id
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The question is a courtesy; never block opening the machine.
            return false
        }
    }

    /** Answers the question and returns the machine to open (null when there was none). */
    internal suspend fun answerPrompt(addToFicha: Boolean): Long? {
        val (asked, routineId) = prompt.value ?: return null
        prompt.value = null
        if (addToFicha) {
            try {
                routines.addMachine(routineId, asked.machine.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Opening the machine still works; the ficha just stays as it was.
            }
        }
        return asked.machine.id
    }
}

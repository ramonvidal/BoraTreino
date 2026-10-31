package com.boratreino.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The machine editor's form. [machineId] is 0 for a new machine. [done] turns true once a
 * save or archive has been written; the screen then leaves.
 */
internal data class MachineEditorUiState(
    val loading: Boolean = true,
    val machineId: Long = 0,
    val name: String = "",
    val muscleGroup: MuscleGroup = MuscleGroup.LEGS,
    val kind: MachineKind = MachineKind.WEIGHT_REPS,
    val stepKg: Double = 2.5,
    val setupNote: String = "",
    val archived: Boolean = false,
    /** True once the name has been edited (or a save was tried), so the error doesn't show on an untouched new form. */
    val nameTouched: Boolean = false,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = machineId == 0L
    val nameBlank: Boolean get() = name.isBlank()
    val nameError: Boolean get() = nameTouched && nameBlank
    val canSave: Boolean get() = !loading && !nameBlank
}

internal data class MachineEditorCallbacks(
    val onBack: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onMuscleGroupChange: (MuscleGroup) -> Unit = {},
    val onKindChange: (MachineKind) -> Unit = {},
    val onStepChange: (Double) -> Unit = {},
    val onSetupNoteChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onToggleArchived: () -> Unit = {},
)

@HiltViewModel
class MachineEditorViewModel @Inject constructor(
    private val machines: MachineRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val machineId: Long = savedState.get<Long>(MACHINE_ID_ARG)?.takeIf { it != NEW_MACHINE } ?: 0L

    /** The stored machine, kept so Save carries over what the form doesn't edit (rest time). */
    private var original: Machine? = null

    private val _uiState = MutableStateFlow(MachineEditorUiState(loading = machineId != 0L))
    internal val uiState: StateFlow<MachineEditorUiState> = _uiState

    init {
        if (machineId != 0L) {
            viewModelScope.launch {
                val machine = machines.observeMachine(machineId).first()
                if (machine == null) {
                    _uiState.update { it.copy(loading = false, done = true) }
                } else {
                    original = machine
                    _uiState.update {
                        it.copy(
                            loading = false,
                            machineId = machine.id,
                            name = machine.name,
                            muscleGroup = machine.muscleGroup,
                            kind = machine.kind,
                            stepKg = machine.stepKg,
                            setupNote = machine.setupNote.orEmpty(),
                            archived = machine.archived,
                        )
                    }
                }
            }
        }
    }

    internal fun setName(value: String) = _uiState.update { it.copy(name = value, nameTouched = true) }
    internal fun setMuscleGroup(value: MuscleGroup) = _uiState.update { it.copy(muscleGroup = value) }
    internal fun setKind(value: MachineKind) = _uiState.update { it.copy(kind = value) }
    internal fun setStep(value: Double) = _uiState.update { it.copy(stepKg = value) }
    internal fun setSetupNote(value: String) = _uiState.update { it.copy(setupNote = value) }

    internal fun save() {
        val s = _uiState.value
        val machine = try {
            Machine(
                id = s.machineId,
                name = s.name.trim(),
                muscleGroup = s.muscleGroup,
                kind = s.kind,
                stepKg = s.stepKg,
                setupNote = s.setupNote.trim().ifEmpty { null },
                restSeconds = original?.restSeconds,
                archived = s.archived,
            )
        } catch (e: IllegalArgumentException) {
            // The model's own rules (blank name) say no: show the inline error instead of crashing.
            _uiState.update { it.copy(nameTouched = true) }
            return
        }
        viewModelScope.launch {
            machines.save(machine)
            _uiState.update { it.copy(done = true) }
        }
    }

    /** Archives, or unarchives, an existing machine and leaves. */
    internal fun toggleArchived() {
        val s = _uiState.value
        if (s.isNew) return
        viewModelScope.launch {
            machines.setArchived(s.machineId, !s.archived)
            _uiState.update { it.copy(archived = !s.archived, done = true) }
        }
    }
}

package com.boratreino.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.ExerciseLibraryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.model.ConversionFactor
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.KindConversionRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.SetMeasure
import com.boratreino.core.model.measure
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.settings.AppLanguage
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
    /** Rest after each set in seconds; null = the default from Settings. */
    val restSeconds: Int? = null,
    val archived: Boolean = false,
    /** The library exercise the machine came from, kept on Save. */
    val libraryId: String? = null,
    /** Its instructions (English); empty when there are none. */
    val steps: List<String> = emptyList(),
    val portuguese: Boolean = false,
    /** True once the name has been edited (or a save was tried), so the error doesn't show on an untouched new form. */
    val nameTouched: Boolean = false,
    val done: Boolean = false,
    val deleteDialog: DeleteDialog? = null,
    /** What changing the stored kind means for the logged sets, per kind; empty for a new machine. */
    val conversions: Map<MachineKind, KindConversion> = emptyMap(),
    /** The kind-change confirmation, open when Save needs to convert logged sets. */
    val convertDialog: ConvertDialog? = null,
    /** True when saving the conversion was refused; the screen shows a snackbar and clears it. */
    val saveFailed: Boolean = false,
    /** Logged sets on the stored machine (0 for a new one). */
    val setCount: Int = 0,
) {
    val isNew: Boolean get() = machineId == 0L
    val nameBlank: Boolean get() = name.isBlank()
    val nameError: Boolean get() = nameTouched && nameBlank
    val canSave: Boolean get() = !loading && !nameBlank

    /** What picking [kind] would do to the logged sets; [KindConversion.Free] when nothing is stored for it. */
    fun conversionFor(kind: MachineKind): KindConversion = conversions[kind] ?: KindConversion.Free
    fun kindEnabled(kind: MachineKind): Boolean = conversionFor(kind) !is KindConversion.NotOffered

    /** Group and kind are tied: Cardio is the group of the Cardio kind only. */
    fun groupEnabled(group: MuscleGroup): Boolean = when {
        group == MuscleGroup.CARDIO -> kindEnabled(MachineKind.CARDIO)
        kind == MachineKind.CARDIO -> kindEnabled(MachineKind.WEIGHT_REPS)
        else -> true
    }

    /** The logged sets that block some kinds, for the caption; 0 when every kind is offered. */
    val lockedSetCount: Int
        get() = if (conversions.values.any { it is KindConversion.NotOffered }) setCount else 0
}

/**
 * The confirmation before logged sets are converted. [before] and [after] are the machine with the
 * stored and the new kind, for the preview. [factor] is null until the user sets it (metres per rep);
 * [conversion] is [KindConversion.Unchanged] or [KindConversion.NeedsFactor].
 */
internal data class ConvertDialog(
    val before: Machine,
    val after: Machine,
    val conversion: KindConversion,
    val factor: Int? = null,
) {
    val setCount: Int
        get() = when (conversion) {
            is KindConversion.Unchanged -> conversion.setCount
            is KindConversion.NeedsFactor -> conversion.setCount
            else -> 0
        }
    val needsFactor: Boolean get() = conversion is KindConversion.NeedsFactor
    val canConfirm: Boolean get() = !needsFactor || factor != null
    val maxFactor: Int
        get() = when ((conversion as? KindConversion.NeedsFactor)?.factor) {
            ConversionFactor.SECONDS_PER_REP -> KindConversionRules.MAX_SECONDS_PER_REP
            else -> KindConversionRules.MAX_METRES_PER_REP
        }
    /** True when the reps side is the old kind (reps become seconds / metres). */
    val fromReps: Boolean get() = before.kind.measure == SetMeasure.REPS
}

/** The +/-5 grid of the factor stepper; off-grid values snap in the step's direction, clamped 1..[max]. */
internal fun stepConversionFactor(current: Int?, up: Boolean, max: Int): Int? = when {
    current == null -> if (up) FACTOR_STEP else null
    up -> (((current / FACTOR_STEP) + 1) * FACTOR_STEP).coerceAtMost(max)
    current % FACTOR_STEP == 0 -> (current - FACTOR_STEP).coerceAtLeast(1)
    else -> ((current / FACTOR_STEP) * FACTOR_STEP).coerceAtLeast(1)
}

internal const val FACTOR_STEP = 5

internal data class MachineEditorCallbacks(
    val onBack: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onMuscleGroupChange: (MuscleGroup) -> Unit = {},
    val onKindChange: (MachineKind) -> Unit = {},
    val onStepChange: (Double) -> Unit = {},
    val onSetupNoteChange: (String) -> Unit = {},
    val onRestChange: (Int?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onFactorStep: (Boolean) -> Unit = {},
    val onFactorSet: (Int) -> Unit = {},
    val onConfirmConvert: () -> Unit = {},
    val onDismissConvert: () -> Unit = {},
    val onSaveFailedShown: () -> Unit = {},
    val onToggleArchived: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onArchiveInstead: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
)

@HiltViewModel
class MachineEditorViewModel @Inject constructor(
    private val machines: MachineRepository,
    private val library: ExerciseLibraryRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val machineId: Long = savedState.get<Long>(MACHINE_ID_ARG)?.takeIf { it != NEW_MACHINE } ?: 0L

    /** The stored machine, kept so Save carries over what the form doesn't edit (rest time). */
    private var original: Machine? = null

    private val _uiState = MutableStateFlow(
        MachineEditorUiState(loading = machineId != 0L, portuguese = AppLanguage.current() == AppLanguage.BAIANES),
    )
    internal val uiState: StateFlow<MachineEditorUiState> = _uiState

    init {
        if (machineId != 0L) {
            viewModelScope.launch {
                val machine = machines.observeMachine(machineId).first()
                if (machine == null) {
                    _uiState.update { it.copy(loading = false, done = true) }
                } else {
                    original = machine
                    val conversions = machines.kindConversions(machine.id)
                    val setCount = machines.usage(machine.id).setCount
                    _uiState.update {
                        it.copy(
                            loading = false,
                            machineId = machine.id,
                            name = machine.name,
                            muscleGroup = machine.muscleGroup,
                            kind = machine.kind,
                            stepKg = machine.stepKg,
                            setupNote = machine.setupNote.orEmpty(),
                            restSeconds = machine.restSeconds,
                            archived = machine.archived,
                            libraryId = machine.libraryId,
                            conversions = conversions,
                            setCount = setCount,
                        )
                    }
                    // The instructions load after the form is usable; a missing library just shows no card.
                    val exercise = machine.libraryId?.let { library.byId(it) }
                    if (exercise != null) _uiState.update { it.copy(steps = exercise.steps) }
                }
            }
        }
    }

    internal fun setName(value: String) = _uiState.update { it.copy(name = value, nameTouched = true) }

    /** Cardio is both a group and a kind: picking one picks the other, and leaving it falls back to weights. */
    internal fun setMuscleGroup(value: MuscleGroup) = _uiState.update {
        if (!it.groupEnabled(value)) return@update it
        val kind = when {
            value == MuscleGroup.CARDIO -> MachineKind.CARDIO
            it.kind == MachineKind.CARDIO -> MachineKind.WEIGHT_REPS
            else -> it.kind
        }
        it.copy(muscleGroup = value, kind = kind)
    }

    internal fun setKind(value: MachineKind) = _uiState.update {
        if (!it.kindEnabled(value)) return@update it
        val group = when {
            value == MachineKind.CARDIO -> MuscleGroup.CARDIO
            it.muscleGroup == MuscleGroup.CARDIO -> MuscleGroup.LEGS
            else -> it.muscleGroup
        }
        it.copy(kind = value, muscleGroup = group)
    }
    internal fun setStep(value: Double) = _uiState.update { it.copy(stepKg = value) }
    internal fun setSetupNote(value: String) = _uiState.update { it.copy(setupNote = value) }
    internal fun setRest(value: Int?) = _uiState.update { it.copy(restSeconds = value) }

    /** The machine as the form describes it, or null (and the name error) when the model refuses it. */
    private fun buildMachine(): Machine? {
        val s = _uiState.value
        return try {
            Machine(
                id = s.machineId,
                name = s.name.trim(),
                muscleGroup = s.muscleGroup,
                kind = s.kind,
                stepKg = s.stepKg,
                setupNote = s.setupNote.trim().ifEmpty { null },
                restSeconds = s.restSeconds,
                archived = s.archived,
                libraryId = s.libraryId,
            )
        } catch (e: IllegalArgumentException) {
            // The model's own rules (blank name) say no: show the inline error instead of crashing.
            _uiState.update { it.copy(nameTouched = true) }
            null
        }
    }

    /**
     * Saves. When the kind changed and the machine has sets, asks first (the dialog) instead of
     * calling save(), which would refuse.
     */
    internal fun save() {
        val s = _uiState.value
        val machine = buildMachine() ?: return
        val stored = original
        if (stored != null && stored.kind != machine.kind) {
            when (val conversion = s.conversionFor(machine.kind)) {
                is KindConversion.Unchanged, is KindConversion.NeedsFactor -> {
                    val factor = (conversion as? KindConversion.NeedsFactor)?.default
                    _uiState.update {
                        it.copy(convertDialog = ConvertDialog(stored, stored.copy(kind = machine.kind), conversion, factor))
                    }
                    return
                }
                is KindConversion.NotOffered -> {
                    _uiState.update { it.copy(saveFailed = true) }
                    return
                }
                is KindConversion.Free -> Unit
            }
        }
        viewModelScope.launch {
            machines.save(machine)
            _uiState.update { it.copy(done = true) }
        }
    }

    internal fun stepFactor(up: Boolean) = _uiState.update {
        val dialog = it.convertDialog ?: return@update it
        it.copy(convertDialog = dialog.copy(factor = stepConversionFactor(dialog.factor, up, dialog.maxFactor)))
    }

    /** A typed factor; 0 clears it, anything above the maximum is clamped. */
    internal fun setFactor(value: Int) = _uiState.update {
        val dialog = it.convertDialog ?: return@update it
        it.copy(convertDialog = dialog.copy(factor = value.takeIf { v -> v > 0 }?.coerceAtMost(dialog.maxFactor)))
    }

    internal fun dismissConvert() = _uiState.update { it.copy(convertDialog = null) }

    internal fun saveFailedShown() = _uiState.update { it.copy(saveFailed = false) }

    /** Converts the logged sets and saves the machine in one go (no Undo). */
    internal fun confirmConvert() {
        val dialog = _uiState.value.convertDialog ?: return
        if (!dialog.canConfirm) return
        val machine = buildMachine() ?: return
        _uiState.update { it.copy(convertDialog = null) }
        viewModelScope.launch {
            val ok = machines.saveConvertingSets(machine, dialog.factor.takeIf { dialog.needsFactor })
            _uiState.update { if (ok) it.copy(done = true) else it.copy(saveFailed = true) }
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

    /** Looks at the machine's history first and decides which question to ask. */
    internal fun requestDelete() {
        val s = _uiState.value
        if (s.isNew) return
        val machine = original ?: return
        viewModelScope.launch {
            val usage = machines.usage(machine.id)
            val dialog = if (usage.canDelete) {
                DeleteDialog.Confirm(machine, usage.routineNames)
            } else {
                DeleteDialog.ArchiveInstead(machine, usage.setCount)
            }
            _uiState.update { it.copy(deleteDialog = dialog) }
        }
    }

    internal fun confirmDelete() {
        val dialog = _uiState.value.deleteDialog as? DeleteDialog.Confirm ?: return
        _uiState.update { it.copy(deleteDialog = null) }
        viewModelScope.launch {
            machines.delete(dialog.machine.id)
            _uiState.update { it.copy(done = true) }
        }
    }

    internal fun archiveInstead() {
        val dialog = _uiState.value.deleteDialog as? DeleteDialog.ArchiveInstead ?: return
        _uiState.update { it.copy(deleteDialog = null) }
        viewModelScope.launch {
            machines.setArchived(dialog.machine.id, true)
            _uiState.update { it.copy(archived = true, done = true) }
        }
    }

    internal fun dismissDelete() = _uiState.update { it.copy(deleteDialog = null) }
}

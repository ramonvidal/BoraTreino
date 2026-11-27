package com.boratreino.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.ExerciseImages
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.NotOfferedReason
import com.boratreino.core.model.hasLoad
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraButtonStyle
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.label
import com.boratreino.core.ui.R as CoreUiR

private val KindChoices = listOf(
    MachineKind.WEIGHT_REPS,
    MachineKind.BODYWEIGHT_REPS,
    MachineKind.WEIGHT_TIME,
    MachineKind.WEIGHT_DISTANCE,
    MachineKind.TIME,
    MachineKind.CARDIO,
)

private val StepChoices = listOf(1.0, 2.5, 5.0)

@Composable
internal fun MachineEditorScreen(
    onDone: () -> Unit,
    viewModel: MachineEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    MachineEditorContent(
        state = state,
        callbacks = MachineEditorCallbacks(
            onBack = onDone,
            onNameChange = viewModel::setName,
            onMuscleGroupChange = viewModel::setMuscleGroup,
            onKindChange = viewModel::setKind,
            onStepChange = viewModel::setStep,
            onSetupNoteChange = viewModel::setSetupNote,
            onRestChange = viewModel::setRest,
            onSave = viewModel::save,
            onFactorStep = viewModel::stepFactor,
            onFactorSet = viewModel::setFactor,
            onConfirmConvert = viewModel::confirmConvert,
            onDismissConvert = viewModel::dismissConvert,
            onSaveFailedShown = viewModel::saveFailedShown,
            onToggleArchived = viewModel::toggleArchived,
            onDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onArchiveInstead = viewModel::archiveInstead,
            onDismissDelete = viewModel::dismissDelete,
        ),
    )
}

/** The machine editor, drawn from its state alone, so screenshot tests can render it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MachineEditorContent(
    state: MachineEditorUiState,
    callbacks: MachineEditorCallbacks,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.editor_convert_failed)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(failedText)
            callbacks.onSaveFailedShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_back),
                            contentDescription = stringResource(CoreUiR.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = callbacks.onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.editor_name_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Section(stringResource(R.string.editor_muscle_group)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroup.entries.forEach { group ->
                        BoraChip(
                            label = group.label(),
                            selected = state.muscleGroup == group,
                            enabled = state.groupEnabled(group),
                            onClick = { callbacks.onMuscleGroupChange(group) },
                        )
                    }
                }
                if (MuscleGroup.entries.any { !state.groupEnabled(it) }) {
                    Text(
                        stringResource(R.string.editor_group_locked_cardio),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Section(stringResource(R.string.editor_kind)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChoices.forEach { kind ->
                        BoraChip(
                            label = kind.editorLabel(),
                            selected = state.kind == kind,
                            onClick = { callbacks.onKindChange(kind) },
                            enabled = state.kindEnabled(kind),
                        )
                    }
                }
                KindLockedCaption(state)
            }

            // Weight step: only kinds with a load. Rest timer: every kind but cardio.
            if (state.kind.hasLoad) {
                Section(stringResource(R.string.editor_step)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // A machine saved with another step keeps it as a fourth choice.
                        val steps = if (state.stepKg in StepChoices) StepChoices else (StepChoices + state.stepKg).sorted()
                        steps.forEach { step ->
                            BoraChip(
                                label = stringResource(R.string.editor_step_value, formatKg(step)),
                                selected = state.stepKg == step,
                                onClick = { callbacks.onStepChange(step) },
                            )
                        }
                    }
                }
            }

            if (state.kind != MachineKind.CARDIO) {
                Section(stringResource(R.string.editor_rest)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoraChip(
                            label = stringResource(R.string.editor_rest_default),
                            selected = state.restSeconds == null,
                            onClick = { callbacks.onRestChange(null) },
                        )
                        // A stored value outside the options keeps its own chip.
                        val stored = state.restSeconds
                        val options = if (stored == null || stored in RestTimerRules.OPTIONS) {
                            RestTimerRules.OPTIONS
                        } else {
                            (RestTimerRules.OPTIONS + stored).sorted()
                        }
                        options.forEach { seconds ->
                            BoraChip(
                                label = stringResource(R.string.editor_rest_value, seconds),
                                selected = state.restSeconds == seconds,
                                onClick = { callbacks.onRestChange(seconds) },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.setupNote,
                onValueChange = callbacks.onSetupNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_setup_note)) },
                placeholder = { Text(stringResource(R.string.editor_setup_note_hint)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            if (state.steps.isNotEmpty()) {
                HowToCard(
                    steps = state.steps,
                    portuguese = state.portuguese,
                    imageSlug = ExerciseImages.slugFor(state.name, state.libraryId),
                    imageName = state.name,
                )
            }

            BoraButton(
                text = stringResource(CoreUiR.string.save),
                onClick = callbacks.onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.isNew) {
                BoraButton(
                    text = stringResource(if (state.archived) R.string.editor_unarchive else R.string.editor_archive),
                    onClick = callbacks.onToggleArchived,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                    style = BoraButtonStyle.Tonal,
                    leadingIcon = if (state.archived) CoreUiR.drawable.ic_unarchive else CoreUiR.drawable.ic_archive,
                )
                BoraButton(
                    text = stringResource(R.string.editor_delete),
                    onClick = callbacks.onDelete,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                    style = BoraButtonStyle.Text,
                )
            }
        }
    }

    ConvertKindDialog(
        dialog = state.convertDialog,
        onFactorStep = callbacks.onFactorStep,
        onFactorSet = callbacks.onFactorSet,
        onConfirm = callbacks.onConfirmConvert,
        onDismiss = callbacks.onDismissConvert,
    )
    DeleteMachineDialog(
        dialog = state.deleteDialog,
        onConfirm = callbacks.onConfirmDelete,
        onArchiveInstead = callbacks.onArchiveInstead,
        onDismiss = callbacks.onDismissDelete,
    )
}

/** Why some kinds are off for a machine with sets: the count, then one line per reason. */
@Composable
private fun KindLockedCaption(state: MachineEditorUiState) {
    val reasons = state.conversions.values.filterIsInstance<KindConversion.NotOffered>().map { it.reason }.toSet()
    if (reasons.isEmpty()) return
    val count = LocalContext.current.resources.getQuantityString(R.plurals.editor_kind_locked, state.setCount, state.setCount)
    val lines = buildList {
        add(count)
        if (NotOfferedReason.CARDIO in reasons) add(stringResource(R.string.editor_kind_locked_cardio))
        if (NotOfferedReason.LOAD_WOULD_BE_LOST in reasons) add(stringResource(R.string.editor_kind_locked_load))
        if (NotOfferedReason.NO_COMMON_MEASURE in reasons) add(stringResource(R.string.editor_kind_locked_measure))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

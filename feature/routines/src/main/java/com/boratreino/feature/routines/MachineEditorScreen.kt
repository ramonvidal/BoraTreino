package com.boratreino.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.label
import com.boratreino.core.ui.R as CoreUiR

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
    Scaffold(
        modifier = modifier,
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
                        FilterChip(
                            selected = state.muscleGroup == group,
                            onClick = { callbacks.onMuscleGroupChange(group) },
                            label = { Text(group.label()) },
                        )
                    }
                }
            }

            Section(stringResource(R.string.editor_kind)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.kind == MachineKind.WEIGHT_REPS,
                        onClick = { callbacks.onKindChange(MachineKind.WEIGHT_REPS) },
                        label = { Text(stringResource(R.string.editor_kind_weights)) },
                    )
                    FilterChip(
                        selected = state.kind == MachineKind.BODYWEIGHT_REPS,
                        onClick = { callbacks.onKindChange(MachineKind.BODYWEIGHT_REPS) },
                        label = { Text(stringResource(R.string.editor_kind_bodyweight)) },
                    )
                    FilterChip(
                        selected = state.kind == MachineKind.CARDIO,
                        onClick = { callbacks.onKindChange(MachineKind.CARDIO) },
                        label = { Text(stringResource(R.string.editor_kind_cardio)) },
                    )
                }
            }

            // Weight step and rest timer don't apply to cardio.
            if (state.kind != MachineKind.CARDIO) {
                Section(stringResource(R.string.editor_step)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // A machine saved with another step keeps it as a fourth choice.
                        val steps = if (state.stepKg in StepChoices) StepChoices else (StepChoices + state.stepKg).sorted()
                        steps.forEach { step ->
                            FilterChip(
                                selected = state.stepKg == step,
                                onClick = { callbacks.onStepChange(step) },
                                label = { Text(stringResource(R.string.editor_step_value, formatKg(step))) },
                            )
                        }
                    }
                }

                Section(stringResource(R.string.editor_rest)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.restSeconds == null,
                            onClick = { callbacks.onRestChange(null) },
                            label = { Text(stringResource(R.string.editor_rest_default)) },
                        )
                        // A stored value outside the options keeps its own chip.
                        val stored = state.restSeconds
                        val options = if (stored == null || stored in RestTimerRules.OPTIONS) {
                            RestTimerRules.OPTIONS
                        } else {
                            (RestTimerRules.OPTIONS + stored).sorted()
                        }
                        options.forEach { seconds ->
                            FilterChip(
                                selected = state.restSeconds == seconds,
                                onClick = { callbacks.onRestChange(seconds) },
                                label = { Text(stringResource(R.string.editor_rest_value, seconds)) },
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
                HowToCard(steps = state.steps, portuguese = state.portuguese)
            }

            Button(
                onClick = callbacks.onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(CoreUiR.string.save))
            }

            if (!state.isNew) {
                OutlinedButton(
                    onClick = callbacks.onToggleArchived,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.archived) CoreUiR.drawable.ic_unarchive else CoreUiR.drawable.ic_archive,
                        ),
                        contentDescription = null,
                    )
                    Text(
                        stringResource(if (state.archived) R.string.editor_unarchive else R.string.editor_archive),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = callbacks.onDelete,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    DeleteMachineDialog(
        dialog = state.deleteDialog,
        onConfirm = callbacks.onConfirmDelete,
        onArchiveInstead = callbacks.onArchiveInstead,
        onDismiss = callbacks.onDismissDelete,
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

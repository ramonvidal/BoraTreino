package com.boratreino.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.ui.SmallStepper
import com.boratreino.core.ui.label
import com.boratreino.core.ui.shortLabel
import com.boratreino.core.ui.targetText
import com.boratreino.core.ui.R as CoreUiR
import java.time.DayOfWeek

@Composable
internal fun RoutineEditorScreen(
    onDone: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    RoutineEditorContent(
        state = state,
        callbacks = RoutineEditorCallbacks(
            onBack = onDone,
            onNameChange = viewModel::setName,
            onShortNameChange = viewModel::setShortName,
            onToggleWeekday = viewModel::toggleWeekday,
            onOpenPicker = viewModel::openPicker,
            onClosePicker = viewModel::closePicker,
            onAddMachine = viewModel::addMachine,
            onMoveItem = viewModel::moveItem,
            onRemoveItem = viewModel::removeItem,
            onSetsStep = viewModel::stepSets,
            onRepsMinStep = viewModel::stepRepsMin,
            onRepsMaxStep = viewModel::stepRepsMax,
            onSave = viewModel::save,
            onDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::dismissDelete,
        ),
    )
}

/** The routine editor, drawn from its state alone, so screenshot tests can render it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RoutineEditorContent(
    state: RoutineEditorUiState,
    callbacks: RoutineEditorCallbacks,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.routine_title_new else R.string.routine_title_edit))
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = callbacks.onNameChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.routine_name)) },
                    singleLine = true,
                    isError = state.nameError,
                    supportingText = if (state.nameError) {
                        { Text(stringResource(R.string.routine_name_error)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = state.shortName,
                    onValueChange = callbacks.onShortNameChange,
                    modifier = Modifier.weight(0.5f),
                    label = { Text(stringResource(R.string.routine_short_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }

            if (state.showWeekdays) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.routine_weekdays),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.values().forEach { day ->
                            FilterChip(
                                selected = day in state.weekdays,
                                onClick = { callbacks.onToggleWeekday(day) },
                                label = { Text(day.shortLabel()) },
                            )
                        }
                    }
                }
            }

            Text(stringResource(R.string.routine_machines), style = MaterialTheme.typography.titleMedium)
            if (state.items.isEmpty()) {
                Text(
                    stringResource(R.string.routine_no_machines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.items.forEachIndexed { index, entry ->
                ItemCard(entry, index, lastIndex = state.items.lastIndex, callbacks = callbacks)
            }
            OutlinedButton(
                onClick = callbacks.onOpenPicker,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.routine_add_machine), modifier = Modifier.padding(start = 8.dp))
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
                    onClick = callbacks.onDelete,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.routine_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (state.pickerOpen) {
        MachinePickerDialog(state, callbacks)
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = callbacks.onDismissDelete,
            title = { Text(stringResource(R.string.routine_delete_title)) },
            text = { Text(stringResource(R.string.routine_delete_body)) },
            confirmButton = {
                TextButton(onClick = callbacks.onConfirmDelete) {
                    Text(stringResource(R.string.routine_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissDelete) { Text(stringResource(CoreUiR.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ItemCard(entry: RoutineEditorItem, index: Int, lastIndex: Int, callbacks: RoutineEditorCallbacks) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.machineName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        targetText(entry.item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { callbacks.onMoveItem(index, true) }, enabled = index > 0) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_arrow_upward),
                        contentDescription = stringResource(R.string.routine_move_up, entry.machineName),
                    )
                }
                IconButton(onClick = { callbacks.onMoveItem(index, false) }, enabled = index < lastIndex) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_arrow_downward),
                        contentDescription = stringResource(R.string.routine_move_down, entry.machineName),
                    )
                }
                IconButton(onClick = { callbacks.onRemoveItem(index) }) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.routine_remove, entry.machineName),
                    )
                }
            }
            FlowRow(
                Modifier.padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LabelledStepper(
                    label = stringResource(R.string.routine_sets),
                    value = entry.item.targetSets.toString(),
                    onMinus = { callbacks.onSetsStep(index, false) },
                    onPlus = { callbacks.onSetsStep(index, true) },
                )
                LabelledStepper(
                    label = stringResource(R.string.routine_reps_min),
                    value = entry.item.repsMin.toString(),
                    onMinus = { callbacks.onRepsMinStep(index, false) },
                    onPlus = { callbacks.onRepsMinStep(index, true) },
                )
                LabelledStepper(
                    label = stringResource(R.string.routine_reps_max),
                    value = entry.item.repsMax.toString(),
                    onMinus = { callbacks.onRepsMaxStep(index, false) },
                    onPlus = { callbacks.onRepsMaxStep(index, true) },
                )
            }
        }
    }
}

@Composable
private fun LabelledStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SmallStepper(value = value, onMinus = onMinus, onPlus = onPlus, contentDescription = label)
    }
}

/** "Add machine": your machines (not archived) not yet in the routine, by muscle group. */
@Composable
private fun MachinePickerDialog(state: RoutineEditorUiState, callbacks: RoutineEditorCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onClosePicker,
        title = { Text(stringResource(R.string.routine_add_machine)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.available.isEmpty()) {
                    Text(
                        stringResource(R.string.routine_add_machine_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.available.forEach { group ->
                    Text(
                        group.muscleGroup.label(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    group.machines.forEach { machine ->
                        Text(
                            machine.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                                .clickable { callbacks.onAddMachine(machine) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = callbacks.onClosePicker) { Text(stringResource(CoreUiR.string.cancel)) }
        },
    )
}

package com.boratreino.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.MachineKind
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.MoveButtons
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
            onBack = viewModel::back,
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
            onOpenTarget = viewModel::openTarget,
            onCloseTarget = viewModel::closeTarget,
            onDuplicate = viewModel::duplicate,
            onDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::dismissDelete,
        ),
    )
}

/**
 * The routine editor, drawn from its state alone, so screenshot tests can render it. There is
 * no Save: every change is stored at once. The name is edited right in the title.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RoutineEditorContent(
    state: RoutineEditorUiState,
    callbacks: RoutineEditorCallbacks,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = state.name,
                            onValueChange = callbacks.onNameChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.routine_name)) },
                            textStyle = MaterialTheme.typography.titleLarge,
                            singleLine = true,
                            colors = titleFieldColors(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        TextField(
                            value = state.shortName,
                            onValueChange = callbacks.onShortNameChange,
                            modifier = Modifier.width(72.dp),
                            placeholder = { Text(stringResource(R.string.routine_short_name)) },
                            textStyle = MaterialTheme.typography.titleMedium,
                            singleLine = true,
                            colors = titleFieldColors(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_back),
                            contentDescription = stringResource(CoreUiR.string.back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.routine_more),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routines_menu_duplicate)) },
                                onClick = { menuOpen = false; callbacks.onDuplicate() },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.routines_menu_delete), color = MaterialTheme.colorScheme.error)
                                },
                                onClick = { menuOpen = false; callbacks.onDelete() },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            if (state.items.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    state.items.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ItemRow(entry, index, lastIndex = state.items.lastIndex, callbacks = callbacks)
                    }
                }
            }
            OutlinedButton(
                onClick = callbacks.onOpenPicker,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.routine_add_machine), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (state.pickerOpen) {
        MachinePickerDialog(state, callbacks)
    }
    val targetIndex = state.items.indexOfFirst { it.item.machineId == state.targetMachineId }
    if (targetIndex >= 0) {
        SetsRepsSheet(entry = state.items[targetIndex], index = targetIndex, callbacks = callbacks)
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
private fun titleFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
)

/** One compact machine row: thumb, name, "3 x 8-12"; tap opens the sets / reps sheet. */
@Composable
private fun ItemRow(entry: RoutineEditorItem, index: Int, lastIndex: Int, callbacks: RoutineEditorCallbacks) {
    val cardio = entry.kind == MachineKind.CARDIO
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.weight(1f).defaultMinSize(minHeight = 56.dp)
                .then(if (cardio) Modifier else Modifier.clickable { callbacks.onOpenTarget(entry.item.machineId) })
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MachineThumb(name = entry.machineName, muscleGroup = entry.muscleGroup, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(entry.machineName, style = MaterialTheme.typography.bodyLarge)
                if (!cardio) {
                    Text(
                        targetText(entry.item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        MoveButtons(
            canMoveUp = index > 0,
            canMoveDown = index < lastIndex,
            onUp = { callbacks.onMoveItem(index, true) },
            onDown = { callbacks.onMoveItem(index, false) },
        )
        IconButton(onClick = { callbacks.onRemoveItem(index) }) {
            Icon(
                painterResource(CoreUiR.drawable.ic_close),
                contentDescription = stringResource(R.string.routine_remove, entry.machineName),
            )
        }
    }
}

/** The bottom sheet with the three steppers for one machine's target. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetsRepsSheet(entry: RoutineEditorItem, index: Int, callbacks: RoutineEditorCallbacks) {
    ModalBottomSheet(
        onDismissRequest = callbacks.onCloseTarget,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SetsRepsSheetContent(entry, index, callbacks)
    }
}

/** What is inside the sets / reps sheet, apart from the sheet itself, so it can be tested alone. */
@Composable
internal fun SetsRepsSheetContent(entry: RoutineEditorItem, index: Int, callbacks: RoutineEditorCallbacks) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(entry.machineName, style = MaterialTheme.typography.titleLarge)
        Text(
            targetText(entry.item),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
        Button(
            onClick = callbacks.onCloseTarget,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.routine_close)) }
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
        title = { Text(stringResource(R.string.routine_picker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.routine_picker_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        Row(
                            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                                .clickable { callbacks.onAddMachine(machine) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MachineThumb(machine = machine, size = 40.dp)
                            Text(machine.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = callbacks.onClosePicker) { Text(stringResource(CoreUiR.string.cancel)) }
        },
    )
}

package com.boratreino.feature.calendar

import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.weightLabel
import com.boratreino.core.ui.draftText
import com.boratreino.core.ui.showUndo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.EmptyState
import com.boratreino.core.ui.NumberPadDialog
import com.boratreino.core.ui.RoutineBadge
import com.boratreino.core.ui.Stepper
import com.boratreino.core.ui.label
import kotlinx.coroutines.launch
import com.boratreino.core.ui.R as CoreUiR

/** What the session editor can ask for. */
internal data class SessionEditorActions(
    val onBack: () -> Unit = {},
    /** Null = Free. */
    val onChooseRoutine: (routineId: Long?) -> Unit = {},
    val onAddSet: (machineId: Long) -> Unit = {},
    val onAddMachine: () -> Unit = {},
    val onPickMachine: (machineId: Long) -> Unit = {},
    val onDismissPicker: () -> Unit = {},
    val onOpenSet: (WorkoutSet) -> Unit = {},
    val onStepEditorWeight: (up: Boolean) -> Unit = {},
    val onStepEditorReps: (up: Boolean) -> Unit = {},
    val onOpenPad: (PadTarget) -> Unit = {},
    val onConfirmPad: (String) -> Unit = {},
    val onDismissPad: () -> Unit = {},
    val onSaveEditor: () -> Unit = {},
    val onDeleteEditor: () -> Unit = {},
    val onDismissEditor: () -> Unit = {},
)

@Composable
internal fun SessionEditorScreen(
    onBack: () -> Unit,
    viewModel: SessionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val removedText = stringResource(R.string.calendar_set_removed)
    val undoLabel = stringResource(CoreUiR.string.undo)
    val failedText = stringResource(CoreUiR.string.error_generic)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Only sent once the repository write has finished.
                SessionEditorEvent.SetAdded -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                is SessionEditorEvent.SetRemoved -> launch {
                    if (snackbar.showUndo(removedText, undoLabel)) viewModel.restoreSet(event.set)
                }
                SessionEditorEvent.Failed -> launch { snackbar.showSnackbar(failedText) }
            }
        }
    }

    SessionEditorContent(
        state = state,
        actions = SessionEditorActions(
            onBack = onBack,
            onChooseRoutine = viewModel::chooseRoutine,
            onAddSet = viewModel::addSet,
            onAddMachine = viewModel::openPicker,
            onPickMachine = viewModel::addMachine,
            onDismissPicker = viewModel::closePicker,
            onOpenSet = viewModel::openSet,
            onStepEditorWeight = viewModel::stepEditorWeight,
            onStepEditorReps = viewModel::stepEditorReps,
            onOpenPad = viewModel::openPad,
            onConfirmPad = viewModel::confirmPad,
            onDismissPad = viewModel::dismissPad,
            onSaveEditor = viewModel::saveEditor,
            onDeleteEditor = viewModel::deleteEditor,
            onDismissEditor = viewModel::closeEditor,
        ),
        snackbarHostState = snackbar,
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionEditorContent(
    state: SessionEditorUiState,
    actions: SessionEditorActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val titleFormat = remember { dayTitleFormatter() }
    val detail = state.detail
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(state.date?.format(titleFormat).orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(CoreUiR.drawable.ic_back), stringResource(CoreUiR.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            !state.loaded -> Unit
            detail == null -> EmptyState(
                title = stringResource(R.string.calendar_session_not_found),
                modifier = Modifier.padding(innerPadding),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoutineChips(state, actions.onChooseRoutine)
                if (detail.machines.isEmpty()) {
                    Text(
                        stringResource(R.string.calendar_no_sets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                detail.machines.forEach { entry -> MachineCard(entry, actions) }
                Button(
                    onClick = actions.onAddMachine,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null)
                    Text(stringResource(R.string.calendar_add_machine), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (state.picking) {
        MachinePicker(state.machines, actions)
    }
    val editor = state.editor
    if (editor != null) {
        EditSetSheet(editor, actions)
    }
    val pad = state.pad
    if (editor != null && pad != null) {
        val isWeight = pad.target == PadTarget.EDIT_WEIGHT
        NumberPadDialog(
            title = if (isWeight) weightLabel(editor.machine) else stringResource(R.string.calendar_reps),
            initial = pad.initial,
            allowDecimal = isWeight,
            onConfirm = actions.onConfirmPad,
            onDismiss = actions.onDismissPad,
        )
    }
}

/** One chip per routine, and Free; the session's own is green. */
@Composable
private fun RoutineChips(state: SessionEditorUiState, onChoose: (routineId: Long?) -> Unit) {
    val current = state.detail?.session?.routineId
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.routines.forEach { routine ->
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = routine.name, role = Role.RadioButton) { onChoose(routine.id) },
                contentAlignment = Alignment.Center,
            ) {
                RoutineBadge(routine.shortName, selected = routine.id == current)
            }
        }
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.RadioButton) { onChoose(null) },
            contentAlignment = Alignment.Center,
        ) {
            RoutineBadge(stringResource(R.string.calendar_free), selected = current == null)
        }
    }
}

/** A machine of the session: its numbered sets (tap to fix one) and Add set. */
@Composable
private fun MachineCard(entry: SessionMachine, actions: SessionEditorActions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                entry.machine.name,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            entry.sets.forEachIndexed { index, set ->
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(onClickLabel = stringResource(R.string.calendar_edit_set)) { actions.onOpenSet(set) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.width(36.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        draftText(entry.machine, set.weightKg ?: 0.0, set.reps ?: 0),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            FilledTonalButton(
                onClick = { actions.onAddSet(entry.machine.id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp),
            ) {
                Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.calendar_add_set), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Your machines, grouped by muscle group; picking one adds its first set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachinePicker(machines: List<Machine>, actions: SessionEditorActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismissPicker,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            stringResource(R.string.calendar_pick_machine),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        if (machines.isEmpty()) {
            Text(
                stringResource(R.string.calendar_no_machines),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val groups = MuscleGroup.entries.mapNotNull { group ->
                machines.filter { it.muscleGroup == group }.takeIf { it.isNotEmpty() }?.let { group to it }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                groups.forEach { (group, inGroup) ->
                    item(key = "group-${group.name}") {
                        Text(
                            group.label(),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(inGroup, key = { it.id }) { machine ->
                        Text(
                            machine.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { actions.onPickMachine(machine.id) }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

/** Fix or delete a set: the two steppers, Delete and Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSetSheet(editor: SetEdit, actions: SessionEditorActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismissEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.calendar_set_number, editor.number), style = MaterialTheme.typography.titleLarge)
            Stepper(
                label = weightLabel(editor.machine),
                value = formatKg(editor.weightKg),
                onMinus = { actions.onStepEditorWeight(false) },
                onPlus = { actions.onStepEditorWeight(true) },
                onValueClick = { actions.onOpenPad(PadTarget.EDIT_WEIGHT) },
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper(
                label = stringResource(R.string.calendar_reps),
                value = editor.reps.toString(),
                onMinus = { actions.onStepEditorReps(false) },
                onPlus = { actions.onStepEditorReps(true) },
                onValueClick = { actions.onOpenPad(PadTarget.EDIT_REPS) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = actions.onDeleteEditor,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(CoreUiR.drawable.ic_delete), contentDescription = null)
                    Text(stringResource(CoreUiR.string.delete), Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = actions.onSaveEditor,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(stringResource(CoreUiR.string.save))
                }
            }
        }
    }
}

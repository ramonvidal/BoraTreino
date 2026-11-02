package com.boratreino.feature.today

import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.weightLabel
import com.boratreino.core.ui.summaryLabel
import com.boratreino.core.ui.draftText
import com.boratreino.core.ui.showUndo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.Machine
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.EmptyState
import com.boratreino.core.ui.NumberPadDialog
import com.boratreino.core.ui.Stepper
import com.boratreino.core.ui.recordText
import com.boratreino.core.ui.suggestionText
import com.boratreino.core.ui.targetText
import com.boratreino.core.model.Suggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.boratreino.core.ui.R as CoreUiR

/** What the machine screen can ask for. */
internal data class MachineActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onStepWeight: (up: Boolean) -> Unit = {},
    val onStepReps: (up: Boolean) -> Unit = {},
    val onOpenPad: (PadTarget) -> Unit = {},
    val onConfirmPad: (String) -> Unit = {},
    val onDismissPad: () -> Unit = {},
    val onLogSet: () -> Unit = {},
    val onSameAgain: () -> Unit = {},
    val onOpenSet: (WorkoutSet) -> Unit = {},
    val onStepEditorWeight: (up: Boolean) -> Unit = {},
    val onStepEditorReps: (up: Boolean) -> Unit = {},
    val onSaveEditor: () -> Unit = {},
    val onDeleteEditor: () -> Unit = {},
    val onDismissEditor: () -> Unit = {},
)

@Composable
internal fun MachineScreen(
    onBack: () -> Unit,
    onEdit: (machineId: Long) -> Unit,
    viewModel: MachineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val removedText = stringResource(R.string.today_set_removed)
    val undoLabel = stringResource(CoreUiR.string.undo)
    val failedText = stringResource(CoreUiR.string.error_generic)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Only sent once the repository write has finished.
                is MachineEvent.Logged -> launch {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (event.record != null) {
                        // A record buzzes three times, so it can be told from a normal set without looking.
                        repeat(2) {
                            delay(140)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                is MachineEvent.SetRemoved -> launch {
                    if (snackbar.showUndo(removedText, undoLabel)) viewModel.restoreSet(event.set)
                }
                MachineEvent.Failed -> launch { snackbar.showSnackbar(failedText) }
            }
        }
    }

    MachineContent(
        state = state,
        actions = MachineActions(
            onBack = onBack,
            onEdit = { state.machine?.let { onEdit(it.id) } },
            onStepWeight = viewModel::stepWeight,
            onStepReps = viewModel::stepReps,
            onOpenPad = viewModel::openPad,
            onConfirmPad = viewModel::confirmPad,
            onDismissPad = viewModel::dismissPad,
            onLogSet = viewModel::logSet,
            onSameAgain = viewModel::logSameAgain,
            onOpenSet = viewModel::openSet,
            onStepEditorWeight = viewModel::stepEditorWeight,
            onStepEditorReps = viewModel::stepEditorReps,
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
internal fun MachineContent(
    state: MachineUiState,
    actions: MachineActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val machine = state.machine
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(machine?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(CoreUiR.drawable.ic_back), stringResource(CoreUiR.string.back))
                    }
                },
                actions = {
                    if (machine != null) {
                        IconButton(onClick = actions.onEdit) {
                            Icon(painterResource(CoreUiR.drawable.ic_edit), stringResource(R.string.today_edit_machine))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            !state.loaded -> Unit
            machine == null -> EmptyState(
                title = stringResource(R.string.today_machine_not_found),
                modifier = Modifier.padding(innerPadding),
            )
            else -> Column(Modifier.fillMaxSize().padding(innerPadding)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoCard(machine, state)
                    state.record?.let { RecordCard(recordText(it)) }
                    SetsCard(machine, state.sets, actions.onOpenSet)
                }
                LogPanel(machine, state, actions)
            }
        }
    }

    val editor = state.editor
    if (machine != null && editor != null) {
        EditSetSheet(machine, editor, actions)
    }
    val pad = state.pad
    if (machine != null && pad != null) {
        val title = when (pad.target) {
            PadTarget.WEIGHT, PadTarget.EDIT_WEIGHT -> weightLabel(machine)
            PadTarget.REPS, PadTarget.EDIT_REPS -> stringResource(R.string.today_reps)
        }
        NumberPadDialog(
            title = title,
            initial = pad.initial,
            allowDecimal = pad.target == PadTarget.WEIGHT || pad.target == PadTarget.EDIT_WEIGHT,
            onConfirm = actions.onConfirmPad,
            onDismiss = actions.onDismissPad,
        )
    }
}

/** Setup note and "Last time" in one white card. */
@Composable
private fun InfoCard(machine: Machine, state: MachineUiState) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!machine.setupNote.isNullOrBlank()) {
                Text(
                    machine.setupNote!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val last = state.lastTime
            val lastAt = state.lastTimeAt
            Text(
                text = when {
                    last == null -> stringResource(R.string.today_first_time)
                    lastAt == null -> stringResource(R.string.today_last_time, summaryLabel(machine, last))
                    else -> stringResource(
                        R.string.today_last_time_on,
                        summaryLabel(machine, last),
                        dateFormat.format(lastAt),
                    )
                },
                style = MaterialTheme.typography.titleMedium,
            )
            state.target?.let {
                Text(
                    stringResource(R.string.today_target, targetText(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.suggestion?.let { suggestion ->
                Text(suggestionText(suggestion), style = MaterialTheme.typography.titleMedium)
                if (suggestion is Suggestion.Down) {
                    Text(
                        stringResource(R.string.today_missed_twice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** This session's sets as numbered rows; tapping one opens the editor sheet. */
@Composable
private fun SetsCard(machine: Machine, sets: List<WorkoutSet>, onOpenSet: (WorkoutSet) -> Unit) {
    if (sets.isEmpty()) return
    SectionTitle(stringResource(R.string.today_this_session))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        sets.forEachIndexed { index, set ->
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClickLabel = stringResource(R.string.today_edit_set)) { onOpenSet(set) }
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
                    draftText(machine, set.weightKg ?: 0.0, set.reps ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** The bottom half: steppers, then Same again and Log set. */
@Composable
private fun LogPanel(machine: Machine, state: MachineUiState, actions: MachineActions) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stepper(
                label = weightLabel(machine),
                value = formatKg(state.weightKg),
                onMinus = { actions.onStepWeight(false) },
                onPlus = { actions.onStepWeight(true) },
                onValueClick = { actions.onOpenPad(PadTarget.WEIGHT) },
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper(
                label = stringResource(R.string.today_reps),
                value = state.reps.toString(),
                onMinus = { actions.onStepReps(false) },
                onPlus = { actions.onStepReps(true) },
                onValueClick = { actions.onOpenPad(PadTarget.REPS) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val same = state.sameAgain
                if (same != null) {
                    OutlinedButton(
                        onClick = actions.onSameAgain,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.today_same_again),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                draftText(machine, same.weightKg, same.reps),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // The one green button on the screen.
                Button(
                    onClick = actions.onLogSet,
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                ) {
                    Text(stringResource(R.string.today_log_set), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Fix or delete a logged set: the two steppers, Save and Delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSetSheet(machine: Machine, editor: SetEditor, actions: MachineActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismissEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.today_set_number, editor.number), style = MaterialTheme.typography.titleLarge)
            Stepper(
                label = weightLabel(machine),
                value = formatKg(editor.weightKg),
                onMinus = { actions.onStepEditorWeight(false) },
                onPlus = { actions.onStepEditorWeight(true) },
                onValueClick = { actions.onOpenPad(PadTarget.EDIT_WEIGHT) },
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper(
                label = stringResource(R.string.today_reps),
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
                    Text(stringResource(R.string.today_delete), Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = actions.onSaveEditor,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                ) {
                    Text(stringResource(CoreUiR.string.save))
                }
            }
        }
    }
}

/** "New record: 125 kg" with a tick, shown for a few seconds after the set is logged. */
@Composable
private fun RecordCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(CoreUiR.drawable.ic_emoji_events), contentDescription = null)
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

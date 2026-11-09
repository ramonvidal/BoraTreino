package com.boratreino.feature.today

import com.boratreino.core.model.DayKind
import com.boratreino.core.model.MachineKind
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import com.boratreino.core.ui.summaryLabel
import com.boratreino.core.ui.showUndo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.Machine
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.ui.MoveButtons
import com.boratreino.core.ui.targetText
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Alignment
import com.boratreino.core.ui.label
import com.boratreino.core.ui.BoraChip
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.boratreino.core.ui.R as CoreUiR

/** What the Today tab can ask for; `:app` and the graph decide where each goes. */
internal data class TodayActions(
    val onOpenMachine: (machineId: Long) -> Unit = {},
    val onAllMachines: () -> Unit = {},
    val onAddMachine: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onStartEditing: () -> Unit = {},
    val onFinishEditing: () -> Unit = {},
    val onMoveMachine: (machineId: Long, up: Boolean) -> Unit = { _, _ -> },
    val onRemoveMachine: (machineId: Long) -> Unit = {},
    /** Opens the full editor of the active ficha (Edit sets and reps). */
    val onEditRoutine: () -> Unit = {},
    val onChooseRoutine: (RoutineChoice) -> Unit = {},
    val onSkipMachine: (machineId: Long) -> Unit = {},
    val onSkipToday: () -> Unit = {},
    val onRemoveFromRoutine: () -> Unit = {},
    val onDismissSkip: () -> Unit = {},
    val onRepeatSame: () -> Unit = {},
    val onRepeatSuggested: () -> Unit = {},
    val summary: SummaryActions = SummaryActions(),
)

@Composable
internal fun TodayScreen(
    onOpenMachine: (Long) -> Unit,
    onAllMachines: () -> Unit,
    onEditRoutine: (Long) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val undoLabel = stringResource(CoreUiR.string.undo)
    val failedText = stringResource(CoreUiR.string.error_generic)
    val context = LocalContext.current

    // Leaving the screen ends edit mode.
    DisposableEffect(viewModel) { onDispose { viewModel.finishEditing() } }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // The summary sheet replaces the old Done snackbar; it opens from the state.
                is TodayEvent.Finished -> Unit
                is TodayEvent.Share -> launch {
                    // Drawing the picture is off the main thread; opening the share sheet is not.
                    val chooser = withContext(Dispatchers.IO) {
                        runCatching { shareChooser(context, event.summary) }.getOrNull()
                    }
                    val started = chooser != null && runCatching { context.startActivity(chooser) }.isSuccess
                    if (!started) viewModel.shareFailed()
                }
                is TodayEvent.Skipped -> launch {
                    val text = resources.getString(R.string.today_skipped, event.machine.name)
                    if (snackbar.showUndo(text, undoLabel)) viewModel.unskip(event.machine.id)
                }
                is TodayEvent.Removed -> launch {
                    val text = resources.getString(R.string.today_removed_from, event.routineName)
                    if (snackbar.showUndo(text, undoLabel)) viewModel.restoreItem(event.item)
                }
                is TodayEvent.Repeated -> launch {
                    val text = resources.getQuantityString(
                        R.plurals.today_repeated, event.outcome.machineCount, event.outcome.machineCount,
                    )
                    if (snackbar.showUndo(text, undoLabel)) viewModel.undoRepeat(event.outcome)
                }
                TodayEvent.Failed -> launch { snackbar.showSnackbar(failedText) }
            }
        }
    }

    TodayContent(
        state = state,
        actions = TodayActions(
            onOpenMachine = onOpenMachine,
            onAllMachines = onAllMachines,
            onAddMachine = onAllMachines,
            onDone = viewModel::finish,
            onStartEditing = viewModel::startEditing,
            onFinishEditing = viewModel::finishEditing,
            onMoveMachine = viewModel::moveMachine,
            onRemoveMachine = viewModel::removeMachine,
            onEditRoutine = {
                state.routines?.activeId?.let {
                    viewModel.finishEditing()
                    onEditRoutine(it)
                }
            },
            onChooseRoutine = viewModel::choose,
            onSkipMachine = viewModel::askSkip,
            onSkipToday = viewModel::skipToday,
            onRemoveFromRoutine = viewModel::removeFromRoutine,
            onDismissSkip = viewModel::dismissSkip,
            onRepeatSame = { viewModel.repeatLast(RepeatMode.SAME) },
            onRepeatSuggested = { viewModel.repeatLast(RepeatMode.SUGGESTED) },
            summary = SummaryActions(
                onShare = viewModel::shareSummary,
                onUpdateRoutine = viewModel::applyRoutineUpdate,
                onUndoRoutineUpdate = viewModel::undoRoutineUpdate,
                onNotFinished = viewModel::reopenFromSummary,
                onClose = viewModel::dismissSummary,
            ),
        ),
        snackbarHostState = snackbar,
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun TodayContent(
    state: TodayUiState,
    actions: TodayActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val session = state.session
            val routines = state.routines
            todayHeader(state)
            if (routines == null) energyLine(state.energy)
            if (routines != null) {
                withRoutines(state, routines, actions)
            } else if (session == null) {
                noSession(state.recent, actions)
            } else {
                openSession(session, actions)
            }
        }
    }

    state.skipDialog?.let { SkipMachineDialog(it, actions) }
    state.summary?.let { TodaySummarySheet(it, actions.summary) }
}

/** App name, the date in large type and, under it, the next or active routine in one grey line. */
private fun LazyListScope.todayHeader(state: TodayUiState) {
    item(key = "today_header") {
        val resources = LocalContext.current.resources
        val date = state.today
        val routines = state.routines
        val active = routines?.activeName
        Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.today_app_name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (date != null) {
                val locale = Locale.getDefault()
                val dateText = remember(date, locale) {
                    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
                    date.format(DateTimeFormatter.ofPattern(pattern, locale))
                }
                Text(
                    dateText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (routines != null && active != null) {
                val counts = resources.machinesAndSets(
                    routines.machines.size,
                    routines.machines.sumOf { it.target.targetSets },
                )
                Text(
                    stringResource(R.string.today_header_routine, active, counts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "Skip Pull-ups today?": skip for this training, take it out of the ficha, or cancel. */
@Composable
private fun SkipMachineDialog(dialog: SkipDialog, actions: TodayActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissSkip,
        title = { Text(stringResource(R.string.today_skip_title, dialog.machine.name)) },
        text = {
            OutlinedButton(
                onClick = actions.onRemoveFromRoutine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.today_remove_from, dialog.routineName)) }
        },
        confirmButton = {
            TextButton(onClick = actions.onSkipToday) { Text(stringResource(R.string.today_skip_today)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissSkip) { Text(stringResource(CoreUiR.string.cancel)) }
        },
    )
}

private fun LazyListScope.noSession(
    recent: List<Machine>,
    actions: TodayActions,
    title: Int = R.string.today_pick_to_start,
    upcoming: UpcomingUi? = null,
) {
    item(key = "title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
    if (upcoming != null) {
        item(key = "upcoming") {
            val day = if (upcoming.tomorrow) {
                stringResource(R.string.today_tomorrow)
            } else {
                upcoming.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            }
            Text(
                stringResource(R.string.today_next_training, day, upcoming.routineName),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(recent, key = { "r${it.id}" }) { machine ->
        MachineRow(
            title = machine.name,
            supporting = machine.muscleGroup.label(),
            onClick = { actions.onOpenMachine(machine.id) },
            leading = { RowThumb(machine) },
        )
    }
    item(key = "all") {
        AddButton(stringResource(R.string.today_all_machines), actions.onAllMachines)
    }
}

private fun LazyListScope.openSession(session: SessionUi, actions: TodayActions) {
    sessionHeader(session, actions, routineName = null)
    sessionRows(session.machines, actions)
    item(key = "add") {
        AddButton(stringResource(R.string.today_add_machine), actions.onAddMachine)
    }
}

private fun LazyListScope.sessionHeader(
    session: SessionUi,
    actions: TodayActions,
    routineName: String?,
    routineProgress: Pair<Int, Int>? = null,
    canEdit: Boolean = false,
    editing: Boolean = false,
    repeatOffer: RepeatOfferUi? = null,
) {
    item(key = "header") {
        val resources = LocalContext.current.resources
        val counts = resources.machinesAndSets(session.machineCount, session.setCount)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (routineName == null) {
                        Text(
                            stringResource(R.string.today_training_now, counts),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.today_training_now_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                routineName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (canEdit && !editing) {
                        IconButton(onClick = actions.onStartEditing) {
                            Icon(
                                painterResource(CoreUiR.drawable.ic_edit),
                                contentDescription = stringResource(R.string.today_edit_ficha),
                            )
                        }
                    }
                }
                if (routineProgress != null && !editing) {
                    val (doneCount, total) = routineProgress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            LocalContext.current.resources.getQuantityString(
                                R.plurals.today_machines_progress, total, doneCount, total,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else doneCount.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (editing) {
                    Text(
                        stringResource(R.string.today_edit_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = actions.onFinishEditing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Icon(painterResource(CoreUiR.drawable.ic_check), contentDescription = null, Modifier.size(20.dp))
                        Text(stringResource(R.string.today_done), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = actions.onEditRoutine,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(painterResource(CoreUiR.drawable.ic_edit), contentDescription = null, Modifier.size(20.dp))
                        Text(stringResource(R.string.today_edit_sets_reps), Modifier.padding(start = 8.dp))
                    }
                } else {
                    if (repeatOffer != null) RepeatButtons(repeatOffer, actions)
                    val allDone = routineProgress != null && routineProgress.second > 0 &&
                        routineProgress.first >= routineProgress.second
                    if (allDone) {
                        Button(
                            onClick = actions.onDone,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Icon(painterResource(CoreUiR.drawable.ic_check), contentDescription = null, Modifier.size(20.dp))
                            Text(stringResource(R.string.today_done), Modifier.padding(start = 8.dp))
                        }
                    } else {
                        OutlinedButton(
                            onClick = actions.onDone,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Icon(painterResource(CoreUiR.drawable.ic_check), contentDescription = null, Modifier.size(20.dp))
                            Text(stringResource(R.string.today_done), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.sessionRows(machines: List<SessionMachineUi>, actions: TodayActions) {
    items(machines, key = { "m${it.machine.id}" }) { item ->
        MachineRow(
            title = item.machine.name,
            supporting = item.summary?.let { summaryLabel(item.machine, it) },
            onClick = { actions.onOpenMachine(item.machine.id) },
            leading = { RowThumb(item.machine) },
        )
    }
}

/** Today when the owner has routines: the chips, then the active routine's machines (or the Phase 1 lists). */
private fun LazyListScope.withRoutines(state: TodayUiState, routines: RoutineUi, actions: TodayActions) {
    item(key = "chips") { RoutineChips(routines, actions.onChooseRoutine) }
    energyLine(state.energy)
    val session = state.session
    val active = routines.activeName
    if (active == null) {
        // Free, or a rest day: the Phase 1 lists, under a "Rest day" heading on a rest day.
        if (session == null) {
            noSession(
                state.recent, actions,
                if (routines.restDay) R.string.today_rest_day else R.string.today_pick_to_start,
                routines.upcoming,
            )
        } else {
            openSession(session, actions)
        }
        return
    }
    if (session == null) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.today_next_up, active),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 4.dp, bottom = 4.dp),
                )
                if (routines.activeId != null && !state.editing) {
                    IconButton(onClick = actions.onStartEditing) {
                        Icon(
                            painterResource(CoreUiR.drawable.ic_edit),
                            contentDescription = stringResource(R.string.today_edit_ficha),
                        )
                    }
                }
            }
        }
        if (state.editing) item(key = "editbar") { EditBar(actions) }
        val offer = state.repeatOffer
        if (offer != null && !state.editing) item(key = "repeat") { RepeatCard(offer, actions) }
        routineRows(routines, actions, state.editing)
        if (!state.editing) item(key = "add") { AddButton(stringResource(R.string.today_add_machine), actions.onAddMachine) }
    } else {
        sessionHeader(session, actions, routineName = active,
            routineProgress = routines.machines.count { it.done } to routines.machines.size, canEdit = routines.activeId != null, editing = state.editing,
            repeatOffer = state.repeatOffer)
        routineRows(routines, actions, state.editing)
        if (routines.alsoThisSession.isNotEmpty() && !state.editing) {
            item(key = "also") { SectionTitle(stringResource(R.string.today_also_this_session)) }
            sessionRows(routines.alsoThisSession, actions)
        }
        if (!state.editing) item(key = "add") { AddButton(stringResource(R.string.today_add_machine), actions.onAddMachine) }
    }
}

/** The two shortcut buttons: log the remaining machines like last time, or with the suggested weights. */
@Composable
private fun RepeatButtons(offer: RepeatOfferUi, actions: TodayActions) {
    val resources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = actions.onRepeatSame,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.today_repeat_same))
                Text(
                    resources.machinesAndSets(offer.machineCount, offer.setCount),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        OutlinedButton(
            onClick = actions.onRepeatSuggested,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.today_repeat_suggested)) }
    }
}

/** The shortcut on the "Next up" card (no session yet). */
@Composable
private fun RepeatCard(offer: RepeatOfferUi, actions: TodayActions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp)) { RepeatButtons(offer, actions) }
    }
}

/** Edit mode's card: hint, Done and Edit sets and reps (used when there is no open session). */
@Composable
private fun EditBar(actions: TodayActions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.today_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = actions.onFinishEditing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(CoreUiR.drawable.ic_check), contentDescription = null, Modifier.size(20.dp))
                Text(stringResource(R.string.today_done), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = actions.onEditRoutine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(painterResource(CoreUiR.drawable.ic_edit), contentDescription = null, Modifier.size(20.dp))
                Text(stringResource(R.string.today_edit_sets_reps), Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun LazyListScope.routineRows(routines: RoutineUi, actions: TodayActions, editing: Boolean) {
    itemsIndexed(routines.machines, key = { _, row -> "rm${row.machine.id}" }) { index, row ->
        MachineRow(
            title = row.machine.name,
            supporting = if (row.machine.kind == MachineKind.CARDIO) {
                row.cardioSummary?.let { summaryLabel(row.machine, it) }
            } else {
                targetText(row.target)
            },
            onClick = { if (!editing) actions.onOpenMachine(row.machine.id) },
            highlighted = row.highlighted && !editing,
            leading = { RowThumb(row.machine) },
            trailing = {
                when {
                    editing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        MoveButtons(
                            canMoveUp = index > 0,
                            canMoveDown = index < routines.machines.lastIndex,
                            onUp = { actions.onMoveMachine(row.machine.id, true) },
                            onDown = { actions.onMoveMachine(row.machine.id, false) },
                        )
                        IconButton(onClick = { actions.onRemoveMachine(row.machine.id) }) {
                            Icon(
                                painterResource(CoreUiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.today_remove_machine, row.machine.name),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    row.canSkip -> IconButton(onClick = { actions.onSkipMachine(row.machine.id) }) {
                        Icon(
                            painterResource(CoreUiR.drawable.ic_close),
                            contentDescription = stringResource(R.string.today_skip_or_remove, row.machine.name),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    row.done -> Icon(
                        painterResource(CoreUiR.drawable.ic_check),
                        contentDescription = stringResource(R.string.today_machine_done),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    row.setCount > 0 && row.machine.kind != MachineKind.CARDIO -> Text(
                        stringResource(R.string.today_progress, row.setCount, row.target.targetSets),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/** Routine chips and "Free"; the selected one is filled green, the suggested one carries a tiny "Next". */
@Composable
private fun RoutineChips(routines: RoutineUi, onChoose: (RoutineChoice) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(routines.chips, key = { it.id }) { chip ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BoraChip(
                    label = chip.shortName,
                    selected = chip.selected,
                    onClick = { onChoose(RoutineChoice.Chosen(chip.id)) },
                    large = true,
                )
                if (chip.suggested && !chip.selected) {
                    Text(
                        stringResource(R.string.today_chip_next),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item(key = "free") {
            BoraChip(
                label = stringResource(R.string.today_free),
                selected = routines.freeSelected,
                onClick = { onChoose(RoutineChoice.Free) },
                large = true,
            )
        }
    }
}

/** The main action of the screen: filled, in the theme's green. */
@Composable
internal fun AddButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null, Modifier.size(20.dp))
        Text(text, Modifier.padding(start = 8.dp))
    }
}

/** One quiet grey line with the day's estimated energy need; nothing without an estimate. Never part of the logging path. */
private fun LazyListScope.energyLine(energy: DayEnergyUi?) {
    if (energy == null) return
    item(key = "energy") {
        val amount = NumberFormat.getIntegerInstance().format(energy.kcal)
        Text(
            if (energy.kind == DayKind.REST) stringResource(R.string.today_energy_rest, amount)
            else stringResource(R.string.today_energy_day, amount),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Draws the picture and returns the chooser that shares it. Throws when anything fails; the caller shows the failure. */
private fun shareChooser(context: android.content.Context, summary: SummaryUi): android.content.Intent {
    val texts = summaryTexts(context.resources, summary.summary)
    val file = renderSummaryPng(context, summary, texts)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return android.content.Intent.createChooser(send, null)
}

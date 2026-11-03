package com.boratreino.feature.today

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.Machine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.ui.RoutineBadge
import com.boratreino.core.ui.targetText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import com.boratreino.core.ui.label
import kotlinx.coroutines.launch
import com.boratreino.core.ui.R as CoreUiR

/** What the Today tab can ask for; `:app` and the graph decide where each goes. */
internal data class TodayActions(
    val onOpenMachine: (machineId: Long) -> Unit = {},
    val onAllMachines: () -> Unit = {},
    val onAddMachine: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onChooseRoutine: (RoutineChoice) -> Unit = {},
    val onSkipMachine: (machineId: Long) -> Unit = {},
    val onSkipToday: () -> Unit = {},
    val onRemoveFromRoutine: () -> Unit = {},
    val onDismissSkip: () -> Unit = {},
)

@Composable
internal fun TodayScreen(
    onOpenMachine: (Long) -> Unit,
    onAllMachines: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val undoLabel = stringResource(CoreUiR.string.undo)
    val failedText = stringResource(CoreUiR.string.error_generic)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TodayEvent.Finished -> launch {
                    val text = resources.getString(
                        R.string.today_done_snackbar,
                        resources.machinesAndSets(event.summary.machineCount, event.summary.setCount, event.summary.recordCount),
                    )
                    if (snackbar.showUndo(text, undoLabel)) viewModel.reopen(event.summary.sessionId)
                }
                is TodayEvent.Skipped -> launch {
                    val text = resources.getString(R.string.today_skipped, event.machine.name)
                    if (snackbar.showUndo(text, undoLabel)) viewModel.unskip(event.machine.id)
                }
                is TodayEvent.Removed -> launch {
                    val text = resources.getString(R.string.today_removed_from, event.routineName)
                    if (snackbar.showUndo(text, undoLabel)) viewModel.restoreItem(event.item)
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
            onOpenSettings = onOpenSettings,
            onChooseRoutine = viewModel::choose,
            onSkipMachine = viewModel::askSkip,
            onSkipToday = viewModel::skipToday,
            onRemoveFromRoutine = viewModel::removeFromRoutine,
            onDismissSkip = viewModel::dismissSkip,
        ),
        snackbarHostState = snackbar,
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_app_name)) },
                actions = {
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_settings),
                            contentDescription = stringResource(R.string.today_open_settings),
                        )
                    }
                },
            )
        },
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
) {
    item(key = "title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
    items(recent, key = { "r${it.id}" }) { machine ->
        MachineRow(
            title = machine.name,
            supporting = machine.muscleGroup.label(),
            onClick = { actions.onOpenMachine(machine.id) },
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

private fun LazyListScope.sessionHeader(session: SessionUi, actions: TodayActions, routineName: String?) {
    item(key = "header") {
        val resources = LocalContext.current.resources
        val counts = resources.machinesAndSets(session.machineCount, session.setCount)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (routineName == null) {
                        stringResource(R.string.today_training_now, counts)
                    } else {
                        stringResource(R.string.today_training_now_routine, routineName, counts)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(
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

private fun LazyListScope.sessionRows(machines: List<SessionMachineUi>, actions: TodayActions) {
    items(machines, key = { "m${it.machine.id}" }) { item ->
        MachineRow(
            title = item.machine.name,
            supporting = item.summary?.let { summaryLabel(item.machine, it) },
            onClick = { actions.onOpenMachine(item.machine.id) },
        )
    }
}

/** Today when the owner has routines: the chips, then the active routine's machines (or the Phase 1 lists). */
private fun LazyListScope.withRoutines(state: TodayUiState, routines: RoutineUi, actions: TodayActions) {
    item(key = "chips") { RoutineChips(routines, actions.onChooseRoutine) }
    val session = state.session
    val active = routines.activeName
    if (active == null) {
        // Free, or a rest day: the Phase 1 lists, under a "Rest day" heading on a rest day.
        if (session == null) {
            noSession(state.recent, actions, if (routines.restDay) R.string.today_rest_day else R.string.today_pick_to_start)
        } else {
            openSession(session, actions)
        }
        return
    }
    if (session == null) {
        item(key = "title") {
            Text(
                stringResource(R.string.today_next_up, active),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        routineRows(routines, actions)
        item(key = "add") { AddButton(stringResource(R.string.today_add_machine), actions.onAddMachine) }
    } else {
        sessionHeader(session, actions, routineName = active)
        routineRows(routines, actions)
        if (routines.alsoThisSession.isNotEmpty()) {
            item(key = "also") { SectionTitle(stringResource(R.string.today_also_this_session)) }
            sessionRows(routines.alsoThisSession, actions)
        }
        item(key = "add") { AddButton(stringResource(R.string.today_add_machine), actions.onAddMachine) }
    }
}

private fun LazyListScope.routineRows(routines: RoutineUi, actions: TodayActions) {
    items(routines.machines, key = { "rm${it.machine.id}" }) { row ->
        MachineRow(
            title = row.machine.name,
            supporting = targetText(row.target),
            onClick = { actions.onOpenMachine(row.machine.id) },
            highlighted = row.highlighted,
            trailing = {
                when {
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
                    row.setCount > 0 -> Text(
                        stringResource(R.string.today_progress, row.setCount, row.target.targetSets),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/** Routine badges and "Free"; the selected one is green-tinted. */
@Composable
private fun RoutineChips(routines: RoutineUi, onChoose: (RoutineChoice) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(routines.chips, key = { it.id }) { chip ->
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.RadioButton) {
                    onChoose(RoutineChoice.Chosen(chip.id))
                },
                contentAlignment = Alignment.Center,
            ) { RoutineBadge(chip.shortName, selected = chip.selected) }
        }
        item(key = "free") {
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.RadioButton) { onChoose(RoutineChoice.Free) },
                contentAlignment = Alignment.Center,
            ) { RoutineBadge(stringResource(R.string.today_free), selected = routines.freeSelected) }
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

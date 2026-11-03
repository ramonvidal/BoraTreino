package com.boratreino.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.Machine
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.RoutineBadge
import com.boratreino.core.ui.label
import com.boratreino.core.ui.R as CoreUiR

@Composable
internal fun RoutinesScreen(
    onOpenEditor: (machineId: Long?) -> Unit,
    onOpenRoutine: (routineId: Long?) -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RoutinesContent(
        state = state,
        callbacks = RoutinesCallbacks(
            onNewMachine = { onOpenEditor(null) },
            onEditMachine = { onOpenEditor(it) },
            onToggleArchived = viewModel::toggleArchived,
            onToggleStarters = viewModel::toggleStarters,
            // Creates the routine first, then opens its editor: there are no unsaved routines.
            onNewRoutine = { viewModel.createRoutine { id -> onOpenRoutine(id) } },
            onOpenRoutine = { onOpenRoutine(it) },
            onMoveRoutine = viewModel::moveRoutine,
            onDuplicateRoutine = viewModel::duplicateRoutine,
            onDeleteRoutine = viewModel::requestDeleteRoutine,
            onConfirmDeleteRoutine = viewModel::confirmDeleteRoutine,
            onDismissDeleteRoutine = viewModel::dismissDeleteRoutine,
            onScheduleModeChange = viewModel::setScheduleMode,
            onSearchStarters = viewModel::searchStarters,
            onToggleStarter = viewModel::toggleStarter,
            onAddSelectedStarters = viewModel::addSelectedStarters,
            onDeleteMachine = viewModel::deleteMachine,
            onConfirmDelete = viewModel::confirmDelete,
            onArchiveInstead = viewModel::archiveInstead,
            onDismissDelete = viewModel::dismissDelete,
        ),
        modifier = Modifier.fillMaxSize(),
    )
}

/** The two pages of the tab. */
internal enum class RoutinesPage { ROUTINES, GYM }

/**
 * The Routines tab, drawn from its state alone, so screenshot tests can render it. A switch at
 * the top picks the page: Routines (your workouts) or Gym (every machine the gym has). The page
 * starts on Routines once the gym has a machine, on Gym before that; the choice survives
 * rotation. [initialPage] only lets screenshot tests start on a given page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutinesContent(
    state: RoutinesUiState,
    callbacks: RoutinesCallbacks,
    modifier: Modifier = Modifier,
    initialPage: RoutinesPage? = null,
) {
    var chosen by rememberSaveable { mutableStateOf(initialPage?.name) }
    Column(modifier) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
            Text(stringResource(R.string.routines_title), style = MaterialTheme.typography.headlineMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                val page = chosen?.let { RoutinesPage.valueOf(it) }
                    ?: if (state.gymCount > 0) RoutinesPage.ROUTINES else RoutinesPage.GYM
                RoutinesPage.values().forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = page == option,
                        onClick = { chosen = option.name },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = RoutinesPage.values().size),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            stringResource(
                                if (option == RoutinesPage.ROUTINES) R.string.routines_page_routines else R.string.routines_page_gym,
                            ),
                        )
                    }
                }
            }
        }
        if (state.loaded) {
            val page = chosen?.let { RoutinesPage.valueOf(it) }
                ?: if (state.gymCount > 0) RoutinesPage.ROUTINES else RoutinesPage.GYM
            when (page) {
                RoutinesPage.ROUTINES -> RoutinesPageList(state, callbacks, Modifier.weight(1f))
                RoutinesPage.GYM -> GymPageList(state, callbacks, Modifier.weight(1f))
            }
        }
    }

    DeleteMachineDialog(
        dialog = state.deleteDialog,
        onConfirm = callbacks.onConfirmDelete,
        onArchiveInstead = callbacks.onArchiveInstead,
        onDismiss = callbacks.onDismissDelete,
    )
    val routineDelete = state.routineDelete
    if (routineDelete != null) {
        AlertDialog(
            onDismissRequest = callbacks.onDismissDeleteRoutine,
            title = { Text(stringResource(R.string.routine_delete_title)) },
            text = { Text(stringResource(R.string.routine_delete_body)) },
            confirmButton = {
                TextButton(onClick = callbacks.onConfirmDeleteRoutine) {
                    Text(stringResource(R.string.routine_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissDeleteRoutine) { Text(stringResource(CoreUiR.string.cancel)) }
            },
        )
    }
}

/** Routines page: hint, one card per routine, which one is next, and a big New routine button. */
@Composable
private fun RoutinesPageList(state: RoutinesUiState, callbacks: RoutinesCallbacks, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InfoCard(title = null, body = stringResource(R.string.routines_hint))
        }
        if (state.routines.isEmpty()) {
            item {
                InfoCard(
                    title = stringResource(R.string.routines_empty_title),
                    body = stringResource(R.string.routines_empty_body),
                )
            }
        }
        itemsIndexed(state.routines, key = { _, row -> "r-" + row.routine.id }) { index, row ->
            RoutineCard(
                row = row,
                canMoveUp = index > 0,
                canMoveDown = index < state.routines.lastIndex,
                callbacks = callbacks,
            )
        }
        if (state.routines.isNotEmpty()) {
            item { NextRoutineCard(state, callbacks) }
        }
        item {
            Button(
                onClick = callbacks.onNewRoutine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.routines_new_routine), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Gym page: every machine and exercise the gym has, grouped by muscle, plus the starter list. */
@Composable
private fun GymPageList(state: RoutinesUiState, callbacks: RoutinesCallbacks, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { InfoCard(title = stringResource(R.string.routines_intro_title), body = stringResource(R.string.routines_intro_body)) }
        item {
            SectionTitle(stringResource(R.string.routines_gym_heading, state.gymCount))
        }
        items(state.machineGroups, key = { "g-" + it.muscleGroup.name }) { group ->
            GroupCard(title = group.muscleGroup.label()) {
                group.machines.forEachIndexed { index, machine ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MachineRow(
                        machine = machine,
                        onClick = { callbacks.onEditMachine(machine.id) },
                        onDelete = { callbacks.onDeleteMachine(machine) },
                    )
                }
            }
        }
        if (state.starterGroups.isNotEmpty() || state.startersSearch.isNotBlank()) {
            item { StartersCard(state, callbacks) }
        }
        item {
            Button(
                onClick = callbacks.onNewMachine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.routines_new_machine), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.archived.isNotEmpty()) {
            item {
                FoldHeader(
                    title = stringResource(R.string.routines_archived) + " (${state.archived.size})",
                    expanded = state.archivedExpanded,
                    onClick = callbacks.onToggleArchived,
                )
            }
            if (state.archivedExpanded) {
                item {
                    GroupCard(title = null) {
                        state.archived.forEachIndexed { index, machine ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            MachineRow(
                                machine = machine,
                                onClick = { callbacks.onEditMachine(machine.id) },
                                onDelete = { callbacks.onDeleteMachine(machine) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A grey card with a short explanation, with an optional bold title. */
@Composable
private fun InfoCard(title: String?, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Add from the list": folded card with search, checkbox rows and "Add N selected". */
@Composable
private fun StartersCard(state: RoutinesUiState, callbacks: RoutinesCallbacks) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FoldHeader(
                title = stringResource(R.string.routines_add_from_list),
                expanded = state.startersExpanded,
                onClick = callbacks.onToggleStarters,
            )
            if (state.startersExpanded) {
                OutlinedTextField(
                    value = state.startersSearch,
                    onValueChange = callbacks.onSearchStarters,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.routines_search)) },
                    singleLine = true,
                )
                if (state.starterGroups.isEmpty()) {
                    Text(
                        stringResource(R.string.routines_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                state.starterGroups.forEach { group ->
                    Text(
                        group.muscleGroup.label(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    group.starters.forEach { starter ->
                        StarterRow(
                            starter = starter,
                            name = starter.displayName(state.portuguese),
                            checked = starter.name in state.selectedStarters,
                            onClick = { callbacks.onToggleStarter(starter) },
                        )
                    }
                }
                val count = state.selectedStarters.size
                if (count > 0) {
                    val label = LocalContext.current.resources
                        .getQuantityString(R.plurals.routines_add_selected, count, count)
                    Button(
                        onClick = callbacks.onAddSelectedStarters,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(label) }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun StarterRow(starter: StarterMachine, name: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
        // The picture is looked up by the English name, whichever name is shown.
        MachineThumb(name = starter.name, muscleGroup = starter.muscleGroup, size = 40.dp)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

/** One routine: badge, name, machine count and the first machine names; tap opens it. */
@Composable
private fun RoutineCard(row: RoutineRow, canMoveUp: Boolean, canMoveDown: Boolean, callbacks: RoutinesCallbacks) {
    val count = LocalContext.current.resources
        .getQuantityString(R.plurals.routines_machine_count, row.machineCount, row.machineCount)
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = { callbacks.onOpenRoutine(row.routine.id) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoutineBadge(shortName = row.routine.shortName)
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(row.routine.name, style = MaterialTheme.typography.titleMedium)
                Text(count, style = MaterialTheme.typography.bodyMedium)
                if (row.machineNames.isNotEmpty()) {
                    Text(
                        row.machineNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.routines_menu, row.routine.name),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routines_menu_edit)) },
                        onClick = { menuOpen = false; callbacks.onOpenRoutine(row.routine.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routines_menu_move_up)) },
                        enabled = canMoveUp,
                        onClick = { menuOpen = false; callbacks.onMoveRoutine(row.routine.id, true) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routines_menu_move_down)) },
                        enabled = canMoveDown,
                        onClick = { menuOpen = false; callbacks.onMoveRoutine(row.routine.id, false) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routines_menu_duplicate)) },
                        onClick = { menuOpen = false; callbacks.onDuplicateRoutine(row.routine.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routines_menu_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; callbacks.onDeleteRoutine(row.routine.id) },
                    )
                }
            }
        }
    }
}

/** "Which routine is next": In turn / On weekdays. */
@Composable
private fun NextRoutineCard(state: RoutinesUiState, callbacks: RoutinesCallbacks) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.routines_next_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.scheduleMode == ScheduleMode.IN_TURN,
                    onClick = { callbacks.onScheduleModeChange(ScheduleMode.IN_TURN) },
                    label = { Text(stringResource(R.string.routines_mode_in_turn)) },
                )
                FilterChip(
                    selected = state.scheduleMode == ScheduleMode.WEEKDAYS,
                    onClick = { callbacks.onScheduleModeChange(ScheduleMode.WEEKDAYS) },
                    label = { Text(stringResource(R.string.routines_mode_weekdays)) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

/** A white card with an optional muscle group heading and its rows inside. */
@Composable
private fun GroupCard(title: String?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun FoldHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(if (expanded) CoreUiR.drawable.ic_expand_less else CoreUiR.drawable.ic_expand_more),
            contentDescription = stringResource(if (expanded) R.string.routines_collapse else R.string.routines_expand),
        )
    }
}

@Composable
private fun MachineRow(machine: Machine, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).clickable(onClick = onClick)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MachineThumb(machine = machine, size = 40.dp)
            Column(verticalArrangement = Arrangement.Center) {
                Text(machine.name, style = MaterialTheme.typography.bodyLarge)
                val note = machine.setupNote
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_delete),
                contentDescription = stringResource(R.string.routines_delete_machine, machine.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

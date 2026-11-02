package com.boratreino.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.boratreino.core.model.Machine
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.ui.RoutineBadge
import com.boratreino.core.model.StarterMachine
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
            onAddStarter = viewModel::addStarter,
            onToggleArchived = viewModel::toggleArchived,
            onToggleStarters = viewModel::toggleStarters,
            onNewRoutine = { onOpenRoutine(null) },
            onOpenRoutine = { onOpenRoutine(it) },
            onMoveRoutine = viewModel::moveRoutine,
            onScheduleModeChange = viewModel::setScheduleMode,
        ),
        modifier = Modifier.fillMaxSize(),
    )
}

/** The Routines tab, drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun RoutinesContent(
    state: RoutinesUiState,
    callbacks: RoutinesCallbacks,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.routines_title), style = MaterialTheme.typography.headlineMedium)
        }
        item { RoutinesSection(state, callbacks) }
        item {
            SectionTitle(stringResource(R.string.routines_machines))
        }
        if (state.isEmpty) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.routines_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.routines_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(state.machineGroups, key = { "g-" + it.muscleGroup.name }) { group ->
            GroupCard(title = group.muscleGroup.label()) {
                group.machines.forEachIndexed { index, machine ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MachineRow(machine, onClick = { callbacks.onEditMachine(machine.id) })
                }
            }
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
                            MachineRow(machine, onClick = { callbacks.onEditMachine(machine.id) })
                        }
                    }
                }
            }
        }
        if (state.starterGroups.isNotEmpty()) {
            item {
                FoldHeader(
                    title = stringResource(R.string.routines_add_from_list),
                    expanded = state.startersExpanded,
                    onClick = callbacks.onToggleStarters,
                )
            }
            if (state.startersExpanded) {
                items(state.starterGroups, key = { "s-" + it.muscleGroup.name }) { group ->
                    GroupCard(title = group.muscleGroup.label()) {
                        group.starters.forEachIndexed { index, starter ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            StarterRow(starter, onClick = { callbacks.onAddStarter(starter) })
                        }
                    }
                }
            }
        }
    }
}

/** Routines: one row each with up / down, "New routine", and the In turn / On weekdays switch. */
@Composable
private fun RoutinesSection(state: RoutinesUiState, callbacks: RoutinesCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.routines.isNotEmpty()) {
            GroupCard(title = null) {
                state.routines.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RoutineRowItem(
                        row = row,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.routines.lastIndex,
                        callbacks = callbacks,
                    )
                }
            }
        }
        OutlinedButton(
            onClick = callbacks.onNewRoutine,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.routines_new_routine), modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            stringResource(R.string.routines_next_routine),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun RoutineRowItem(row: RoutineRow, canMoveUp: Boolean, canMoveDown: Boolean, callbacks: RoutinesCallbacks) {
    val count = LocalContext.current.resources
        .getQuantityString(R.plurals.routines_machine_count, row.machineCount, row.machineCount)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
            .clickable { callbacks.onOpenRoutine(row.routine.id) }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoutineBadge(shortName = row.routine.shortName)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(row.routine.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { callbacks.onMoveRoutine(row.routine.id, true) }, enabled = canMoveUp) {
            Icon(
                painterResource(CoreUiR.drawable.ic_arrow_upward),
                contentDescription = stringResource(R.string.routines_move_up, row.routine.name),
            )
        }
        IconButton(onClick = { callbacks.onMoveRoutine(row.routine.id, false) }, enabled = canMoveDown) {
            Icon(
                painterResource(CoreUiR.drawable.ic_arrow_downward),
                contentDescription = stringResource(R.string.routines_move_down, row.routine.name),
            )
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
private fun MachineRow(machine: Machine, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
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

@Composable
private fun StarterRow(starter: StarterMachine, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            starter.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(CoreUiR.drawable.ic_add),
            contentDescription = stringResource(R.string.routines_add_starter, starter.name),
            modifier = Modifier.size(20.dp),
        )
    }
}

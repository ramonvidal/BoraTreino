package com.boratreino.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.Machine
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraButtonStyle
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.RoutineBadge
import com.boratreino.core.ui.label
import com.boratreino.core.ui.R as CoreUiR

@Composable
internal fun RoutinesScreen(
    onOpenEditor: (machineId: Long?) -> Unit,
    onOpenRoutine: (routineId: Long?) -> Unit,
    onOpenLibrary: () -> Unit = {},
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
            onOpenLibrary = onOpenLibrary,
            // Creates the routine first, then opens its editor: there are no unsaved routines.
            onNewRoutine = { viewModel.createRoutine { id -> onOpenRoutine(id) } },
            onOpenRoutine = { onOpenRoutine(it) },
            onMoveRoutine = viewModel::moveRoutine,
            onDuplicateRoutine = viewModel::duplicateRoutine,
            onDeleteRoutine = viewModel::requestDeleteRoutine,
            onConfirmDeleteRoutine = viewModel::confirmDeleteRoutine,
            onDismissDeleteRoutine = viewModel::dismissDeleteRoutine,
            onScheduleModeChange = viewModel::setScheduleMode,
            onTrainingDaysChange = viewModel::setTrainingDays,
            onSearchStarters = viewModel::searchStarters,
            onToggleStarter = viewModel::toggleStarter,
            onToggleLibrary = viewModel::toggleLibrary,
            onGroupFilter = viewModel::setGroupFilter,
            onEquipmentFilter = viewModel::setEquipmentFilter,
            onLevelFilter = viewModel::setLevelFilter,
            onClearFilters = viewModel::clearFilters,
            onShowDetail = viewModel::showDetail,
            onDismissDetail = viewModel::dismissDetail,
            onAddDetail = viewModel::addDetail,
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
@Composable
internal fun RoutinesContent(
    state: RoutinesUiState,
    callbacks: RoutinesCallbacks,
    modifier: Modifier = Modifier,
    initialPage: RoutinesPage? = null,
) {
    var chosen by rememberSaveable { mutableStateOf(initialPage?.name) }
    val page = chosen?.let { RoutinesPage.valueOf(it) }
        ?: if (state.gymCount > 0) RoutinesPage.ROUTINES else RoutinesPage.GYM
    // This tab has no Scaffold of its own, so it keeps itself clear of the status bar.
    Column(modifier.statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
            Text(stringResource(R.string.routines_title), style = MaterialTheme.typography.headlineMedium)
            if (page == RoutinesPage.ROUTINES) {
                Text(
                    stringResource(R.string.routines_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutinesPage.values().forEach { option ->
                    BoraChip(
                        label = stringResource(
                            if (option == RoutinesPage.ROUTINES) R.string.routines_page_routines else R.string.routines_page_gym,
                        ),
                        selected = page == option,
                        onClick = { chosen = option.name },
                        large = true,
                    )
                }
            }
        }
        if (state.loaded) {
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

/** Routines page: one card per routine and a big New routine button. */
@Composable
private fun RoutinesPageList(state: RoutinesUiState, callbacks: RoutinesCallbacks, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        item {
            BoraButton(
                text = stringResource(R.string.routines_new_routine),
                onClick = callbacks.onNewRoutine,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = CoreUiR.drawable.ic_add,
            )
        }
    }
}

/** Academia page: every exercise the gym has, grouped by muscle, and the buttons that add more. */
@Composable
private fun GymPageList(state: RoutinesUiState, callbacks: RoutinesCallbacks, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "heading") {
            Text(
                stringResource(R.string.routines_gym_heading, state.gymCount),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.pickerAvailable) {
            item(key = "add-exercises") {
                BoraButton(
                    text = stringResource(R.string.routines_add_exercises),
                    onClick = callbacks.onOpenLibrary,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = CoreUiR.drawable.ic_add,
                )
            }
        }
        item(key = "new-machine") {
            BoraButton(
                text = stringResource(R.string.routines_new_machine),
                onClick = callbacks.onNewMachine,
                modifier = Modifier.fillMaxWidth(),
                style = BoraButtonStyle.Tonal,
            )
        }
        items(state.machineGroups, key = { "g-" + it.muscleGroup.name }) { group ->
            GroupCard(title = group.muscleGroup.label()) {
                group.machines.forEachIndexed { index, machine ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MachineRow(machine = machine, onClick = { callbacks.onEditMachine(machine.id) })
                }
            }
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-header") {
                FoldHeader(
                    title = stringResource(R.string.routines_archived) + " (${state.archived.size})",
                    expanded = state.archivedExpanded,
                    onClick = callbacks.onToggleArchived,
                )
            }
            if (state.archivedExpanded) {
                item(key = "archived") {
                    GroupCard(title = null) {
                        state.archived.forEachIndexed { index, machine ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            MachineRow(machine = machine, onClick = { callbacks.onEditMachine(machine.id) })
                        }
                    }
                }
            }
        }
    }
}

/** A white card with a short explanation, with an optional bold title. */
@Composable
private fun InfoCard(title: String?, body: String) {
    BoraCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One routine: badge, name, its muscle groups as small pills and the exercise count; tap opens it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineCard(row: RoutineRow, canMoveUp: Boolean, canMoveDown: Boolean, callbacks: RoutinesCallbacks) {
    val count = LocalContext.current.resources
        .getQuantityString(R.plurals.routines_machine_count, row.machineCount, row.machineCount)
    val lastTrained = row.lastTrainedDaysAgo?.let { days ->
        when (days) {
            0 -> stringResource(R.string.routines_last_trained_today)
            1 -> stringResource(R.string.routines_last_trained_yesterday)
            else -> pluralStringResource(R.plurals.routines_last_trained_days, days, days)
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    BoraCard(
        onClick = { callbacks.onOpenRoutine(row.routine.id) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoutineBadge(shortName = row.routine.shortName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        row.routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.isNext) NextPill()
                }
                if (lastTrained != null) {
                    Text(
                        lastTrained,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.muscleGroups.take(MAX_GROUP_PILLS).forEach { group -> GroupPill(group.label()) }
                    Text(
                        count,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private const val MAX_GROUP_PILLS = 4

/** The small yellow "Next" pill beside the name of the routine due today. */
@Composable
private fun NextPill() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            stringResource(R.string.routines_next_pill),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** A small, plain, not clickable pill with a muscle group's name. */
@Composable
private fun GroupPill(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** A white card with an optional muscle group heading and its rows inside. */
@Composable
private fun GroupCard(title: String?, content: @Composable () -> Unit) {
    BoraCard(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        content()
    }
}

@Composable
internal fun FoldHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
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

/** One exercise of the gym: picture, name and setup note; the whole row opens the editor. */
@Composable
private fun MachineRow(machine: Machine, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
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
}

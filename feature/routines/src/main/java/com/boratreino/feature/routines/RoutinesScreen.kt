package com.boratreino.feature.routines

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.MuscleGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.RoutineBadge
import com.boratreino.core.ui.shortLabel
import java.time.DayOfWeek
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
    ExerciseDetailDialog(
        detail = state.detail,
        portuguese = state.portuguese,
        onAdd = callbacks.onAddDetail,
        onDismiss = callbacks.onDismissDetail,
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

/** Gym page: every machine and exercise the gym has, grouped by muscle, plus the exercise picker. */
@Composable
private fun GymPageList(state: RoutinesUiState, callbacks: RoutinesCallbacks, modifier: Modifier = Modifier) {
    // No spacedBy: the picker's rows must sit flush, so the other items add their own gap.
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        spaced("intro") { InfoCard(title = stringResource(R.string.routines_intro_title), body = stringResource(R.string.routines_intro_body)) }
        spaced("heading") {
            SectionTitle(stringResource(R.string.routines_gym_heading, state.gymCount))
        }
        items(state.machineGroups, key = { "g-" + it.muscleGroup.name }) { group ->
            Box(Modifier.padding(bottom = 12.dp)) {
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
        }
        if (state.pickerAvailable) {
            pickerItems(state, callbacks)
        }
        spaced("new-machine") {
            Button(
                onClick = callbacks.onNewMachine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.routines_new_machine), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.archived.isNotEmpty()) {
            spaced("archived-header") {
                FoldHeader(
                    title = stringResource(R.string.routines_archived) + " (${state.archived.size})",
                    expanded = state.archivedExpanded,
                    onClick = callbacks.onToggleArchived,
                )
            }
            if (state.archivedExpanded) {
                spaced("archived") {
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

/** One item with the usual 12 dp gap below it. */
private fun LazyListScope.spaced(key: Any, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.padding(bottom = 12.dp)) { content() } }
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

private val PickerTopShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
private val PickerBottomShape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

/**
 * The exercise picker, as flat items of the Gym list so only the visible rows are composed:
 * a folded header (search and filters inside when open), then the rows by muscle group.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.pickerItems(state: RoutinesUiState, callbacks: RoutinesCallbacks) {
    val fill = Modifier.fillMaxWidth()
    item(key = "picker-header") {
        PickerHeader(state, callbacks, Modifier.padding(bottom = if (state.startersExpanded) 0.dp else 12.dp))
    }
    if (!state.startersExpanded) return
    if (state.selectedCount > 0) {
        stickyHeader(key = "picker-add") {
            val label = LocalContext.current.resources
                .getQuantityString(R.plurals.routines_add_selected, state.selectedCount, state.selectedCount)
            Box(fill.background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 4.dp)) {
                Button(
                    onClick = callbacks.onAddSelectedStarters,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(label) }
            }
        }
    }
    if (state.pickerGroups.isEmpty()) {
        item(key = "picker-empty") {
            Text(
                stringResource(R.string.routines_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = fill.background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            )
        }
    }
    state.pickerGroups.forEach { group ->
        item(key = "ph-" + group.muscleGroup.name) {
            Text(
                group.muscleGroup.label(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = fill.background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
        }
        items(group.rows, key = { it.key }) { row ->
            PickerRowItem(
                row = row,
                checked = if (row.starter != null) row.starter.name in state.selectedStarters else row.libraryId in state.selectedLibrary,
                onToggle = {
                    if (row.starter != null) callbacks.onToggleStarter(row.starter) else row.libraryId?.let(callbacks.onToggleLibrary)
                },
                onInfo = { callbacks.onShowDetail(row) },
            )
        }
    }
    item(key = "picker-end") {
        Box(Modifier.padding(bottom = 12.dp)) {
            Box(
                Modifier.fillMaxWidth().height(12.dp).clip(PickerBottomShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
}

/** "Add from the list": the fold header and, when open, search, filters and the count. */
@Composable
private fun PickerHeader(state: RoutinesUiState, callbacks: RoutinesCallbacks, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = if (state.startersExpanded) PickerTopShape else CardDefaults.shape,
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
                    value = state.filter.query,
                    onValueChange = callbacks.onSearchStarters,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.routines_search)) },
                    singleLine = true,
                )
                FilterChipRow(
                    title = stringResource(R.string.library_filter_group),
                    options = MuscleGroup.entries,
                    selected = state.filter.group,
                    onSelect = callbacks.onGroupFilter,
                ) { it.label() }
                FilterChipRow(
                    title = stringResource(R.string.library_filter_equipment),
                    options = LibraryEquipment.entries,
                    selected = state.filter.equipment,
                    onSelect = callbacks.onEquipmentFilter,
                ) { it.label() }
                FilterChipRow(
                    title = stringResource(R.string.library_filter_level),
                    options = LibraryLevel.entries,
                    selected = state.filter.level,
                    onSelect = callbacks.onLevelFilter,
                ) { it.label() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val count = LocalContext.current.resources
                        .getQuantityString(R.plurals.library_count, state.pickerCount, state.pickerCount)
                    Text(
                        count,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.filter.group != null || state.filter.equipment != null || state.filter.level != null) {
                        TextButton(onClick = callbacks.onClearFilters, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.library_filter_clear))
                        }
                    }
                }
            }
        }
    }
}

/** A titled, sideways-scrolling row of chips: "All" plus one per option; tapping the chosen one clears it. */
@Composable
private fun <T> FilterChipRow(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    optionLabel: @Composable (T) -> String,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoraChip(
                label = stringResource(R.string.library_filter_all),
                selected = selected == null,
                onClick = { onSelect(null) },
            )
            options.forEach { option ->
                BoraChip(
                    label = optionLabel(option),
                    selected = option == selected,
                    onClick = { onSelect(if (option == selected) null else option) },
                )
            }
        }
    }
}

/** One exercise: the row toggles it (one tap); the info button opens "How to do it". Added ones are marked. */
@Composable
private fun PickerRowItem(row: PickerRow, checked: Boolean, onToggle: () -> Unit, onInfo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).defaultMinSize(minHeight = 56.dp)
                .clickable(enabled = !row.added, onClick = onToggle)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.added) {
                Box(Modifier.size(48.dp).padding(end = 8.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
            }
            val starter = row.starter
            // The picture is looked up by the English name, whichever name is shown. Library-only entries have none.
            if (starter != null) {
                MachineThumb(name = starter.name, muscleGroup = starter.muscleGroup, size = 40.dp)
            }
            Column(Modifier.weight(1f).padding(start = if (starter != null) 12.dp else 0.dp, top = 4.dp, bottom = 4.dp)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (row.added) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = if (row.added) stringResource(R.string.library_added) else row.equipment?.label()
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (row.libraryId != null) {
            IconButton(onClick = onInfo) {
                Icon(
                    painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.library_info, row.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

/** "Which routine is next": In turn / On weekdays; in turn also asks for the training days. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextRoutineCard(state: RoutinesUiState, callbacks: RoutinesCallbacks) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.routines_next_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoraChip(
                    label = stringResource(R.string.routines_mode_in_turn),
                    selected = state.scheduleMode == ScheduleMode.IN_TURN,
                    onClick = { callbacks.onScheduleModeChange(ScheduleMode.IN_TURN) },
                )
                BoraChip(
                    label = stringResource(R.string.routines_mode_weekdays),
                    selected = state.scheduleMode == ScheduleMode.WEEKDAYS,
                    onClick = { callbacks.onScheduleModeChange(ScheduleMode.WEEKDAYS) },
                )
            }
            if (state.scheduleMode == ScheduleMode.IN_TURN) {
                Text(
                    stringResource(R.string.routines_training_days),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.values().forEach { day ->
                        BoraChip(
                            label = day.shortLabel(),
                            selected = day in state.trainingDays,
                            onClick = {
                                val next = if (day in state.trainingDays) state.trainingDays - day else state.trainingDays + day
                                callbacks.onTrainingDaysChange(next)
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.routines_training_days_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

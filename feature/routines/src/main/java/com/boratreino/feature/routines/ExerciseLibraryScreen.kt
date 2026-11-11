package com.boratreino.feature.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraButtonStyle
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.ThumbTile
import com.boratreino.core.ui.label
import com.boratreino.core.ui.R as CoreUiR

/** The full-screen exercise library; [onDone] closes it (back arrow, or after "Add N"). */
@Composable
internal fun ExerciseLibraryScreen(
    onDone: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The exercise list is only worked out while the library is open.
    DisposableEffect(viewModel) {
        viewModel.setLibraryOpen(true)
        onDispose { viewModel.setLibraryOpen(false) }
    }
    ExerciseLibraryContent(
        state = state,
        callbacks = RoutinesCallbacks(
            onBack = onDone,
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
            // Written first (the view model launches the insert), then back to the list.
            onAddSelectedStarters = {
                viewModel.addSelectedStarters()
                onDone()
            },
        ),
        modifier = Modifier.fillMaxSize(),
    )
}

/** One line of the results list: a muscle group heading or an exercise. */
private sealed interface LibraryLine {
    val key: String

    data class Header(val group: MuscleGroup) : LibraryLine {
        override val key: String get() = "h-" + group.name
    }

    data class Item(val row: PickerRow) : LibraryLine {
        override val key: String get() = row.key
    }
}

/**
 * The library, drawn from its state alone: back and title, search, three dropdown filters, the
 * count, the results in one card grouped by muscle group, and "Add N" stuck to the bottom while
 * something is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseLibraryContent(
    state: RoutinesUiState,
    callbacks: RoutinesCallbacks,
    modifier: Modifier = Modifier,
) {
    val lines = remember(state.pickerGroups) {
        state.pickerGroups.flatMap { group -> listOf<LibraryLine>(LibraryLine.Header(group.muscleGroup)) + group.rows.map { LibraryLine.Item(it) } }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routines_add_exercises)) },
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
        bottomBar = {
            if (state.selectedCount > 0) {
                val label = LocalContext.current.resources
                    .getQuantityString(R.plurals.routines_add_selected, state.selectedCount, state.selectedCount)
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    BoraButton(text = label, onClick = callbacks.onAddSelectedStarters, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = callbacks.onSearchStarters,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.routines_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterDropdownChip(
                        title = stringResource(R.string.library_filter_group),
                        options = MuscleGroup.entries,
                        selected = state.filter.group,
                        onSelect = callbacks.onGroupFilter,
                    ) { it.label() }
                    FilterDropdownChip(
                        title = stringResource(R.string.library_filter_equipment),
                        options = LibraryEquipment.entries,
                        selected = state.filter.equipment,
                        onSelect = callbacks.onEquipmentFilter,
                    ) { it.label() }
                    FilterDropdownChip(
                        title = stringResource(R.string.library_filter_level),
                        options = LibraryLevel.entries,
                        selected = state.filter.level,
                        onSelect = callbacks.onLevelFilter,
                    ) { it.label() }
                }
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
                        BoraButton(
                            text = stringResource(R.string.library_filter_clear),
                            onClick = callbacks.onClearFilters,
                            // The button fills the width it is given, so give it a fixed one and keep the count's.
                            modifier = Modifier.width(100.dp),
                            style = BoraButtonStyle.Text,
                        )
                    }
                }
            }
            if (lines.isEmpty()) {
                Text(
                    stringResource(R.string.routines_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    items(lines.size, key = { lines[it].key }) { index ->
                        val line = lines[index]
                        val big = MaterialTheme.shapes.large
                        val none = ZeroCornerSize
                        val shape = big.copy(
                            topStart = if (index == 0) big.topStart else none,
                            topEnd = if (index == 0) big.topEnd else none,
                            bottomStart = if (index == lines.lastIndex) big.bottomStart else none,
                            bottomEnd = if (index == lines.lastIndex) big.bottomEnd else none,
                        )
                        Column(
                            Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            when (line) {
                                is LibraryLine.Header -> Text(
                                    line.group.label(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
                                )
                                is LibraryLine.Item -> {
                                    // Hairline between rows, not right under a heading.
                                    if (lines[index - 1] is LibraryLine.Item) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                    LibraryRow(
                                        row = line.row,
                                        checked = if (line.row.starter != null) {
                                            line.row.starter.name in state.selectedStarters
                                        } else {
                                            line.row.libraryId in state.selectedLibrary
                                        },
                                        onToggle = {
                                            val starter = line.row.starter
                                            if (starter != null) {
                                                callbacks.onToggleStarter(starter)
                                            } else {
                                                line.row.libraryId?.let(callbacks.onToggleLibrary)
                                            }
                                        },
                                        onInfo = { callbacks.onShowDetail(line.row) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ExerciseDetailDialog(
        detail = state.detail,
        portuguese = state.portuguese,
        onAdd = callbacks.onAddDetail,
        onDismiss = callbacks.onDismissDetail,
    )
}

/** A compact chip that shows the chosen value (yellow) or the [title], and opens "All" plus the options. */
@Composable
private fun <T> FilterDropdownChip(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    optionLabel: @Composable (T) -> String,
) {
    var open by remember { mutableStateOf(false) }
    val chosen = if (selected != null) optionLabel(selected) else null
    Box {
        BoraChip(label = chosen ?: title, selected = selected != null, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_filter_all)) },
                onClick = { open = false; onSelect(null) },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}

/** One exercise: the row toggles it (one tap); the info button opens "How to do it". Added ones are marked and disabled. */
@Composable
private fun LibraryRow(row: PickerRow, checked: Boolean, onToggle: () -> Unit, onInfo: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).defaultMinSize(minHeight = 56.dp)
                .clickable(enabled = !row.added, onClick = onToggle)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val starter = row.starter
            // The picture is looked up by the English name, whichever name is shown. Library-only entries use the muscle tile.
            if (starter != null) {
                MachineThumb(name = starter.name, muscleGroup = starter.muscleGroup, size = 40.dp)
            } else {
                ThumbTile(imageSlug = null, muscleGroup = row.group, size = 40.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (row.added) muted else MaterialTheme.colorScheme.onSurface,
                )
                val details = listOfNotNull(row.equipment?.label(), row.level?.label()).joinToString(" · ")
                val subtitle = if (row.added) stringResource(R.string.library_added) else details
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            if (row.added) {
                Icon(painterResource(CoreUiR.drawable.ic_check), contentDescription = null, tint = muted)
            } else {
                CheckCircle(checked)
            }
        }
        if (row.libraryId != null) {
            IconButton(onClick = onInfo) {
                Icon(
                    painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.library_info, row.name),
                    tint = muted,
                )
            }
        } else {
            Box(Modifier.size(16.dp))
        }
    }
}

/** The selection mark: a yellow circle with a check when selected, an empty outlined circle otherwise. */
@Composable
private fun CheckCircle(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .then(if (checked) Modifier.background(scheme.primaryContainer) else Modifier.border(2.dp, scheme.outline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(CoreUiR.drawable.ic_check),
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

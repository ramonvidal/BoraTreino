package com.boratreino.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.ui.label
import kotlinx.coroutines.launch
import com.boratreino.core.ui.R as CoreUiR

internal data class PickerActions(
    val onBack: () -> Unit = {},
    val onOpenMachine: (machineId: Long) -> Unit = {},
    val onAddStarter: (StarterMachine) -> Unit = {},
    val onToggleMore: () -> Unit = {},
    val onNewMachine: () -> Unit = {},
    val onFichaAdd: () -> Unit = {},
    val onFichaJustToday: () -> Unit = {},
)

@Composable
internal fun PickerScreen(
    onBack: () -> Unit,
    onOpenMachine: (Long) -> Unit,
    onNewMachine: () -> Unit,
    viewModel: PickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    PickerContent(
        state = state,
        actions = PickerActions(
            onBack = onBack,
            onOpenMachine = { id -> scope.launch { if (!viewModel.requestOpen(id)) onOpenMachine(id) } },
            // Added first, then opened: one tap from the list to the machine screen.
            onAddStarter = { starter ->
                scope.launch {
                    val id = viewModel.addStarter(starter)
                    if (!viewModel.requestOpen(id)) onOpenMachine(id)
                }
            },
            onFichaAdd = { scope.launch { viewModel.answerPrompt(addToFicha = true)?.let(onOpenMachine) } },
            onFichaJustToday = { scope.launch { viewModel.answerPrompt(addToFicha = false)?.let(onOpenMachine) } },
            onToggleMore = viewModel::toggleMore,
            onNewMachine = onNewMachine,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerContent(
    state: PickerUiState,
    actions: PickerActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(CoreUiR.drawable.ic_back), stringResource(CoreUiR.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recentSection(state, actions)
            for (group in state.groups) {
                item(key = "gh-${group.group}") { SectionTitle(group.group.label()) }
                items(group.machines, key = { "m${it.id}" }) { machine ->
                    MachineRow(machine.name, machine.setupNote, onClick = { actions.onOpenMachine(machine.id) })
                }
            }
            moreSection(state, actions)
            item(key = "new") {
                AddButton(stringResource(R.string.today_new_machine), actions.onNewMachine)
            }
        }
    }

    state.fichaPrompt?.let { FichaPromptDialog(it, actions) }
}

private fun LazyListScope.recentSection(state: PickerUiState, actions: PickerActions) {
    if (state.recent.isEmpty()) return
    item(key = "recent-h") { SectionTitle(stringResource(R.string.today_recent)) }
    items(state.recent, key = { "r${it.id}" }) { machine ->
        MachineRow(machine.name, machine.muscleGroup.label(), onClick = { actions.onOpenMachine(machine.id) })
    }
}

private fun LazyListScope.moreSection(state: PickerUiState, actions: PickerActions) {
    if (state.starterGroups.isEmpty()) return
    item(key = "more-h") {
        MachineRow(
            title = stringResource(R.string.today_more_machines),
            supporting = null,
            onClick = actions.onToggleMore,
            trailing = {
                val icon = if (state.moreExpanded) CoreUiR.drawable.ic_expand_less else CoreUiR.drawable.ic_expand_more
                Icon(painterResource(icon), contentDescription = null)
            },
        )
    }
    if (!state.moreExpanded) return
    for (group in state.starterGroups) {
        item(key = "sh-${group.group}") { SectionTitle(group.group.label()) }
        items(group.starters, key = { "s${it.name}" }) { starter ->
            MachineRow(starter.displayName(state.portuguese), null, onClick = { actions.onAddStarter(starter) })
        }
    }
}

/** "Add Leg curl to Treino B?" Dismissing counts as Just today. */
@Composable
private fun FichaPromptDialog(prompt: FichaPrompt, actions: PickerActions) {
    AlertDialog(
        onDismissRequest = actions.onFichaJustToday,
        title = { Text(stringResource(R.string.today_ficha_add_title, prompt.machine.name, prompt.routineName)) },
        text = { Text(stringResource(R.string.today_ficha_add_text)) },
        confirmButton = {
            TextButton(onClick = actions.onFichaAdd) { Text(stringResource(R.string.today_ficha_add)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onFichaJustToday) { Text(stringResource(R.string.today_ficha_just_today)) }
        },
    )
}

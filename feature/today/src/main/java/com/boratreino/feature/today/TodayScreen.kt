package com.boratreino.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
                        resources.machinesAndSets(event.summary.machineCount, event.summary.setCount),
                    )
                    if (snackbar.showUndo(text, undoLabel)) viewModel.reopen(event.summary.sessionId)
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
            if (session == null) {
                noSession(state.recent, actions)
            } else {
                openSession(session, actions)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.noSession(recent: List<Machine>, actions: TodayActions) {
    item(key = "title") {
        Text(
            stringResource(R.string.today_pick_to_start),
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

private fun androidx.compose.foundation.lazy.LazyListScope.openSession(session: SessionUi, actions: TodayActions) {
    item(key = "header") {
        val resources = LocalContext.current.resources
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.today_training_now,
                        resources.machinesAndSets(session.machineCount, session.setCount),
                    ),
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
    items(session.machines, key = { "m${it.machine.id}" }) { item ->
        MachineRow(
            title = item.machine.name,
            supporting = item.summary?.let { summaryLabel(item.machine, it) },
            onClick = { actions.onOpenMachine(item.machine.id) },
        )
    }
    item(key = "add") {
        AddButton(stringResource(R.string.today_add_machine), actions.onAddMachine)
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

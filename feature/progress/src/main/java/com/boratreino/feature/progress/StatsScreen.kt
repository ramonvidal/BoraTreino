package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun StatsScreen(
    onOpenMachine: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onOpenMachine) {
        StatsActions(onPeriod = viewModel::setPeriod, onOpenMachine = onOpenMachine)
    }
    StatsContent(state = state, actions = actions, modifier = modifier)
}

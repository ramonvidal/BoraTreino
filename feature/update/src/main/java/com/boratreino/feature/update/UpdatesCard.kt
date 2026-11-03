package com.boratreino.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal data class UpdatesCardActions(
    val onCheckForUpdates: () -> Unit = {},
)

/**
 * The "App updates" card: installed version, "Check for updates" and what the check found.
 * Public entry point (with [UpdateBanner]); `:app` hands it to Settings as a slot.
 */
@Composable
fun UpdatesCard(modifier: Modifier = Modifier) {
    val viewModel: UpdatesCardViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    UpdatesCardContent(
        state = state,
        actions = UpdatesCardActions(onCheckForUpdates = viewModel::checkForUpdates),
        modifier = modifier,
    )
}

/** Drawn from its arguments alone, so screenshot tests can render it. */
@Composable
internal fun UpdatesCardContent(
    state: UpdatesCardUiState = UpdatesCardUiState(),
    actions: UpdatesCardActions = UpdatesCardActions(),
    modifier: Modifier = Modifier,
) {
    val status = when (val s = state.status) {
        UpdatesCardStatus.Idle, UpdatesCardStatus.Checking -> null
        UpdatesCardStatus.UpToDate -> stringResource(R.string.updates_card_up_to_date)
        is UpdatesCardStatus.Available -> stringResource(R.string.updates_card_available, s.version)
        UpdatesCardStatus.Unreachable -> stringResource(R.string.updates_card_unreachable)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.updates_card_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.updates_card_version, state.installedVersion),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(
                onClick = actions.onCheckForUpdates,
                enabled = state.status != UpdatesCardStatus.Checking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.updates_card_check)) }
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

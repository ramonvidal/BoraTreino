package com.boratreino.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Which banner [UpdateBannerContent] draws. */
internal enum class UpdateBannerKind { IDLE, AVAILABLE, DOWNLOADING, NEEDS_PERMISSION, FAILED }

/**
 * What the banner shows. [versionName] (e.g. "0.41") is used by [UpdateBannerKind.AVAILABLE];
 * [progress] (0 to 1) by [UpdateBannerKind.DOWNLOADING].
 */
internal data class UpdateUiState(
    val kind: UpdateBannerKind = UpdateBannerKind.IDLE,
    val versionName: String = "",
    val progress: Float = 0f,
)

internal data class UpdateActions(
    val onInstall: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onAllow: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

internal fun UpdateState.toUiState(): UpdateUiState = when (this) {
    UpdateState.None -> UpdateUiState()
    is UpdateState.Available -> UpdateUiState(UpdateBannerKind.AVAILABLE, update.versionName)
    is UpdateState.Downloading -> UpdateUiState(UpdateBannerKind.DOWNLOADING, update.versionName, progress)
    is UpdateState.NeedsPermission -> UpdateUiState(UpdateBannerKind.NEEDS_PERMISSION, update.versionName)
    is UpdateState.Failed -> UpdateUiState(UpdateBannerKind.FAILED, update.versionName)
}

/**
 * "Bora pro Treino! 0.41 is available - Install". Takes no room when there's nothing to offer.
 * This feature's only public entry point; `:app` shows it at the top of the screen.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val viewModel: UpdateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val current = state
    val update = when (current) {
        UpdateState.None -> return
        is UpdateState.Available -> current.update
        is UpdateState.Downloading -> current.update
        is UpdateState.NeedsPermission -> current.update
        is UpdateState.Failed -> current.update
    }
    val actions = UpdateActions(
        onInstall = { viewModel.install(update) },
        onLater = { viewModel.later(update) },
        onAllow = { context.startActivity(viewModel.allowInstallsIntent()) },
        onRetry = { viewModel.install(update) },
    )
    // The banner sits in the Scaffold's top bar, so it keeps clear of the status bar itself.
    UpdateBannerContent(current.toUiState(), actions, modifier.statusBarsPadding())
}

/** Drawn from its arguments alone, so screenshot tests can render it. Draws nothing when idle. */
@Composable
internal fun UpdateBannerContent(
    state: UpdateUiState = UpdateUiState(),
    actions: UpdateActions = UpdateActions(),
    modifier: Modifier = Modifier,
) {
    when (state.kind) {
        UpdateBannerKind.IDLE -> Unit
        UpdateBannerKind.AVAILABLE -> BannerCard(modifier) {
            MessageRow(stringResource(R.string.update_available, state.versionName)) {
                TextButton(onClick = actions.onLater) { Text(stringResource(R.string.update_later)) }
                Button(onClick = actions.onInstall) { Text(stringResource(R.string.update_install)) }
            }
        }
        UpdateBannerKind.DOWNLOADING -> BannerCard(modifier) {
            Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                // The card's end padding is narrow for the buttons; even it out for the bar.
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
            )
        }
        UpdateBannerKind.NEEDS_PERMISSION -> BannerCard(modifier) {
            MessageRow(stringResource(R.string.update_allow)) {
                FilledTonalButton(onClick = actions.onAllow) { Text(stringResource(R.string.update_allow_button)) }
            }
        }
        UpdateBannerKind.FAILED -> BannerCard(modifier) {
            MessageRow(stringResource(R.string.update_failed)) {
                TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.update_retry)) }
            }
        }
    }
}

/** A soft white card on the grey page, like the rest of the app. */
@Composable
private fun BannerCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun MessageRow(text: String, actions: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        actions()
    }
}

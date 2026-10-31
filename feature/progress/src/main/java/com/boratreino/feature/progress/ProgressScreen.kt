package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.boratreino.core.ui.PlaceholderScreen

@Composable
internal fun ProgressScreen() {
    ProgressContent()
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until its phase lands. */
@Composable
internal fun ProgressContent(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.progress_title),
        message = stringResource(R.string.progress_message),
        modifier = modifier,
    )
}

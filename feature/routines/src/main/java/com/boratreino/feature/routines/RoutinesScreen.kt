package com.boratreino.feature.routines

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.boratreino.core.ui.PlaceholderScreen

@Composable
internal fun RoutinesScreen() {
    RoutinesContent()
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until its phase lands. */
@Composable
internal fun RoutinesContent(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.routines_title),
        message = stringResource(R.string.routines_message),
        modifier = modifier,
    )
}

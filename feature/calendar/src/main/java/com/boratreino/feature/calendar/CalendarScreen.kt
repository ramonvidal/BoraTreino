package com.boratreino.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.boratreino.core.ui.PlaceholderScreen

@Composable
internal fun CalendarScreen() {
    CalendarContent()
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until its phase lands. */
@Composable
internal fun CalendarContent(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.calendar_title),
        message = stringResource(R.string.calendar_message),
        modifier = modifier,
    )
}

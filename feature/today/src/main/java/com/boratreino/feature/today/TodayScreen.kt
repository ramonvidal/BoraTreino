package com.boratreino.feature.today

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.boratreino.core.ui.PlaceholderScreen
import com.boratreino.core.ui.R as CoreUiR

@Composable
internal fun TodayScreen(onOpenSettings: () -> Unit) {
    TodayContent(onOpenSettings = onOpenSettings)
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until Phase 1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayContent(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_settings),
                            contentDescription = stringResource(R.string.today_open_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PlaceholderScreen(
            title = stringResource(R.string.today_title),
            message = stringResource(R.string.today_message),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

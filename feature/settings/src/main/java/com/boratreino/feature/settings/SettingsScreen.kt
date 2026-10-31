package com.boratreino.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.boratreino.core.ui.PlaceholderScreen
import com.boratreino.core.ui.R as CoreUiR

@Composable
internal fun SettingsScreen(onBack: () -> Unit) {
    SettingsContent(onBack = onBack)
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until Phase 4. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_back),
                            contentDescription = stringResource(CoreUiR.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PlaceholderScreen(
            title = stringResource(R.string.settings_title),
            message = stringResource(R.string.settings_message),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

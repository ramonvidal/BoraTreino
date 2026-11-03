package com.boratreino.feature.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val SETTINGS_ROUTE = "settings"

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, updatesCard: (@Composable () -> Unit)? = null) {
    composable(SETTINGS_ROUTE) { SettingsScreen(onBack = onBack, updatesCard = updatesCard) }
}

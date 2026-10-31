package com.boratreino.feature.routines

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val ROUTINES_ROUTE = "routines"

fun NavGraphBuilder.routinesScreen() {
    composable(ROUTINES_ROUTE) { RoutinesScreen() }
}

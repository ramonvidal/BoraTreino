package com.boratreino.feature.progress

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val PROGRESS_ROUTE = "progress"

fun NavGraphBuilder.progressScreen() {
    composable(PROGRESS_ROUTE) { ProgressScreen() }
}

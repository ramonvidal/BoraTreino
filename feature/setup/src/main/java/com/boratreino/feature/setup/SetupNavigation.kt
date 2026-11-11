package com.boratreino.feature.setup

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry points; everything else stays internal to the module. */
const val SETUP_ROUTE = "setup"

/** The first-run setup (gym, first routine, training days); [onDone] is called on Finish or Skip. */
fun NavGraphBuilder.setupScreen(onDone: () -> Unit) {
    composable(SETUP_ROUTE) { SetupScreen(onDone = onDone) }
}

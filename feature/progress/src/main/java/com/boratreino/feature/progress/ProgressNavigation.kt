package com.boratreino.feature.progress

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** This feature's only public entry point; everything else stays internal to the module. */
const val PROGRESS_ROUTE = "progress"

internal const val MACHINE_ID_ARG = "machineId"
internal const val MACHINE_PROGRESS_ROUTE = "progress/machine/{$MACHINE_ID_ARG}"

internal fun machineProgressRoute(machineId: Long): String = "progress/machine/$machineId"

/** The Progress tab and the machine detail it opens. [navController] is only used for this feature's own screens. */
fun NavGraphBuilder.progressGraph(navController: NavController) {
    composable(PROGRESS_ROUTE) {
        ProgressScreen(onOpenMachine = { navController.navigate(machineProgressRoute(it)) })
    }
    composable(
        route = MACHINE_PROGRESS_ROUTE,
        arguments = listOf(navArgument(MACHINE_ID_ARG) { type = NavType.LongType }),
    ) {
        MachineProgressScreen(onBack = { navController.popBackStack() })
    }
}

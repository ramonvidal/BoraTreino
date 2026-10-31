package com.boratreino.feature.today

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** This feature's only public entry point; everything else stays internal to the module. */
const val TODAY_ROUTE = "today"

internal const val PICKER_ROUTE = "today/pick"
internal const val MACHINE_ID_ARG = "machineId"
internal const val MACHINE_ROUTE = "today/machine/{$MACHINE_ID_ARG}"

internal fun machineRoute(machineId: Long): String = "today/machine/$machineId"

/**
 * The Today tab, the machine picker and the machine screen.
 * [navController] is only used for this feature's own screens; the editor is opened through
 * [onEditMachine] and [onNewMachine], which `:app` wires to the routines feature.
 */
fun NavGraphBuilder.todayGraph(
    navController: NavController,
    onOpenSettings: () -> Unit,
    onEditMachine: (machineId: Long) -> Unit,
    onNewMachine: () -> Unit,
) {
    composable(TODAY_ROUTE) {
        TodayScreen(
            onOpenMachine = { navController.navigate(machineRoute(it)) },
            onAllMachines = { navController.navigate(PICKER_ROUTE) },
            onOpenSettings = onOpenSettings,
        )
    }
    composable(PICKER_ROUTE) {
        PickerScreen(
            onBack = { navController.popBackStack() },
            // The picker is replaced by the machine, so Back from the machine goes to where the picker was opened.
            onOpenMachine = { id ->
                navController.navigate(machineRoute(id)) { popUpTo(PICKER_ROUTE) { inclusive = true } }
            },
            onNewMachine = onNewMachine,
        )
    }
    composable(
        route = MACHINE_ROUTE,
        arguments = listOf(navArgument(MACHINE_ID_ARG) { type = NavType.LongType }),
    ) {
        MachineScreen(onBack = { navController.popBackStack() }, onEdit = onEditMachine)
    }
}

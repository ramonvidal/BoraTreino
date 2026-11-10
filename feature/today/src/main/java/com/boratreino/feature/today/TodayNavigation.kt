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

/** True on the machine screen, which draws the big rest card itself (`:app` hides its rest banner there). */
fun isMachineRoute(route: String?): Boolean = route == MACHINE_ROUTE

/**
 * The Today tab, the machine picker and the machine screen.
 * [navController] is only used for this feature's own screens; the editor is opened through
 * [onEditMachine] and [onNewMachine], which `:app` wires to the routines feature.
 */
fun NavGraphBuilder.todayGraph(
    navController: NavController,
    onEditMachine: (machineId: Long) -> Unit,
    onNewMachine: () -> Unit,
    onEditRoutine: (routineId: Long) -> Unit,
) {
    composable(TODAY_ROUTE) {
        TodayScreen(
            onOpenMachine = { navController.navigate(machineRoute(it)) },
            onAllMachines = { navController.navigate(PICKER_ROUTE) },
            onEditRoutine = onEditRoutine,
            onEditMachine = onEditMachine,
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
        MachineScreen(
            onBack = { navController.popBackStack() },
            onEdit = onEditMachine,
            // The next exercise replaces this screen, so Back goes to where this one was opened from.
            onOpenMachine = { id ->
                navController.navigate(machineRoute(id)) { popUpTo(MACHINE_ROUTE) { inclusive = true } }
            },
            // The training finished by itself: back to Today, which shows the summary.
            onRoutineFinished = {
                if (!navController.popBackStack(TODAY_ROUTE, inclusive = false)) {
                    navController.navigate(TODAY_ROUTE) { launchSingleTop = true }
                }
            },
        )
    }
}

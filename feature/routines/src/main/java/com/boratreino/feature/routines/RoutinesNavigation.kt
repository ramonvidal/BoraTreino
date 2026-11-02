package com.boratreino.feature.routines

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** This feature's only public entry points; everything else stays internal to the module. */
const val ROUTINES_ROUTE = "routines"

/** The editor's optional query argument; [NEW_MACHINE] means "a new machine". */
internal const val MACHINE_ID_ARG = "machineId"
internal const val NEW_MACHINE = -1L

internal const val ROUTINE_ID_ARG = "routineId"
internal const val NEW_ROUTINE = -1L

private const val ROUTINE_EDITOR_ROUTE = "routine/edit?$ROUTINE_ID_ARG={$ROUTINE_ID_ARG}"

/** The route that opens the routine editor: a new routine when [routineId] is null. */
fun routineEditorRoute(routineId: Long?): String =
    if (routineId == null) "routine/edit" else "routine/edit?$ROUTINE_ID_ARG=$routineId"

private const val MACHINE_EDITOR_ROUTE = "machine/edit?$MACHINE_ID_ARG={$MACHINE_ID_ARG}"

/** The route that opens the machine editor: a new machine when [machineId] is null. */
fun machineEditorRoute(machineId: Long?): String =
    if (machineId == null) "machine/edit" else "machine/edit?$MACHINE_ID_ARG=$machineId"

/**
 * The Routines tab. [onOpenEditor] gets a machine id to edit, or null for a new machine;
 * [onOpenRoutine] a routine id to edit, or null for a new routine.
 */
fun NavGraphBuilder.routinesScreen(
    onOpenEditor: (machineId: Long?) -> Unit,
    onOpenRoutine: (routineId: Long?) -> Unit,
) {
    composable(ROUTINES_ROUTE) { RoutinesScreen(onOpenEditor = onOpenEditor, onOpenRoutine = onOpenRoutine) }
}

/** The machine editor; [onDone] is called after Save, Archive / Unarchive, or the back arrow. */
fun NavGraphBuilder.machineEditorScreen(onDone: () -> Unit) {
    composable(
        route = MACHINE_EDITOR_ROUTE,
        arguments = listOf(
            navArgument(MACHINE_ID_ARG) {
                type = NavType.LongType
                defaultValue = NEW_MACHINE
            },
        ),
    ) { MachineEditorScreen(onDone = onDone) }
}

/** The routine editor; [onDone] is called after Save, Delete, or the back arrow. */
fun NavGraphBuilder.routineEditorScreen(onDone: () -> Unit) {
    composable(
        route = ROUTINE_EDITOR_ROUTE,
        arguments = listOf(
            navArgument(ROUTINE_ID_ARG) {
                type = NavType.LongType
                defaultValue = NEW_ROUTINE
            },
        ),
    ) { RoutineEditorScreen(onDone = onDone) }
}

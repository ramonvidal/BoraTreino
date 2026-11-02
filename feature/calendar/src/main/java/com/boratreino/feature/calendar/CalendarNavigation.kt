package com.boratreino.feature.calendar

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** This feature's only public entry point; everything else stays internal to the module. */
const val CALENDAR_ROUTE = "calendar"

internal const val SESSION_ID_ARG = "sessionId"
internal const val SESSION_ROUTE = "calendar/session/{$SESSION_ID_ARG}"

internal fun sessionRoute(sessionId: Long): String = "calendar/session/$sessionId"

/** The Calendar tab and the session editor it opens. [navController] is only used for this feature's own screens. */
fun NavGraphBuilder.calendarGraph(navController: NavController) {
    composable(CALENDAR_ROUTE) {
        CalendarScreen(onOpenSession = { navController.navigate(sessionRoute(it)) })
    }
    composable(
        route = SESSION_ROUTE,
        arguments = listOf(navArgument(SESSION_ID_ARG) { type = NavType.LongType }),
    ) {
        SessionEditorScreen(onBack = { navController.popBackStack() })
    }
}

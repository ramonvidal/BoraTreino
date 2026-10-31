package com.boratreino.feature.calendar

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val CALENDAR_ROUTE = "calendar"

fun NavGraphBuilder.calendarScreen() {
    composable(CALENDAR_ROUTE) { CalendarScreen() }
}

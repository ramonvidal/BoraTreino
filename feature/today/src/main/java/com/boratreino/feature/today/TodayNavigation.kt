package com.boratreino.feature.today

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val TODAY_ROUTE = "today"

fun NavGraphBuilder.todayScreen(onOpenSettings: () -> Unit) {
    composable(TODAY_ROUTE) { TodayScreen(onOpenSettings = onOpenSettings) }
}

package com.boratreino.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boratreino.R
import com.boratreino.core.ui.R as CoreUiR
import com.boratreino.feature.calendar.CALENDAR_ROUTE
import com.boratreino.feature.calendar.calendarScreen
import com.boratreino.feature.progress.PROGRESS_ROUTE
import com.boratreino.feature.progress.progressScreen
import com.boratreino.feature.routines.ROUTINES_ROUTE
import com.boratreino.feature.routines.machineEditorRoute
import com.boratreino.feature.routines.machineEditorScreen
import com.boratreino.feature.routines.routineEditorRoute
import com.boratreino.feature.routines.routineEditorScreen
import com.boratreino.feature.routines.routinesScreen
import com.boratreino.feature.settings.SETTINGS_ROUTE
import com.boratreino.feature.settings.settingsScreen
import com.boratreino.feature.today.TODAY_ROUTE
import com.boratreino.feature.today.todayGraph
import com.boratreino.feature.update.UpdateBanner

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Today(TODAY_ROUTE, R.string.nav_today, CoreUiR.drawable.ic_today),
    Calendar(CALENDAR_ROUTE, R.string.nav_calendar, CoreUiR.drawable.ic_calendar),
    Progress(PROGRESS_ROUTE, R.string.nav_progress, CoreUiR.drawable.ic_progress),
    Routines(ROUTINES_ROUTE, R.string.nav_routines, CoreUiR.drawable.ic_routines),
}

/** The four tabs; Settings, the machine editor and Today's picker and machine screens are full screens above them with the bottom bar hidden. */
@Composable
fun BoraTreinoNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        topBar = { UpdateBanner() },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination.route) },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                // No pill: the selected tab is just green.
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Today.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            todayGraph(
                navController = navController,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
                onEditMachine = { machineId -> navController.navigate(machineEditorRoute(machineId)) },
                onNewMachine = { navController.navigate(machineEditorRoute(null)) },
            )
            calendarScreen()
            progressScreen()
            routinesScreen(
                onOpenEditor = { machineId -> navController.navigate(machineEditorRoute(machineId)) },
                onOpenRoutine = { routineId -> navController.navigate(routineEditorRoute(routineId)) },
            )
            routineEditorScreen(onDone = { navController.popBackStack() })
            machineEditorScreen(onDone = { navController.popBackStack() })
            settingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Switches tabs the way the bottom bar does, keeping each tab's state. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

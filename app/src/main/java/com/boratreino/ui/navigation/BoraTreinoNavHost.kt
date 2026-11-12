package com.boratreino.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boratreino.R
import com.boratreino.core.ui.RestBanner
import com.boratreino.core.ui.R as CoreUiR
import com.boratreino.feature.calendar.CALENDAR_ROUTE
import com.boratreino.feature.calendar.calendarGraph
import com.boratreino.feature.progress.PROGRESS_ROUTE
import com.boratreino.feature.progress.progressGraph
import com.boratreino.feature.routines.ROUTINES_ROUTE
import com.boratreino.feature.routines.exerciseLibraryRoute
import com.boratreino.feature.routines.exerciseLibraryScreen
import com.boratreino.feature.routines.machineEditorRoute
import com.boratreino.feature.routines.machineEditorScreen
import com.boratreino.feature.routines.routineEditorRoute
import com.boratreino.feature.routines.routineEditorScreen
import com.boratreino.feature.routines.routinesScreen
import com.boratreino.feature.settings.SETTINGS_ROUTE
import com.boratreino.feature.settings.settingsScreen
import com.boratreino.feature.setup.SETUP_ROUTE
import com.boratreino.feature.setup.setupScreen
import com.boratreino.feature.today.TODAY_ROUTE
import com.boratreino.feature.today.isMachineRoute
import com.boratreino.feature.today.todayGraph
import com.boratreino.ui.rest.RestBannerViewModel
import com.boratreino.ui.updates.UpdateBannerSlot
import com.boratreino.ui.updates.updatesCardSlot

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Today(TODAY_ROUTE, R.string.nav_today, CoreUiR.drawable.ic_today),
    Calendar(CALENDAR_ROUTE, R.string.nav_calendar, CoreUiR.drawable.ic_calendar),
    Progress(PROGRESS_ROUTE, R.string.nav_progress, CoreUiR.drawable.ic_progress),
    Routines(ROUTINES_ROUTE, R.string.nav_routines, CoreUiR.drawable.ic_routines),
    Settings(SETTINGS_ROUTE, R.string.nav_settings, CoreUiR.drawable.ic_settings),
}

/** The five tabs; the machine editor and Today's picker and machine screens are full screens above them with the bottom bar hidden. */
@Composable
fun BoraTreinoNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    val restViewModel: RestBannerViewModel = hiltViewModel()
    val rest by restViewModel.state.collectAsStateWithLifecycle()
    // The machine screen has its own big rest card.
    val restShown = rest != null && !isMachineRoute(currentDestination?.route)

    Scaffold(
        topBar = {
            // Padding the column keeps both banners clear of the status bar (the update banner's own padding is then consumed).
            Column(if (restShown) Modifier.statusBarsPadding() else Modifier) {
                UpdateBannerSlot()
                val banner = rest
                if (restShown && banner != null) {
                    RestBanner(
                        remainingSeconds = banner.remainingSeconds,
                        totalSeconds = banner.totalSeconds,
                        onExtend = restViewModel::extend,
                        onSkip = restViewModel::skip,
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 0.dp,
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination.route) },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                // Selected: Ink icon and label on a yellow rounded indicator.
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            enterTransition = { if (isTabSwitch()) TAB_FADE_IN else SLIDE_IN },
            exitTransition = { if (isTabSwitch()) TAB_FADE_OUT else SLIDE_OUT_PARALLAX },
            popEnterTransition = { if (isTabSwitch()) TAB_FADE_IN else SLIDE_IN_PARALLAX },
            popExitTransition = { if (isTabSwitch()) TAB_FADE_OUT else SLIDE_OUT },
        ) {
            todayGraph(
                navController = navController,
                onEditMachine = { machineId -> navController.navigate(machineEditorRoute(machineId)) },
                onNewMachine = { navController.navigate(machineEditorRoute(null)) },
                onEditRoutine = { routineId -> navController.navigate(routineEditorRoute(routineId)) },
                onStartSetup = { navController.navigate(SETUP_ROUTE) },
            )
            calendarGraph(navController)
            progressGraph(navController)
            routinesScreen(
                onOpenEditor = { machineId -> navController.navigate(machineEditorRoute(machineId)) },
                onOpenRoutine = { routineId -> navController.navigate(routineEditorRoute(routineId)) },
                onOpenLibrary = { navController.navigate(exerciseLibraryRoute()) },
            )
            exerciseLibraryScreen(onDone = { navController.popBackStack() })
            routineEditorScreen(onDone = { navController.popBackStack() })
            machineEditorScreen(onDone = { navController.popBackStack() })
            setupScreen(onDone = { navController.popBackStack() })
            settingsScreen(updatesCard = updatesCardSlot)
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

private val TAB_ROUTES = TopLevelDestination.entries.map { it.route }.toSet()

private fun NavDestination?.isTab(): Boolean = this?.route in TAB_ROUTES

/** Tab to tab changes fade so the tabs do not slide; anything involving a full-screen route slides. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTab() && targetState.destination.isTab()

private const val SLIDE_MS = 220
private const val FADE_MS = 150

private val TAB_FADE_IN: EnterTransition = fadeIn(tween(FADE_MS))
private val TAB_FADE_OUT: ExitTransition = fadeOut(tween(FADE_MS))
private val SLIDE_IN: EnterTransition = slideInHorizontally(tween(SLIDE_MS)) { it }
private val SLIDE_OUT: ExitTransition = slideOutHorizontally(tween(SLIDE_MS)) { it }
/** The screen underneath moves a little and fades while a full screen slides over it. */
private val SLIDE_OUT_PARALLAX: ExitTransition = slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeOut(tween(SLIDE_MS))
private val SLIDE_IN_PARALLAX: EnterTransition = slideInHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeIn(tween(SLIDE_MS))

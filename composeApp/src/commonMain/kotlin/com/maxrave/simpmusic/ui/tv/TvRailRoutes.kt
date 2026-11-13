package com.maxrave.simpmusic.ui.tv

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.maxrave.simpmusic.ui.navigation.destination.home.SettingsDestination

internal fun navigateTvRailRoot(navController: NavController, destination: Any) {
    val leavingSettings = navController.currentDestination?.hasRoute<SettingsDestination>() == true
    // Settings is an overlay destination, not a tab stack. Saving it under Home and immediately
    // restoring Home would resurrect Settings while the rail already marks Home as selected.
    if (leavingSettings) navController.popBackStack()
    if (navController.currentDestination?.hasRoute(destination::class) == true) return
    navController.navigate(destination) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

package com.maxrave.simpmusic.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.maxrave.simpmusic.ui.navigation.destination.home.AppLogDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.CreditDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.ListenTogetherDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.ListenTogetherSettingsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.MoodDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.NotificationDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.SettingsDestination
import com.maxrave.simpmusic.ui.screen.home.ListenTogetherScreen
import com.maxrave.simpmusic.ui.screen.home.ListenTogetherSettingsScreen
import com.maxrave.simpmusic.ui.screen.home.MoodScreen
import com.maxrave.simpmusic.ui.screen.home.NotificationScreen
import com.maxrave.simpmusic.ui.screen.home.SettingScreen
import com.maxrave.simpmusic.ui.screen.other.AppLogScreen
import com.maxrave.simpmusic.ui.screen.other.CreditScreen

fun NavGraphBuilder.homeScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
) {
    composable<CreditDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            CreditScreen(
                paddingValues = innerPadding,
                navController = navController,
            )
        }
    }
    composable<AppLogDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            AppLogScreen(
                paddingValues = innerPadding,
                navController = navController,
            )
        }
    }
    composable<MoodDestination> { entry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(entry.id) {
            val params = entry.toRoute<MoodDestination>().params
            MoodScreen(
                navController = navController,
                params = params,
            )
        }
    }
    composable<ListenTogetherDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            ListenTogetherScreen(
                navController = navController,
                innerPadding = innerPadding,
            )
        }
    }
    composable<ListenTogetherSettingsDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            ListenTogetherSettingsScreen(
                navController = navController,
                innerPadding = innerPadding,
            )
        }
    }
    composable<NotificationDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            NotificationScreen(
                navController = navController,
            )
        }
    }
    composable<SettingsDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            SettingScreen(
                navController = navController,
                innerPadding = innerPadding,
            )
        }
    }
}
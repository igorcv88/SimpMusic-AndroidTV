package com.maxrave.simpmusic.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.maxrave.simpmusic.ui.navigation.destination.login.DiscordLoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.LastfmLoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.LoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.SpotifyLoginDestination
import com.maxrave.simpmusic.ui.screen.login.DiscordLoginScreen
import com.maxrave.simpmusic.ui.screen.login.DiscordOAuthLoginScreen
import com.maxrave.simpmusic.ui.screen.login.LastfmLoginScreen
import com.maxrave.simpmusic.ui.screen.login.LoginScreen
import com.maxrave.simpmusic.ui.screen.login.SpotifyLoginScreen
import org.simpmusic.discord.isDiscordOAuthAvailable

fun NavGraphBuilder.loginScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
    hideBottomBar: () -> Unit,
    showBottomBar: () -> Unit,
) {
    composable<LoginDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            LoginScreen(
                innerPadding = innerPadding,
                navController = navController,
                hideBottomNavigation = hideBottomBar,
                showBottomNavigation = showBottomBar,
            )
        }
    }

    composable<SpotifyLoginDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            SpotifyLoginScreen(
                innerPadding = innerPadding,
                navController = navController,
                hideBottomNavigation = hideBottomBar,
                showBottomNavigation = showBottomBar,
            )
        }
    }

    composable<DiscordLoginDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            // A Full build carries a Discord application and logs in through Discord's own page; a FOSS
            // build keeps the WebView login whose token kizzy uses.
            if (isDiscordOAuthAvailable()) {
                DiscordOAuthLoginScreen(
                    innerPadding = innerPadding,
                    navController = navController,
                    hideBottomNavigation = hideBottomBar,
                    showBottomNavigation = showBottomBar,
                )
            } else {
                DiscordLoginScreen(
                    innerPadding = innerPadding,
                    navController = navController,
                    hideBottomNavigation = hideBottomBar,
                    showBottomNavigation = showBottomBar,
                )
            }
        }
    }

    composable<LastfmLoginDestination> { tvEntry ->
        com.maxrave.simpmusic.ui.tv.TvDestination(tvEntry.id) {
            LastfmLoginScreen(
                innerPadding = innerPadding,
                navController = navController,
                hideBottomNavigation = hideBottomBar,
                showBottomNavigation = showBottomBar,
            )
        }
    }
}
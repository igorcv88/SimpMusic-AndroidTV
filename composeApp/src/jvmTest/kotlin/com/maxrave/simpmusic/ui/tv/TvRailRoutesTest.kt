package com.maxrave.simpmusic.ui.tv

import androidx.navigation.NavHostController
import androidx.navigation.createGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.NavDestination.Companion.hasRoute
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.SettingsDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination
import com.maxrave.simpmusic.ui.navigation.destination.player.FullscreenDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.PlaylistDestination
import kotlin.test.*

class TvRailRoutesTest {
    private fun onMain(block: () -> Unit) = javax.swing.SwingUtilities.invokeAndWait(block)
    private fun controller() = NavHostController().apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = HomeDestination) {
            composable<HomeDestination> { }
            composable<SettingsDestination> { }
            composable<LibraryDestination> { }
            composable<SearchDestination> { }
            composable<PlaylistDestination> { }
            composable<FullscreenDestination> { }
        }
    }
    @Test fun choosingHomeFromSettingsCannotRestoreSettingsAsHomeSavedState() = onMain {
        val nav = controller()
        nav.navigate(SettingsDestination)
        navigateTvRailRoot(nav, HomeDestination)
        assertTrue(nav.currentDestination!!.hasRoute<HomeDestination>())
    }
    @Test fun choosingLibraryFromSettingsDoesNotSaveSettingsIntoHomeTab() = onMain {
        val nav = controller()
        nav.navigate(SettingsDestination)
        navigateTvRailRoot(nav, LibraryDestination)
        assertTrue(nav.currentDestination!!.hasRoute<LibraryDestination>())
        navigateTvRailRoot(nav, HomeDestination)
        assertTrue(nav.currentDestination!!.hasRoute<HomeDestination>())
    }
    @Test fun fullscreenBackPreservesTheExactEntryForEveryPlaybackOrigin() = onMain {
        for (origin in listOf(HomeDestination, LibraryDestination, SearchDestination, PlaylistDestination("test", false))) {
            val nav = controller()
            if (origin != HomeDestination) nav.navigate(origin)
            val entry = nav.currentBackStackEntry!!.id
            nav.navigate(FullscreenDestination)
            assertTrue(nav.popBackStack())
            assertEquals(entry, nav.currentBackStackEntry!!.id)
        }
    }
}

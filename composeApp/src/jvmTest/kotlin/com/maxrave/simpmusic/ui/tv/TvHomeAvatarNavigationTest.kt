@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.screen.home.HomeAccountButton
import com.maxrave.simpmusic.ui.theme.AppTheme
import kotlin.test.*

class TvHomeAvatarNavigationTest {
    @Test fun avatarLeftEntersActualRailAndRightRestoresWithSoftwareBackdrop() = railRoundTrip(true)
    @Test fun avatarLeftEntersActualRailAndRightRestoresWithoutSoftwareBackdrop() = railRoundTrip(false)

    private fun railRoundTrip(software: Boolean) = runComposeUiTest {
        val navigation = TvNavigationState()
        lateinit var focusManager: FocusManager
        val avatar = FocusRequester()
        val content = FocusRequester()
        setContent {
            CompositionLocalProvider(LocalTvDevice provides true, LocalTvNavigation provides navigation,
                LocalTvMotion provides resolveTvMotion(0f)) {
                AppTheme {
                    focusManager = LocalFocusManager.current
                    val scene = remember { TvScene() }
                    val producer = rememberTvBackdrop(scene, "home", software)
                    val controller = rememberNavController()
                    CompositionLocalProvider(LocalTvScene provides scene) {
                        Row(Modifier.size(600.dp, 400.dp)) {
                            TvNavigationRail(controller, showAnalyticsTab = false, showMixForYouTab = false,
                                contentFocus = content, reloadDestinationIfNeeded = {})
                            Box(Modifier.weight(1f).fillMaxHeight().tvBackdropSource(producer)
                                .focusRequester(content).focusProperties {
                                    // Same exit contract as the production App content boundary.
                                    onExit = {
                                        if (requestedFocusDirection == FocusDirection.Left && navigation.railFocus.requestFocus()) cancelFocusChange()
                                    }
                                }.focusGroup()) {
                                CompositionLocalProvider(LocalTvBackdropOverlay provides producer) {
                                    NavHost(controller, startDestination = HomeDestination,
                                        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                                        popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                                        composable<HomeDestination> { entry ->
                                            TvDestination(entry.id, root = true) {
                                                Box(Modifier.padding(30.dp)) {
                                                    HomeAccountButton(null, null, controller,
                                                        Modifier.focusRequester(avatar).focusProperties { left = navigation.railFocus })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        TvBackdropOverlays(producer, Modifier.size(600.dp, 400.dp).padding(start = TvDimens.RailCollapsed))
                    }
                }
            }
        }
        val avatarNode = onNode(hasClickAction() and hasContentDescription("Guest"))
        onAllNodes(hasClickAction() and hasContentDescription("Guest")).assertCountEquals(1)
        runOnIdle { assertTrue(avatar.requestFocus()) }
        avatarNode.assertIsFocused()
        val home = onNode(hasClickAction() and hasText("Home"))
        repeat(3) {
            // The production LEFT handler: a direct request for the rail, which lands on Home.
            avatarNode.performKeyInput { pressKey(Key.DirectionLeft) }
            home.assertIsFocused()
            runOnIdle { assertTrue(navigation.railHasFocus) }
            // The rail's onExit toward the content calls restoreContent(). The JVM test harness does
            // not turn a simulated arrow key into default focus movement, so call it directly, as
            // tab-root BACK and the rail's own exit do on the device.
            runOnIdle { assertTrue(navigation.restoreContent()) }
            avatarNode.assertIsFocused()
            runOnIdle { assertFalse(navigation.railHasFocus) }
        }
        avatarNode.performKeyInput { pressKey(Key.DirectionCenter) }
        // OK on the restored avatar still opens its menu (menu dismissal by key is not simulated here).
        onAllNodesWithText("Notification").assertCountEquals(1)
    }

    @Test fun rejectedDirectRailRequestKeepsNormalLeftFallback() = runComposeUiTest {
        val navigation = TvNavigationState()
        val avatar = FocusRequester()
        val fallback = FocusRequester()
        lateinit var focusManager: FocusManager
        setContent {
            CompositionLocalProvider(LocalTvDevice provides true, LocalTvNavigation provides navigation,
                LocalTvMotion provides resolveTvMotion(0f)) {
                AppTheme {
                    focusManager = LocalFocusManager.current
                    val controller = rememberNavController()
                    Row {
                        // An attached target which deliberately rejects focus, rather than an
                        // uninitialized requester or focusable(false), which adds no target.
                        Box(Modifier.size(1.dp).focusRequester(navigation.railFocus)
                            .focusProperties { canFocus = false }.focusTarget())
                        Button({}, Modifier.focusRequester(fallback).testTag("fallback")) { Text("Fallback") }
                        HomeAccountButton(null, null, controller,
                            Modifier.focusRequester(avatar).focusProperties { left = fallback })
                    }
                }
            }
        }
        runOnIdle { assertTrue(avatar.requestFocus()); assertFalse(navigation.railFocus.requestFocus()) }
        onNode(hasClickAction() and hasContentDescription("Guest")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        // The handler must decline (the rail refused focus), leaving the normal focus fallback.
        onNodeWithTag("fallback").assertIsNotFocused()
        runOnIdle { assertTrue(focusManager.moveFocus(FocusDirection.Left)) }
        onNodeWithTag("fallback").assertIsFocused()
    }
}

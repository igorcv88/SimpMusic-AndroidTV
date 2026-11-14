@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.maxrave.simpmusic.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.test.*

class TvBackdropForeignRootTest {
    @Test fun popupKeepsOwnFocusAndPanelWithoutMainWindowExtraction() = foreignWindow(false)
    @Test fun dialogKeepsOwnFocusAndPanelWithoutMainWindowExtraction() = foreignWindow(true)

    private fun foreignWindow(dialog: Boolean) = runComposeUiTest {
        var producer: TvBackdrop? = null
        var show by mutableStateOf(false)
        val requester = FocusRequester()
        setContent {
            CompositionLocalProvider(LocalTvDevice provides true, LocalTvMotion provides resolveTvMotion(0f)) {
                AppTheme {
                    val scene = remember { TvScene() }
                    val backdrop = rememberTvBackdrop(scene, "main", true)
                    producer = backdrop
                    CompositionLocalProvider(LocalTvScene provides scene, LocalTvBackdropOverlay provides backdrop) {
                        Box(Modifier.size(220.dp, 160.dp).testTag("main")) {
                            Box(Modifier.fillMaxSize().tvBackdropSource(backdrop).background(Color.Red))
                            TvBackdropOverlays(backdrop, Modifier.matchParentSize())
                        }
                        if (show) {
                            val content: @Composable () -> Unit = {
                                Box(Modifier.size(120.dp, 100.dp).background(Color.Black).testTag("foreign")) {
                                    Column(Modifier.fillMaxSize().tvSceneGlass()) {
                                        Box(Modifier.padding(16.dp).size(60.dp, 40.dp).focusRequester(requester)
                                            .tvFocus(TvFocusStyle.Control).focusable().testTag("foreignFocus"))
                                    }
                                }
                                LaunchedEffect(Unit) { requester.requestFocus() }
                            }
                            if (dialog) Dialog(onDismissRequest = {}) { content() }
                            else Popup(onDismissRequest = {}) { content() }
                        }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 10000) { producer?.frame != null }
        runOnIdle { show = true }
        onNodeWithTag("foreignFocus").assertIsFocused()
        mainClock.advanceTimeBy(1000)
        runOnIdle { assertEquals(0, producer!!.overlays.size, "foreign roots must not register in the main overlay") }
        val main = onNodeWithTag("main").captureToImage().toPixelMap()
        // (20,20) lies under the Popup's own box, and a Dialog dims the whole scene with its scrim, so
        // sample a pixel the foreign content does not cover and accept only a dimmed red: foreign
        // neutral glass leaking into the main source would raise green/blue.
        val probe = main[200, 140]
        assertTrue(probe.red > .3f && probe.green < .02f && probe.blue < .02f,
            "foreign material must not paint the main source: $probe")
        val foreign = onNodeWithTag("foreign").captureToImage().toPixelMap()
        val panel = foreign[10, foreign.height / 2]
        assertTrue(abs(panel.red - panel.blue) < .03f, "foreign material must not sample red main-window pixels: $panel")
        assertTrue((10 until 85).any { x -> foreign[x, 16].let { maxOf(it.red, it.green, it.blue) > .08f } },
            "focus/panel rim must remain visible in its own root")
    }
}

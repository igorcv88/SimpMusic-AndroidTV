@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.*

class TvBackdropRenderTest {
    @Test fun contentTransitionsSnapOnlyInsideSoftwareBackdrop() = runComposeUiTest {
        mainClock.autoAdvance = false
        var software by mutableStateOf(true)
        var target by mutableStateOf(false)
        var enter: EnterTransition? = null
        var exit: ExitTransition? = null
        setContent {
            val source = rememberGraphicsLayer()
            val sampled = rememberGraphicsLayer()
            val producer = remember(source, sampled) { TvBackdrop(source, sampled) }
            CompositionLocalProvider(LocalTvBackdropOverlay provides if (software) producer else null) {
                enter = tvBackdropEnter(fadeIn())
                exit = tvBackdropExit(fadeOut())
                TvBackdropCrossfade(target, animationSpec = tween(1000)) { Text(if (it) "NEW" else "OLD") }
            }
        }
        runOnIdle { target = true }
        mainClock.advanceTimeByFrame()
        onNodeWithText("OLD").assertDoesNotExist()
        onNodeWithText("NEW").assertExists()
        runOnIdle {
            assertEquals(EnterTransition.None, enter)
            assertEquals(ExitTransition.None, exit)
            software = false
        }
        mainClock.advanceTimeByFrame()
        runOnIdle { target = false }
        mainClock.advanceTimeBy(100)
        onNodeWithText("NEW").assertExists()
        onNodeWithText("OLD").assertExists()
        runOnIdle {
            assertNotEquals(EnterTransition.None, enter)
            assertNotEquals(ExitTransition.None, exit)
        }
    }

    @Test fun focusedCardDoesNotDoubleTranslucentForeground() = runComposeUiTest {
        var backdrop: TvBackdrop? = null
        setContent {
            val scene = remember { TvScene() }
            val producer = rememberTvBackdrop(scene, "card", true)
            backdrop = producer
            CompositionLocalProvider(LocalTvDevice provides true, LocalTvScene provides scene,
                LocalTvBackdropOverlay provides producer, LocalTvMotion provides resolveTvMotion(0f)) {
                val requester = remember { FocusRequester() }
                Box(Modifier.size(160.dp, 100.dp).background(Color.Black).testTag("cardAlpha")) {
                    Box(Modifier.fillMaxSize().tvBackdropSource(producer)) {
                        Box(Modifier.offset(30.dp, 20.dp).size(80.dp, 60.dp).focusRequester(requester)
                            .tvFocus(TvFocusStyle.Card).focusable().background(Color.Red.copy(alpha = .5f)))
                    }
                    TvBackdropOverlays(producer, Modifier.matchParentSize())
                }
                LaunchedEffect(Unit) { requester.requestFocus() }
            }
        }
        waitUntil(timeoutMillis = 10000) { backdrop?.frame != null }
        val center = onNodeWithTag("cardAlpha").captureToImage().toPixelMap()[70, 50]
        assertTrue(center.red in .48f.. .52f, "half-alpha foreground must be composited once: $center")
    }

    @Test fun standingPanelReplayRetainsAncestorClip() = runComposeUiTest {
        var backdrop: TvBackdrop? = null
        setContent {
            val scene = remember { TvScene() }
            val producer = rememberTvBackdrop(scene, "panel", true)
            backdrop = producer
            CompositionLocalProvider(LocalTvDevice provides true, LocalTvScene provides scene, LocalTvBackdropOverlay provides producer) {
                Box(Modifier.size(160.dp, 100.dp).background(Color.Black).testTag("panelClip")) {
                    Box(Modifier.fillMaxSize().tvBackdropSource(producer)) {
                        Box(Modifier.offset(20.dp, 20.dp).size(60.dp, 40.dp).clipToBounds()) {
                            Box(Modifier.requiredSize(100.dp, 40.dp).tvSceneGlass(RoundedCornerShape(8.dp)).background(Color.Red))
                        }
                    }
                    TvBackdropOverlays(producer, Modifier.matchParentSize())
                }
            }
        }
        waitUntil(timeoutMillis = 10000) { backdrop?.frame != null }
        val outside = onNodeWithTag("panelClip").captureToImage().toPixelMap()[100, 40]
        assertTrue(outside.red < .02f && outside.green < .02f && outside.blue < .02f, "$outside")
    }

    @Test fun independentCachedChildPublishesChangedPixels() = runComposeUiTest {
        var backdrop: TvBackdrop? = null
        val color = mutableStateOf(Color.Red)
        setContent {
            val producer = rememberTvBackdrop(remember { TvScene() }, "cached", true)
            backdrop = producer
            Box(Modifier.size(160.dp, 80.dp)) {
                Box(Modifier.fillMaxSize().tvBackdropSource(producer)) {
                    Box(Modifier.fillMaxSize().graphicsLayer().drawBehind { drawRect(color.value) })
                }
                TvBackdropOverlays(producer, Modifier.matchParentSize())
            }
        }
        waitUntil(timeoutMillis = 10000) { backdrop?.frame != null }
        assertTrue(backdrop!!.frame!!.image.toPixelMap()[40, 40].red > .95f)
        runOnIdle { color.value = Color.Green }
        waitUntil(timeoutMillis = 3000) {
            backdrop!!.frame?.image?.toPixelMap()?.let { it[it.width / 2, it.height / 2].green > .95f } == true
        }
    }

    @Test fun realProducerBlursBaseWithoutRecapturingFocusedOverlay() = runComposeUiTest {
        var backdrop: TvBackdrop? = null
        val color = mutableStateOf(Color.Red)
        setContent {
            val scene = remember { TvScene() }
            val producer = rememberTvBackdrop(scene, "home", true)
            backdrop = producer
            CompositionLocalProvider(LocalTvDevice provides true, LocalTvScene provides scene, LocalTvBackdropOverlay provides producer) {
                val requester = remember { FocusRequester() }
                Box(Modifier.size(200.dp, 100.dp).testTag("source")) {
                Box(Modifier.fillMaxSize().tvBackdropSource(producer)) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(color.value))
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue)) { Text("TEXT", color = Color.White) }
                    }
                    Box(Modifier.offset(20.dp, 40.dp).size(50.dp, 40.dp).focusRequester(requester).tvFocus(TvFocusStyle.Control).focusable()) {
                        Text("HI", color = Color.White, fontSize = 16.sp)
                    }
                }
                TvBackdropOverlays(producer, Modifier.matchParentSize())
                }
                LaunchedEffect(Unit) { requester.requestFocus() }
            }
        }
        waitUntil(timeoutMillis = 10000) { backdrop?.frame != null }
        val frame = backdrop!!.frame!!
        val pixels = frame.image.toPixelMap()
        assertTrue(frame.image.width <= 256 && frame.image.height <= 256)
        // A white specular rim would contaminate this red field if it entered the base layer.
        val underFocus = pixels[(frame.image.width * .15f).toInt(), (frame.image.height * .65f).toInt()]
        assertTrue(underFocus.red > .95f && underFocus.green < .02f && underFocus.blue < .02f, "$underFocus")
        val edge = pixels[frame.image.width / 2, frame.image.height * 3 / 4]
        assertTrue(edge.red > .15f && edge.blue > .15f, "blur must spread both source colours: $edge")
        val rendered = onNodeWithTag("source").captureToImage().toPixelMap()
        assertTrue(rendered.width > 0)
        assertTrue((40 until 60).any { y -> (20 until 50).any { x ->
            rendered[x, y].let { it.red > .95f && it.green > .95f && it.blue > .95f }
        } }, "the focused foreground text must be replayed crisply above the material")
        val count = backdrop!!.captures
        runOnIdle { color.value = Color.Green }
        waitUntil(timeoutMillis = 10000) { backdrop!!.captures > count }
        val updated = backdrop!!.frame!!.image.toPixelMap()
        assertTrue(updated[updated.width / 5, updated.height * 3 / 4].green > .9f)
        waitForIdle()
        Thread.sleep(350)
        waitForIdle()
        val settled = backdrop!!.captures
        val unrelatedPlayerProgress = mutableStateOf(0)
        repeat(8) {
            runOnIdle { unrelatedPlayerProgress.value++ }
            Thread.sleep(60)
            waitForIdle()
        }
        assertEquals(settled, backdrop!!.captures, "static content and unrelated state must not trigger self-capture")
    }

    @Test fun disabledProducerAllocatesNoSoftwarePipeline() = runComposeUiTest {
        var producer: TvBackdrop? = null
        setContent { producer = rememberTvBackdrop(remember { TvScene() }, "mobile", false) }
        runOnIdle { assertNull(producer) }
    }
}

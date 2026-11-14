@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.model.home.Content
import com.maxrave.simpmusic.ui.component.*
import com.maxrave.simpmusic.ui.screen.home.HomeAccountButton
import com.maxrave.simpmusic.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.*

class TvPhysicalGeometryTest {
    private val sourceColor = Color(.16f, .28f, .42f)

    @Test fun localPlaylistArtworkOwnsRimInsteadOfAutoBacking() = cardRim("local")
    @Test fun remotePlaylistArtworkOwnsRimInsteadOfAutoBacking() = cardRim("playlist")
    @Test fun songArtworkOwnsRimInsteadOfAutoBacking() = cardRim("song")
    @Test fun videoArtworkOwnsRimInsteadOfAutoBacking() = cardRim("video")
    @Test fun artistArtworkOwnsCircularRimInsteadOfAutoBacking() = cardRim("artist")
    @Test fun actualPlaylistNativeFocusDoesNotWashTheTextGutter() = cardRim("local", nativeOnly = true)

    private fun cardRim(kind: String, nativeOnly: Boolean = false) = runComposeUiTest {
        var producer: TvBackdrop? = null
        var clicks = 0
        val title = "Synthetic $kind"
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f), LocalTvDevice provides true,
                LocalTvMotion provides resolveTvMotion(0f)) {
                AppTheme {
                    val scene = remember { TvScene() }
                    val backdrop = rememberTvBackdrop(scene, kind, true)
                    producer = backdrop
                    CompositionLocalProvider(LocalTvScene provides scene, LocalTvBackdropOverlay provides backdrop) {
                        Box(Modifier.size(420.dp, 320.dp).background(sourceColor).testTag("scene")) {
                            Box(Modifier.fillMaxSize().tvBackdropSource(backdrop).background(sourceColor)) {
                                Box(Modifier.padding(start = 30.dp, top = 20.dp)
                                    .width(if (kind == "video") 288.dp else 168.dp).heightIn(max = 270.dp)) {
                                    val data = Content(null, null, "Synthetic subtitle", false, "playlist", null, emptyList(), title, "track", null)
                                    when (kind) {
                                        "local" -> HomeItemContentPlaylist({ clicks++ }, LocalPlaylistEntity(title = title))
                                        "playlist" -> HomeItemContentPlaylist({ clicks++ }, data)
                                        "song" -> HomeItemSong({ clicks++ }, {}, data)
                                        "video" -> HomeItemVideo({ clicks++ }, {}, data)
                                        else -> HomeItemArtist({ clicks++ }, data)
                                    }
                                }
                            }
                            TvBackdropOverlays(backdrop, Modifier.matchParentSize())
                        }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 10000) { producer?.frame != null }
        val target = onNode(hasClickAction() and hasText(title))
        onAllNodes(hasClickAction()).assertCountEquals(1)
        target.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        val pixels = onNodeWithTag("scene").captureToImage().toPixelMap()
        val bounds = target.fetchSemanticsNode().boundsInRoot
        val scale = focusScaleFor(TvFocusStyle.Card)
        val artWidth = if (kind == "video") TvDimens.WideCardWidth.value else TvDimens.SquareCard.value
        val artHeight = if (kind == "video") TvDimens.WideCardHeight.value else TvDimens.SquareCard.value
        val artRight = bounds.left + (10f + artWidth) * 2f * scale
        val artMiddle = bounds.top + (10f + artHeight / 2f) * 2f * scale
        val imageRim = (0..3).maxOf { offset -> delta(pixels[(artRight + offset).roundToInt(), artMiddle.roundToInt()], sourceColor) }
        val nativeWash = delta(pixels[(bounds.left + 16).roundToInt(), (bounds.bottom - 18).roundToInt()], sourceColor)
        println("physicalGeometry kind=$kind nativeOutsideArtworkDelta=$nativeWash")
        if (nativeOnly) {
            assertTrue(nativeWash < .006f, "native state wash must not cover the text gutter: $nativeWash")
            return@runComposeUiTest
        }
        assertTrue(imageRim > .10f, "$kind artwork rim missing at actual image edge: $imageRim; target=$bounds")
        val textSlotRim = delta(pixels[bounds.center.x.roundToInt(), (bounds.bottom - 2).roundToInt()], sourceColor)
        assertTrue(textSlotRim < .09f, "$kind rim must not include bottom/title slot: $textSlotRim; target=$bounds")
        assertTrue(nativeWash < .006f, "native state wash must not cover the text gutter: $nativeWash")
        target.performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun actualAvatarIndicationStaysInsideCircle() = runComposeUiTest {
        var producer: TvBackdrop? = null
        val requester = FocusRequester()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f), LocalTvDevice provides true,
                LocalTvMotion provides resolveTvMotion(0f)) {
                AppTheme {
                    val scene = remember { TvScene() }
                    val backdrop = rememberTvBackdrop(scene, "avatar", true)
                    producer = backdrop
                    val controller = rememberNavController()
                    CompositionLocalProvider(LocalTvScene provides scene, LocalTvBackdropOverlay provides backdrop) {
                        Box(Modifier.size(160.dp, 120.dp).background(sourceColor).testTag("avatarScene")) {
                            Box(Modifier.fillMaxSize().tvBackdropSource(backdrop).background(sourceColor)) {
                                Box(Modifier.padding(30.dp)) {
                                    HomeAccountButton(null, null, controller, Modifier.focusRequester(requester))
                                }
                            }
                            TvBackdropOverlays(backdrop, Modifier.matchParentSize())
                        }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 10000) { producer?.frame != null }
        onAllNodes(hasClickAction()).assertCountEquals(1)
        val before = onNodeWithTag("avatarScene").captureToImage().toPixelMap()
        runOnIdle { requester.requestFocus() }
        mainClock.advanceTimeBy(1000)
        val target = onNode(hasClickAction()).assertIsFocused().assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        val bounds = target.fetchSemanticsNode().boundsInRoot
        val after = onNodeWithTag("avatarScene").captureToImage().toPixelMap()
        val cornerX = (bounds.left + 4).roundToInt()
        val cornerY = (bounds.top + 4).roundToInt()
        assertTrue(delta(after[cornerX, cornerY], before[cornerX, cornerY]) < .006f,
            "native focus indication must not paint the square avatar corner: ${before[cornerX, cornerY]} -> ${after[cornerX, cornerY]}")
        target.performKeyInput { pressKey(Key.DirectionCenter) }
        onNodeWithText("Notification").assertExists()
    }

    @Test fun actualSettingsIndicationsKeepComputedCornersAtDensityTwo() = settingsCorners(2f)
    @Test fun actualSettingsIndicationsKeepComputedCornersAtDensityThree() = settingsCorners(3f)
    @Test fun nestedSettingsRowUsesOwningGroupInsteadOfItsColumn() = settingsCorners(2f, nested = true)

    private fun settingsCorners(density: Float, nested: Boolean = false) = runComposeUiTest {
        var producer: TvBackdrop? = null
        var single by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var checked by mutableStateOf(false)
        var toggles = 0
        var nestedVisible by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density), LocalTvDevice provides true,
                LocalTvMotion provides resolveTvMotion(0f)) {
                AppTheme {
                    val scene = remember { TvScene() }
                    val backdrop = rememberTvBackdrop(scene, single to enabled, true)
                    producer = backdrop
                    CompositionLocalProvider(LocalTvScene provides scene, LocalTvBackdropOverlay provides backdrop) {
                        Box(Modifier.width(260.dp).height(250.dp).background(sourceColor).testTag("settingsScene")) {
                            Box(Modifier.fillMaxSize().tvBackdropSource(backdrop).background(sourceColor)) {
                                SettingGroup("Synthetic group") {
                                    if (single) SettingItem("Single", "One", isEnable = enabled, onClick = {})
                                    else {
                                        SettingItem("First", "One", onClick = {})
                                        if (nested) {
                                            AnimatedVisibility(nestedVisible, enter = EnterTransition.None, exit = ExitTransition.None) {
                                                Column { SettingItem("Middle", "Two", switch = checked to { checked = it; toggles++ }) }
                                            }
                                        } else SettingItem("Middle", "Two", switch = checked to { checked = it; toggles++ })
                                        SettingItem("Last", "Three", onClick = {})
                                    }
                                }
                            }
                            TvBackdropOverlays(backdrop, Modifier.matchParentSize())
                        }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 10000) { producer?.frame != null }
        fun assertCorners(title: String, top: Boolean, bottom: Boolean) {
            val target = onNode(hasClickAction() and hasText(title))
            val before = onNodeWithTag("settingsScene").captureToImage().toPixelMap()
            target.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            mainClock.advanceTimeBy(1000)
            val bounds = target.fetchSemanticsNode().boundsInRoot
            val after = onNodeWithTag("settingsScene").captureToImage().toPixelMap()
            val xs = listOf((bounds.left + 2).roundToInt(), (bounds.right - 3).roundToInt())
            val ys = buildList { if (top) add((bounds.top + 2).roundToInt()); if (bottom) add((bounds.bottom - 3).roundToInt()) }
            for (x in xs) for (y in ys) assertTrue(delta(after[x, y], before[x, y]) < .006f,
                "$title density$density native wash escaped rounded corner($x,$y): ${before[x, y]} -> ${after[x, y]}")
        }
        assertCorners("First", top = true, bottom = false)
        assertCorners("Last", top = false, bottom = true)
        val middle = onNode(hasClickAction() and hasText("Middle"))
        val beforeMiddle = onNodeWithTag("settingsScene").captureToImage().toPixelMap()
        middle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        mainClock.advanceTimeBy(1000)
        if (nested) {
            val bounds = middle.fetchSemanticsNode().boundsInRoot
            val afterMiddle = onNodeWithTag("settingsScene").captureToImage().toPixelMap()
            val x = (bounds.left + 2).roundToInt()
            for (y in listOf((bounds.top + 2).roundToInt(), (bounds.bottom - 3).roundToInt())) {
                // The focus sheen fades toward the bottom (about .016 at the bottom corner, .03+ at
                // the top); a rounded corner measures under .006, as the First/Last rows above assert.
                assertTrue(delta(afterMiddle[x, y], beforeMiddle[x, y]) > .01f,
                    "nested middle row must have square group-interior corners: $x,$y")
            }
        }
        middle.performKeyInput { pressKey(Key.DirectionCenter) }
        middle.assertIsOn()
        runOnIdle { assertEquals(1, toggles) }
        if (nested) {
            runOnIdle { nestedVisible = false }
            onNodeWithText("Middle").assertDoesNotExist()
            assertCorners("Last", top = false, bottom = true)
        }
        runOnIdle { single = true }
        waitUntil(timeoutMillis = 10000) { producer?.frame != null }
        assertCorners("Single", top = true, bottom = true)
        runOnIdle { enabled = false }
        onAllNodes(hasClickAction()).assertCountEquals(0)
        onNodeWithText("Single").assertExists()
    }

    private fun delta(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
}

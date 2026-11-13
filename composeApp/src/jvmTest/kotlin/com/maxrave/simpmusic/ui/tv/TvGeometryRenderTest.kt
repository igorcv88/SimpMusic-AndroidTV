@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import com.maxrave.simpmusic.ui.component.HeartCheckBox
import com.maxrave.simpmusic.ui.component.settingRowShape
import com.maxrave.simpmusic.ui.component.SettingGroup
import com.maxrave.simpmusic.ui.component.SettingItem
import kotlin.test.*

class TvGeometryRenderTest {
    @Test fun renderedSettingGroupKeepsRoundedEdgesAtDensityThree() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f), LocalTvDevice provides true,
                LocalTvMotion provides resolveTvMotion(0f)) {
                MaterialTheme {
                    Box(Modifier.width(260.dp).background(Color.Black)) {
                        SettingGroup("Settings") {
                            SettingItem(title = "First", subtitle = "One", onClick = {})
                            SettingItem(title = "Last", subtitle = "Two", onClick = {})
                        }
                    }
                }
            }
        }
        val first = onNode(hasClickAction() and hasText("First"))
        val firstBefore = first.captureToImage().toPixelMap()[1, 1]
        first.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val firstAfter = first.captureToImage().toPixelMap()[1, 1]
        assertTrue(kotlin.math.abs(firstAfter.red - firstBefore.red) < .03f, "first outer corner must remain rounded: $firstBefore -> $firstAfter")
        val last = onNode(hasClickAction() and hasText("Last"))
        val before = last.captureToImage().toPixelMap().let { it[1, it.height - 2] }
        last.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val after = last.captureToImage().toPixelMap().let { it[1, it.height - 2] }
        assertTrue(kotlin.math.abs(after.red - before.red) < .03f, "last outer corner must remain rounded: $before -> $after")
    }

    @Test fun heartHasOne48dpTargetAndEqual24dpGlyphs() = runComposeUiTest {
        var checked by mutableStateOf(false)
        var clicks = 0
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTvDevice provides true, LocalTvMotion provides resolveTvMotion(0f)) {
                    HeartCheckBox(size = 48, checked = checked, onStateChange = { checked = !checked; clicks++ })
                }
            }
        }
        onAllNodes(hasClickAction()).assertCountEquals(1)
        onNode(hasClickAction()).assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        onNodeWithContentDescription("Favorite unchecked", useUnmergedTree = true).assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        onNode(hasClickAction()).performClick()
        mainClock.advanceTimeBy(1000)
        onNodeWithContentDescription("Favorite checked", useUnmergedTree = true).assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun settingRowsMatchFirstMiddleLastGroupCorners() {
        val density = Density(1f)
        fun outline(first: Boolean, last: Boolean) = settingRowShape(first, last).createOutline(Size(300f, 80f), LayoutDirection.Ltr, density)
        val first = assertIs<Outline.Rounded>(outline(true, false)).roundRect
        assertEquals(19f, first.topLeftCornerRadius.x)
        assertEquals(19f, first.topRightCornerRadius.x)
        assertEquals(0f, first.bottomLeftCornerRadius.x)
        assertEquals(0f, first.bottomRightCornerRadius.x)
        val middle = assertIs<Outline.Rectangle>(outline(false, false)).rect
        assertEquals(300f, middle.width)
        assertEquals(80f, middle.height)
        val last = assertIs<Outline.Rounded>(outline(false, true)).roundRect
        assertEquals(0f, last.topLeftCornerRadius.x)
        assertEquals(0f, last.topRightCornerRadius.x)
        assertEquals(19f, last.bottomLeftCornerRadius.x)
        assertEquals(19f, last.bottomRightCornerRadius.x)
        assertEquals(19f, assertIs<Outline.Rounded>(outline(true, true)).roundRect.bottomLeftCornerRadius.x)
    }

    @Test fun artworkRimFollowsTheActualAnchoredClipInsideIndependentGutter() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalTvDevice provides true, LocalTvMotion provides resolveTvMotion(0f)) {
                val requester = remember { FocusRequester() }
                Box(Modifier.size(160.dp, 150.dp).testTag("card").background(Color.Black)) {
                    Column(Modifier.padding(10.dp).focusRequester(requester).tvFocus(TvFocusStyle.Card, RoundedCornerShape(12.dp)).focusable()) {
                        Box(Modifier.size(100.dp).tvFocusGlowAnchor().clip(RoundedCornerShape(12.dp)).background(Color.Red))
                        Spacer(Modifier.height(30.dp))
                    }
                }
                LaunchedEffect(Unit) { requester.requestFocus() }
            }
        }
        val pixels = onNodeWithTag("card").captureToImage().toPixelMap()
        // Rim follows the 100dp artwork, while the text and the 10dp layout gutter stay outside.
        val artworkRight = (110f + (100f * (focusScaleFor(TvFocusStyle.Card) - 1f) / 2)).toInt()
        assertTrue(pixels[artworkRight.coerceAtMost(pixels.width - 1), 55].red > .1f)
        assertTrue(pixels[125, 55].red < .03f, "rim must not follow the wider card/text slot")
        assertTrue(pixels[55, 130].red < .03f, "rim must not include the text slot")
    }
}

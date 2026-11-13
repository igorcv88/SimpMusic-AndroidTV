@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import com.maxrave.simpmusic.ui.screen.player.TvRenditionSwitchControl
import com.maxrave.simpmusic.viewModel.TvRenditionSwitchPhase
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.tv_switch_music_video
import simpmusic.composeapp.generated.resources.tv_switch_song
import simpmusic.composeapp.generated.resources.tv_rendition_pending
import simpmusic.composeapp.generated.resources.tv_rendition_error
import kotlin.test.Test
import kotlin.test.assertEquals

class TvRenditionSwitchControlTest {
    @Test fun dpadActivatesOneSwitchAndPendingKeepsFocusAndCommittedLabel() = runComposeUiTest {
        val videoLabel = runBlocking { getString(Res.string.tv_switch_music_video) }
        val songLabel = runBlocking { getString(Res.string.tv_switch_song) }
        val pendingLabel = runBlocking { getString(Res.string.tv_rendition_pending) }
        val errorLabel = runBlocking { getString(Res.string.tv_rendition_error) }
        var requests = 0
        var phase by mutableStateOf(TvRenditionSwitchPhase.Idle)
        var video by mutableStateOf(false)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTvDevice provides true) {
                    val focus = remember { FocusRequester() }
                    Column {
                        TvRenditionSwitchControl(video, phase, { requests++; phase = TvRenditionSwitchPhase.Pending }, Modifier.focusRequester(focus))
                        Button({}, Modifier.testTag("next-control")) { Text("Next control") }
                    }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                }
            }
        }
        onNodeWithText(videoLabel).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { assertEquals(1, requests) }
        onNodeWithText(videoLabel).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { assertEquals(1, requests) }
        onNodeWithText(pendingLabel).assertExists()
        runOnIdle { phase = TvRenditionSwitchPhase.Error }
        onNodeWithText(errorLabel).assertExists()
        onNodeWithText(videoLabel).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { assertEquals(2, requests); phase = TvRenditionSwitchPhase.Idle; video = true }
        onNodeWithText(songLabel).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag("next-control").assertIsFocused()
    }
}

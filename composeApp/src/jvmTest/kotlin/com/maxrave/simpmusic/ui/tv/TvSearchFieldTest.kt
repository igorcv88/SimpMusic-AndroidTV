@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals

class TvSearchFieldTest {
    @Test
    fun focusingSearchStartsRealEditorSessionAndAcceptsText() = runComposeUiTest {
        var inputSessions = 0
        var submitted: String? = null
        val keyboard = RecordingKeyboard()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTvDevice provides true, LocalSoftwareKeyboardController provides keyboard) {
                    InterceptPlatformTextInput(interceptor = { _, _ ->
                        inputSessions++
                        awaitCancellation()
                    }) {
                        val requester = remember { FocusRequester() }
                        Column {
                            TvSearchField(
                                state = rememberTextFieldState(),
                                placeholder = "Search",
                                modifier = Modifier.testTag("editor").focusRequester(requester),
                                onActivate = {},
                                onSearch = { submitted = it },
                            )
                            Button(onClick = {}, modifier = Modifier.testTag("results")) { Text("Results") }
                        }
                        LaunchedEffect(Unit) { requester.requestFocus() }
                    }
                }
            }
        }
        onNodeWithTag("editor").assertIsFocused()
        waitUntil { inputSessions > 0 }
        onNodeWithTag("editor").performTextInput("test query")
        onNodeWithTag("editor").assertTextContains("test query")
        // Model the system hiding IME on Back while retaining editor focus, then press OK.
        // Android's actual Back/IME integration still needs physical acceptance.
        runOnIdle { keyboard.hide() }
        val previousShows = keyboard.shows
        onNodeWithTag("editor").performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { assertEquals(previousShows + 1, keyboard.shows) }
        onNodeWithTag("editor").assertIsFocused().assertTextContains("test query")
        onNodeWithTag("editor").performImeAction()
        runOnIdle { assertEquals("test query", submitted) }
        onNodeWithTag("editor").performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag("results").assertIsFocused()
    }

    private class RecordingKeyboard : SoftwareKeyboardController {
        var shows = 0
        override fun show() { shows++ }
        override fun hide() = Unit
    }
}

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.random.Random

/** Session owner; destination identities are NavBackStackEntry ids, never route names. */
class TvNavigationState {
    val railFocus = FocusRequester()
    var railHasFocus by mutableStateOf(false)
    private var activeDestination: String? = null
    private val contentReady = mutableMapOf<String, Boolean>()
    fun prepare(destination: String, ready: Boolean) { contentReady[destination] = ready }
    fun contentReady(destination: String, ready: Boolean) { contentReady[destination] = ready }
    private val saved = mutableMapOf<String, String>()
    private val targets = mutableMapOf<String, MutableMap<String, FocusRequester>>()
    private var pending: String? = null
    fun hasSavedFocus(destination: String?): Boolean = destination != null && saved.containsKey(destination)
    fun focused(destination: String, key: String) { saved[destination] = key }
    fun resume(destination: String) { activeDestination = destination; pending = destination }
    fun restoreContent(): Boolean {
        val destination = activeDestination ?: return false
        resume(destination)
        return restoreReady(destination)
    }
    fun attach(destination: String, key: String, requester: FocusRequester) {
        targets.getOrPut(destination) { mutableMapOf() }[key] = requester
        restore(destination, key, requester)
    }
    fun detach(destination: String, key: String) { targets[destination]?.remove(key) }
    private fun restore(destination: String, key: String, requester: FocusRequester) {
        if (pending == destination && saved[destination] == key) {
            pending = null
            if (!requester.requestFocus()) pending = destination
        }
    }
    fun restoreReady(destination: String): Boolean {
        if (pending != destination) return false
        val entries = targets[destination] ?: return false
        val key = saved[destination] ?: return false
        if (key !in entries && contentReady[destination] != true) return false
        val resolved = resolveTvFocusKey(key, entries.keys) ?: return false
        val requester = entries.getValue(resolved)
        pending = null
        return requester.requestFocus()
    }
}

val LocalTvNavigation = staticCompositionLocalOf<TvNavigationState?> { null }
val LocalTvDestination = staticCompositionLocalOf<String?> { null }

@Composable
fun TvDestination(id: String, root: Boolean = false, waitForContent: Boolean = false, content: @Composable () -> Unit) {
    if (!LocalTvDevice.current) { content(); return }
    val navigation = LocalTvNavigation.current
    val owner = LocalLifecycleOwner.current
    remember(id, navigation) { navigation?.prepare(id, !waitForContent); true }
    DisposableEffect(owner, id, navigation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                navigation?.resume(id)
                navigation?.restoreReady(id)
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) navigation?.resume(id)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val keyboardVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Registered after NavHost, before child content: dialogs and IME retain BACK priority.
    com.maxrave.simpmusic.expect.ui.PlatformBackHandler(enabled = root && !keyboardVisible) {
        if (navigation?.railHasFocus == true) navigation.restoreContent()
        else navigation?.railFocus?.requestFocus()
    }
    CompositionLocalProvider(LocalTvDestination provides id) {
        Box(Modifier.focusGroup().onGloballyPositioned {
            if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) navigation?.restoreReady(id)
        }) { content() }
    }
}

/** Stable through destination disposal and lazy keyed item recreation; restored only when laid out. */
fun Modifier.tvRememberFocus(): Modifier = composed {
    val navigation = LocalTvNavigation.current
    val destination = LocalTvDestination.current
    if (!LocalTvDevice.current || navigation == null || destination == null) return@composed this
    val key = rememberSaveable { Random.nextLong().toString() }
    val requester = remember { FocusRequester() }
    DisposableEffect(navigation, destination, key) {
        onDispose { navigation.detach(destination, key) }
    }
    this.focusRequester(requester)
        .onFocusChanged { if (it.isFocused) navigation.focused(destination, key) }
        .onGloballyPositioned { navigation.attach(destination, key, requester) }
}

/** A removed target falls back to the first ready control in its own destination. */
internal fun resolveTvFocusKey(saved: String?, ready: Set<String>): String? =
    if (saved == null) null else saved.takeIf { it in ready } ?: ready.firstOrNull()

@Composable
fun TvContentReady(ready: Boolean) {
    val navigation = LocalTvNavigation.current
    val destination = LocalTvDestination.current
    SideEffect { if (destination != null) navigation?.contentReady(destination, ready) }
}

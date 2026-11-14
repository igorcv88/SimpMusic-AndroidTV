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
class TvNavigationState(private val request: (FocusRequester) -> Boolean = { it.requestFocus() }) {
    private var handoffDestination: String? = null
    val railFocus = FocusRequester()
    var railHasFocus by mutableStateOf(false)
    private var activeDestination: String? = null
    private val contentReady = mutableMapOf<String, Boolean>()
    fun prepare(destination: String, ready: Boolean) { contentReady[destination] = ready }
    fun contentReady(destination: String, ready: Boolean) {
        contentReady[destination] = ready
        if (ready) { restoreReady(destination); enterIfFresh(destination) }
    }
    private val saved = mutableMapOf<String, String>()
    private val targets = mutableMapOf<String, MutableMap<String, FocusRequester>>()
    private var pending: String? = null
    private val entries = mutableMapOf<String, FocusRequester>()
    private val fallbackEntries = mutableMapOf<String, FocusRequester>()
    private val entered = mutableSetOf<String>()
    fun registerEntry(destination: String, requester: FocusRequester, fallback: Boolean = false) {
        (if (fallback) fallbackEntries else entries)[destination] = requester
    }
    /** Explicit rail activation may leave the rail; incidental recomposition must never do so. */
    fun requestContentFocus(destination: String): Boolean {
        handoffDestination = destination
        pending = destination
        return restoreReady(destination)
    }
    fun unregisterEntry(destination: String, requester: FocusRequester, fallback: Boolean = false) {
        val registry = if (fallback) fallbackEntries else entries
        if (registry[destination] === requester) registry.remove(destination)
    }

    /**
     * First visit only: puts focus on the destination's entry point once its content is ready.
     * Never runs while the rail holds focus (the user chose the rail) or when there is a remembered
     * element to restore, so it cannot compete with [restoreReady].
     */
    fun enterIfFresh(destination: String): Boolean {
        if (!shouldEnterFresh(destination in entered, activeDestination == destination, railHasFocus, destination in saved, contentReady[destination] == true)) return false
        val requester = entries[destination] ?: return false
        return request(requester).also { if (it) entered += destination }
    }
    fun hasSavedFocus(destination: String?): Boolean = destination != null && saved.containsKey(destination)
    fun focused(destination: String, key: String) { saved[destination] = key }
    fun resume(destination: String) {
        // Back can pop a destination while the rail still owns focus. Hand it to the resumed
        // page once ready; a layout/lifecycle refresh of the same entry must keep rail focus.
        if (railHasFocus && activeDestination != null && activeDestination != destination) {
            handoffDestination = destination
        }
        activeDestination = destination
        pending = destination
    }
    fun restoreContent(): Boolean {
        val destination = activeDestination ?: return false
        return requestContentFocus(destination)
    }
    fun attach(destination: String, key: String, requester: FocusRequester) {
        targets.getOrPut(destination) { mutableMapOf() }[key] = requester
        restoreReady(destination)
    }
    fun detach(destination: String, key: String) { targets[destination]?.remove(key) }
    fun restoreReady(destination: String): Boolean {
        if (pending != destination || activeDestination != destination) return false
        if (handoffDestination == destination) {
            if (contentReady[destination] != true) return false
            val target = saved[destination]?.let { targets[destination]?.get(it) }
                ?: entries[destination] ?: fallbackEntries[destination] ?: targets[destination]?.values?.firstOrNull() ?: return false
            if (!request(target)) return false
            pending = null
            handoffDestination = null
            entered += destination
            return true
        }
        if (railHasFocus) return false
        val entries = targets[destination] ?: return false
        val key = saved[destination] ?: return false
        if (key !in entries && contentReady[destination] != true) return false
        val resolved = resolveTvFocusKey(key, entries.keys) ?: return false
        val requester = entries.getValue(resolved)
        if (!request(requester)) return false
        pending = null
        return true
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
    // BACK from a tab's content opens the rail. With the rail already focused the handler is off,
    // so BACK follows the back stack: another tab returns to Home, Home leaves the app.
    com.maxrave.simpmusic.expect.ui.PlatformBackHandler(enabled = root && !keyboardVisible && navigation?.railHasFocus != true) {
        navigation?.railFocus?.requestFocus()
    }
    CompositionLocalProvider(LocalTvDestination provides id) {
        Box(Modifier.tvDestinationEntry(fallback = true).focusGroup().onGloballyPositioned {
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

/** Marks the element (or group) that takes focus the first time its destination is shown. */
fun Modifier.tvDestinationEntry(fallback: Boolean = false): Modifier = composed {
    val navigation = LocalTvNavigation.current
    val destination = LocalTvDestination.current
    if (!LocalTvDevice.current || navigation == null || destination == null) return@composed this
    val requester = remember { FocusRequester() }
    DisposableEffect(navigation, destination) {
        navigation.registerEntry(destination, requester, fallback)
        onDispose { navigation.unregisterEntry(destination, requester, fallback) }
    }
    this.focusRequester(requester).onGloballyPositioned { navigation.enterIfFresh(destination) }
}

internal fun shouldEnterFresh(
    alreadyEntered: Boolean,
    active: Boolean,
    railHasFocus: Boolean,
    hasSavedFocus: Boolean,
    contentReady: Boolean,
): Boolean = !alreadyEntered && active && !railHasFocus && !hasSavedFocus && contentReady

/** A removed target falls back to the first ready control in its own destination. */
internal fun resolveTvFocusKey(saved: String?, ready: Set<String>): String? =
    if (saved == null) null else saved.takeIf { it in ready } ?: ready.firstOrNull()

@Composable
fun TvContentReady(ready: Boolean) {
    val navigation = LocalTvNavigation.current
    val destination = LocalTvDestination.current
    SideEffect { if (destination != null) navigation?.contentReady(destination, ready) }
}

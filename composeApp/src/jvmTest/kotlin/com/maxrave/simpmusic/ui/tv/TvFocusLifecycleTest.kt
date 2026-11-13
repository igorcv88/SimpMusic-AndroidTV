package com.maxrave.simpmusic.ui.tv

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import java.lang.reflect.Proxy
import kotlin.test.Test

class TvFocusLifecycleTest {
    @Test
    fun latePlacementOnDetachedGlowAnchorDoesNotTraverseAncestors() {
        // Exercise the actual modifier callback, without an owner or a synthetic safety helper.
        // A late placement must return before any traversal, even if its coordinates still exist.
        val constructor = Class.forName("com.maxrave.simpmusic.ui.tv.TvGlowAnchorNode").getDeclaredConstructor()
        constructor.isAccessible = true
        val node = constructor.newInstance() as LayoutAwareModifierNode
        val coordinates = Proxy.newProxyInstance(
            LayoutCoordinates::class.java.classLoader,
            arrayOf(LayoutCoordinates::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "isAttached" -> true
                else -> error("Detached node must not read ${method.name}")
            }
        } as LayoutCoordinates

        node.onPlaced(coordinates)
    }
}

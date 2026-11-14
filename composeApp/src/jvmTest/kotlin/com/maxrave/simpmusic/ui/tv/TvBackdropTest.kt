package com.maxrave.simpmusic.ui.tv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvBackdropTest {
    @Test fun generationRejectsAReplacementSourceRoot() {
        val gate = TvBackdropGate()
        val first = Any()
        gate.reset("home", Size(100f, 80f), first)
        val generation = gate.generation
        assertFalse(gate.reset("home", Size(100f, 80f), first))
        assertTrue(gate.reset("home", Size(100f, 80f), Any()))
        assertFalse(gate.accepts(generation))
    }
    @Test fun generationRejectsObsoleteRouteAndSize() {
        val gate = TvBackdropGate()
        gate.reset("home", Size(800f, 400f))
        val home = gate.generation
        gate.reset("search", Size(800f, 400f))
        assertFalse(gate.accepts(home))
        val search = gate.generation
        gate.reset("search", Size(600f, 400f))
        assertFalse(gate.accepts(search))
        assertTrue(gate.accepts(gate.generation))
    }

    @Test fun throttleCoalescesWithoutPollingFrames() {
        val gate = TvBackdropGate()
        assertEquals(0L, gate.delayMillis(1000))
        gate.started(1000)
        assertEquals(240L, gate.delayMillis(1010))
        assertEquals(0L, gate.delayMillis(1300))
    }

    @Test fun sourceCoordinatesFollowContentInsetAndClampOutsideRail() {
        val source = backdropImage(intArrayOf(0xFFFF0000.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0xFF0000FF.toInt()), 4, 1)
        val output = ImageBitmap(160, 40)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(output), Size(160f, 40f)) {
            drawBackdropFrame(TvBackdropFrame(source, Offset(80f, 0f), Size(80f, 40f), 1), Offset.Zero, size, 1f)
        }
        val pixels = output.toPixelMap()
        assertTrue(pixels[20, 20].red > .95f, "outside rail extends the nearest source edge")
        assertTrue(pixels[100, 20].red > .95f, "partial overlap must keep source alignment")
        assertTrue(pixels[140, 20].blue > .95f)
    }
}

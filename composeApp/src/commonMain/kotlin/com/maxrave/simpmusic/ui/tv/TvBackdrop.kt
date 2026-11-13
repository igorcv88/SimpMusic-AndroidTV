package com.maxrave.simpmusic.ui.tv

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.maxrave.logger.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** Route/size identity is separate from content updates: a busy animation cannot starve publication. */
internal class TvBackdropGate {
    var generation = 0L
        private set
    private var route: Any? = null
    private var size = Size.Zero
    private var lastStart: Long? = null
    fun reset(route: Any?, size: Size): Boolean {
        if (this.route == route && this.size == size) return false
        this.route = route
        this.size = size
        generation++
        return true
    }
    fun accepts(generation: Long) = this.generation == generation
    fun started(now: Long) { lastStart = now }
    fun delayMillis(now: Long): Long = lastStart?.let { (250L - (now - it)).coerceAtLeast(0) } ?: 0L
}

internal data class TvBackdropFrame(val image: ImageBitmap, val origin: Offset, val size: Size, val generation: Long)

/** Clamp outside the source without stretching its interior when the rail partly overlaps it. */
internal fun DrawScope.drawBackdropFrame(frame: TvBackdropFrame, origin: Offset, size: Size, alpha: Float) {
    data class Span(val source: Int, val count: Int, val destination: Int, val length: Int)
    fun spans(start: Float, length: Float, source: Float, extent: Float, pixels: Int): List<Span> {
        val end = start + length
        val cuts = listOf(start, source.coerceIn(start, end), (source + extent).coerceIn(start, end), end).distinct().sorted()
        return cuts.zipWithNext().mapNotNull { (a, b) ->
            val dest = (a - start).roundToInt()
            val width = (b - start).roundToInt() - dest
            if (width <= 0) null else {
                val first = ((a - source) / extent * pixels).roundToInt().coerceIn(0, pixels - 1)
                val last = ((b - source) / extent * pixels).roundToInt().coerceIn(first + 1, pixels)
                Span(first, last - first, dest, width)
            }
        }
    }
    val horizontal = spans(origin.x, size.width, frame.origin.x, frame.size.width, frame.image.width)
    val vertical = spans(origin.y, size.height, frame.origin.y, frame.size.height, frame.image.height)
    for (x in horizontal) for (y in vertical) {
        drawImage(frame.image, srcOffset = IntOffset(x.source, y.source), srcSize = IntSize(x.count, y.count),
            dstOffset = IntOffset(x.destination, y.destination), dstSize = IntSize(x.length, y.length), alpha = alpha, filterQuality = FilterQuality.Low)
    }
}

/** Only content descendants receive this local. The rail is a sibling and is never recorded. */
internal val LocalTvBackdropOverlay = staticCompositionLocalOf<TvBackdrop?> { null }

@Stable
internal class TvBackdrop(val source: GraphicsLayer, val sampled: GraphicsLayer) {
    internal val frameState = mutableStateOf<TvBackdropFrame?>(null)
    val frame get() = frameState.value
    val gate = TvBackdropGate()
    val requests = Channel<Unit>(Channel.CONFLATED)
    fun request() {
        requests.trySend(Unit)
    }
    val reads = SnapshotStateObserver { it() }
    private val contentChanged: (Any) -> Unit = { request() }
    fun observeContent(owner: Any, block: () -> Unit) = reads.observeReads(owner, contentChanged, block)
    val overlays = linkedMapOf<Any, DrawScope.() -> Unit>()
    val overlayVersion = mutableIntStateOf(0)
    fun overlayChanged() {
        // Source draw callbacks must never subscribe to their own overlay notification.
        Snapshot.withoutReadObservation { overlayVersion.intValue++ }
    }
    var origin = Offset.Zero
    var size = Size.Zero
    var route: Any? = null
    var recorded = false
    var density: Density = Density(1f)
    var direction = LayoutDirection.Ltr
    var captures = 0
        private set
    var failures = 0
        private set

    fun reset(route: Any?, size: Size = this.size) {
        this.route = route
        this.size = size
        if (gate.reset(route, size)) {
            recorded = false
            frameState.value = null
            request()
        }
    }

    suspend fun produce() {
        val clock = TimeSource.Monotonic.markNow()
        for (ignored in requests) {
            delay(gate.delayMillis(clock.elapsedNow().inWholeMilliseconds))
            // One conflated request represents every invalidation since the previous readback.
            while (requests.tryReceive().isSuccess) { /* coalesce */ }
            if (!recorded || size.width < 1f || size.height < 1f) continue
            gate.started(clock.elapsedNow().inWholeMilliseconds)
            val generation = gate.generation
            // Snapshot callbacks can precede cached child-layer recording. Let that frame finish
            // before reading its display list; this is on-demand, never a frame polling loop.
            withFrameNanos { }
            withFrameNanos { }
            if (!recorded || !gate.accepts(generation)) continue
            val sourceSize = size
            val sourceOrigin = origin
            val scale = (256f / max(sourceSize.width, sourceSize.height)).coerceAtMost(1f)
            val small = IntSize(max(1, (sourceSize.width * scale).roundToInt()), max(1, (sourceSize.height * scale).roundToInt()))
            val start = TimeSource.Monotonic.markNow()
            try {
                sampled.record(density, direction, small) {
                    scale(scale, scale, pivot = Offset.Zero) { drawLayer(source) }
                }
                val image = sampled.toImageBitmap()
                val captureMs = start.elapsedNow().inWholeMilliseconds
                val blurStart = TimeSource.Monotonic.markNow()
                val blurred = withContext(Dispatchers.Default) {
                    val pixels = IntArray(image.width * image.height)
                    image.readPixels(pixels)
                    val output = blurAmbientPixels(pixels, image.width, image.height, 4)
                    backdropImage(output, image.width, image.height)
                }
                currentCoroutineContext().ensureActive()
                val blurMs = blurStart.elapsedNow().inWholeMilliseconds
                // New content already has a conflated follow-up request. Publishing this bounded
                // sample prevents a busy animation from starving glass indefinitely.
                if (gate.accepts(generation) && origin == sourceOrigin) {
                    frameState.value = TvBackdropFrame(blurred, sourceOrigin, sourceSize, generation)
                }
                captures++
                if (captures == 1 || captures % 20 == 0) {
                    Logger.d("TvBackdrop", "capture=$captures readbackMs=$captureMs blurMs=$blurMs pixels=${small.width * small.height} generation=$generation")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures++
                // No route, media, account or exception message enters timing evidence.
                if (failures == 1 || failures % 20 == 0) Logger.w("TvBackdrop", "readbackFailure=$failures type=${failure::class.simpleName}")
            }
        }
    }
}

/** Platform bitmap upload; pixel read/blur/upload all happen on Default, never inside draw. */
internal expect fun backdropImage(pixels: IntArray, width: Int, height: Int): ImageBitmap

@Composable
internal fun rememberTvBackdrop(scene: TvScene?, route: Any?, enabled: Boolean): TvBackdrop? {
    if (!enabled) {
        SideEffect { scene?.backdrop = null }
        return null
    }
    val source = rememberGraphicsLayer()
    val sampled = rememberGraphicsLayer()
    val backdrop = remember(source, sampled) { TvBackdrop(source, sampled) }
    SideEffect {
        backdrop.reset(route)
        scene?.backdrop = backdrop
    }
    LaunchedEffect(backdrop) { backdrop.produce() }
    DisposableEffect(backdrop) {
        // Subscribe only to state read while drawing the isolated base. Player progress and
        // bitmap publication outside that base cannot keep a static Settings page capturing.
        backdrop.reads.start()
        onDispose {
            backdrop.reads.stop()
            backdrop.reads.clear()
            backdrop.overlays.clear()
            scene?.backdrop = null
        }
    }
    return backdrop
}

internal fun Modifier.tvBackdropSource(backdrop: TvBackdrop?): Modifier {
    if (backdrop == null) return this
    return graphicsLayer().onGloballyPositioned {
        if (it.isAttached) {
            val position = it.positionInRoot()
            if (backdrop.origin != position) {
                backdrop.origin = position
                backdrop.frameState.value = null
                backdrop.request()
            }
        }
    }.drawWithContent {
        backdrop.reset(backdrop.route, size)
        backdrop.density = Density(density, fontScale)
        backdrop.direction = layoutDirection
        backdrop.source.record { backdrop.observeContent(backdrop.source) { this@drawWithContent.drawContent() } }
        backdrop.recorded = true
        backdrop.request()
        backdrop.overlayChanged()
        drawLayer(backdrop.source)
    }
}

/** A separately invalidated sibling; publishing a frame cannot redraw or recapture the source. */
@Composable
internal fun TvBackdropOverlays(backdrop: TvBackdrop?, modifier: Modifier = Modifier) {
    if (backdrop == null) return
    Canvas(modifier.graphicsLayer()) {
        backdrop.overlayVersion.intValue
        // Standing glass containing a focusable child must render before that child's glass.
        backdrop.overlays.entries.sortedBy { entry ->
            val node = entry.key as? Modifier.Node
            var coordinates = node?.takeIf { it.isAttached }?.requireLayoutCoordinates()?.parentLayoutCoordinates
            var depth = 0
            while (coordinates != null) { depth++; coordinates = coordinates.parentLayoutCoordinates }
            depth
        }.forEach { it.value(this) }
    }
}

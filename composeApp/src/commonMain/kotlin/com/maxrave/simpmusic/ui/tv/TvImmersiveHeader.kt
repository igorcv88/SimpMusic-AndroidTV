package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.simpmusic.ui.component.rememberNowPlayingGlowTint
import com.maxrave.simpmusic.ui.theme.typo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.time.TimeSource

/** What the immersive header shows for one Home card. [id] must be stable for the same card. */
data class HomeHeaderItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
)

/**
 * Decides when focus has stayed on one item long enough to count. Pure: the caller supplies the
 * clock, so it is tested without one.
 *
 * Focusing the item that is already pending does not restart its wait; focusing another one does.
 */
class HeaderFocusDebouncer(
    private val delayMs: Long = 300,
) {
    private var pendingId: String? = null
    private var sinceMs = 0L

    fun onFocus(
        itemId: String,
        nowMs: Long,
    ) {
        if (itemId == pendingId) return
        pendingId = itemId
        sinceMs = nowMs
    }

    /** The pending item's id once focus has stayed on it for the delay, else null. */
    fun settled(nowMs: Long): String? = pendingId?.takeIf { nowMs - sinceMs >= delayMs }
}

/**
 * Carries focus from Home's cards to the header.
 *
 * A card reports itself through [onFocus] when it gains focus. The report goes into a conflated
 * channel that one coroutine drains with `collectLatest`: each report starts a wait of the debounce
 * delay, and a newer report cancels the wait in progress. [settled] is therefore written by a timer
 * that fires on its own when the delay ends — not by whatever happens to recompose next — and it is
 * snapshot state, so the header updates even if no other focus event ever arrives.
 *
 * While a D-pad key is held, focus moves about every 50 ms: every wait is cancelled before it ends,
 * [settled] does not change, and so the header neither flickers nor starts an image load for the
 * cards passed on the way.
 */
@Stable
class TvHeaderController internal constructor(
    private val delayMs: Long,
) {
    private val debouncer = HeaderFocusDebouncer(delayMs)
    private val reports = Channel<HomeHeaderItem>(Channel.CONFLATED)

    /** The card focus has rested on. Null until the first one settles. */
    var settled: HomeHeaderItem? by mutableStateOf(null)
        private set

    /** The tint of [settled]'s artwork, once resolved. Null while it is unknown. */
    var tint: Color? by mutableStateOf(null)
        internal set

    fun onFocus(item: HomeHeaderItem) {
        reports.trySend(item)
    }

    /** [tint], but only for the card it was taken from: a card never glows in another's colour. */
    fun tintFor(itemId: String?): Color? = if (itemId != null && settled?.id == itemId) tint else null

    internal suspend fun run() {
        val clock = TimeSource.Monotonic.markNow()
        reports.receiveAsFlow().collectLatest { item ->
            debouncer.onFocus(item.id, clock.elapsedNow().inWholeMilliseconds)
            delay(delayMs)
            // delay() may return a hair early against the monotonic clock; wait the remainder out.
            while (debouncer.settled(clock.elapsedNow().inWholeMilliseconds) != item.id) delay(DEBOUNCE_RECHECK_MS)
            if (settled != item) {
                tint = null
                settled = item
            }
        }
    }
}

/** Null outside Home on TV: cards then report nothing. */
val LocalTvHeaderController = staticCompositionLocalOf<TvHeaderController?> { null }

@Composable
fun rememberTvHeaderController(delayMs: Long = HEADER_DEBOUNCE_MS): TvHeaderController {
    val controller = remember(delayMs) { TvHeaderController(delayMs) }
    LaunchedEffect(controller) { controller.run() }
    return controller
}

/**
 * The TV side of a Home card, as [androidx.compose.ui.Modifier] plus whether the card is focused
 * (for its title colour). The modifier marks focus in the `Card` style and reports the card to the
 * header when it gains focus. Off TV it is an empty modifier and `false`.
 *
 * The glow takes the artwork's tint once the header has settled on this card and resolved it. No
 * card extracts a palette of its own: one artwork is analysed per settled focus, never while focus
 * is moving.
 *
 * @param item built only when the card gains focus.
 */
@Composable
fun rememberTvHomeCard(
    shape: Shape = TvShapes.Card,
    item: () -> HomeHeaderItem?,
): Pair<Modifier, Boolean> {
    if (!LocalTvDevice.current) return Modifier to false
    val controller = LocalTvHeaderController.current
    val currentItem by rememberUpdatedState(item)
    var focused by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    // Derived, so only the card that gains or loses the tint recomposes when the header settles.
    val glow by remember(controller) { derivedStateOf { if (focused) controller?.tintFor(focusedId) else null } }
    val modifier =
        Modifier
            .tvFocus(TvFocusStyle.Card, shape, glow)
            .onFocusChanged { state ->
                val now = state.isFocused
                if (now == focused) return@onFocusChanged
                focused = now
                if (now) {
                    val headerItem = currentItem()
                    focusedId = headerItem?.id
                    if (headerItem != null) controller?.onFocus(headerItem)
                }
            }
    return modifier to focused
}

/**
 * Home's immersive header (spec, "P2-A: identity"): 220 dp tall, the item's artwork on the right
 * under a cinematic scrim, its title in displaySmall on one line and its subtitle in bodyLarge on
 * at most two. It cross-fades when [item] changes.
 *
 * It draws no background of its own: over the artwork-free part the page (the ambient background)
 * shows through. A missing artwork, a blank URL or a failed load leaves the text on the page with
 * no picture and no placeholder box.
 *
 * It is not focusable and adds no focus target.
 */
@Composable
fun TvImmersiveHeader(
    item: HomeHeaderItem?,
    modifier: Modifier = Modifier,
) {
    val motion = LocalTvMotion.current
    Box(
        modifier
            .fillMaxWidth()
            .height(TvDimens.ImmersiveHeaderHeight),
    ) {
        Crossfade(targetState = item, animationSpec = motion.fadeInSpec(), label = "tvImmersiveHeader") { current ->
            if (current != null) HeaderContent(current, motion.fadeInMs)
        }
    }
}

/**
 * Keeps [controller]'s tint in step with the artwork of the item it settled on. Composed once, by
 * the screen that owns the header.
 */
@Composable
fun TvHeaderTintEffect(controller: TvHeaderController) {
    val artworkUrl = ambientImageModel(controller.settled?.artworkUrl)
    val resolved = rememberNowPlayingGlowTint(artworkUrl)
    // The dominant-colour state still holds the previous artwork's colour until the new one
    // resolves, so the tint is taken only when the colour changes after the item did.
    LaunchedEffect(resolved) {
        if (artworkUrl != null) controller.tint = resolved
    }
}

@Composable
private fun HeaderContent(
    item: HomeHeaderItem,
    fadeMs: Int,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val model = ambientImageModel(item.artworkUrl)
    var failed by remember(item.id) { mutableStateOf(false) }
    val type = typo()
    Box(Modifier.fillMaxSize()) {
        if (model != null && !failed) {
            val context = LocalPlatformContext.current
            val request =
                remember(context, model, fadeMs) {
                    ImageRequest
                        .Builder(context)
                        .data(model)
                        .crossfade(fadeMs)
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .width(HEADER_ARTWORK_WIDTH)
                        .fillMaxHeight()
                        .drawWithCache {
                            // Cinematic scrim: the picture dissolves into the page toward the text
                            // and toward the rows below, so it has no visible edge on either side.
                            val page = TvPalette.Background
                            val side =
                                Brush.horizontalGradient(
                                    0f to page,
                                    HEADER_SIDE_FADE to page.copy(alpha = 0.55f),
                                    1f to Color.Transparent,
                                    startX = if (rtl) size.width else 0f,
                                    endX = if (rtl) 0f else size.width,
                                )
                            val bottom =
                                Brush.verticalGradient(
                                    HEADER_BOTTOM_FADE to Color.Transparent,
                                    1f to page,
                                )
                            onDrawWithContent {
                                drawContent()
                                drawRect(side)
                                drawRect(bottom)
                            }
                        },
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = TvDimens.SafeHorizontal, bottom = HEADER_TEXT_BOTTOM)
                    .widthIn(max = HEADER_TEXT_WIDTH),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // One line, ellipsis: a long, CJK or right-to-left title is cut by the text layout
            // inside its own box and never by a parent clip. typo() already resolves direction by
            // content in right-to-left layouts.
            Text(
                text = item.title,
                // The app's typography defines no display roles, so displaySmall arrives with
                // Material's default face. The size comes from the TV scale; the face and weight
                // are the app's title ones.
                style = type.displaySmall.copy(fontFamily = type.titleLarge.fontFamily, fontWeight = FontWeight.Bold),
                color = TvPalette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = type.bodyLarge,
                    color = TvPalette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal const val HEADER_DEBOUNCE_MS = 300L
private const val DEBOUNCE_RECHECK_MS = 8L
private const val HEADER_SIDE_FADE = 0.35f
private const val HEADER_BOTTOM_FADE = 0.45f
private val HEADER_ARTWORK_WIDTH = 440.dp
private val HEADER_TEXT_WIDTH = 520.dp
private val HEADER_TEXT_BOTTOM = 16.dp

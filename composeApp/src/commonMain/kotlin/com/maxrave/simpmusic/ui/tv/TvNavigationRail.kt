package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maxrave.simpmusic.expect.ui.PlatformBackHandler
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.ui.component.BottomNavScreen
import com.maxrave.simpmusic.ui.icon.Settings
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.home.SettingsDestination
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.app_name
import simpmusic.composeapp.generated.resources.circle_app_icon
import simpmusic.composeapp.generated.resources.settings
import kotlin.reflect.KClass

/**
 * The TV's navigation rail (spec, "P2-A: identity", Rail).
 *
 * At rest it is a 72 dp column of icons, and 72 dp is all it ever takes from the layout. When focus
 * enters it, it draws itself 248 dp wide OVER the content, with labels and a scrim that fades out
 * to the right; the content beside it is neither moved nor resized.
 *
 * How it stays stable under a remote:
 * - **Nothing is added or removed when it opens.** The panel is always measured 248 dp wide and its
 *   labels are always composed; opening only moves a clip edge and an alpha, both read in the draw
 *   phase. No focus target is recreated, so focus cannot drop when the spring settles, and a held
 *   key costs no layout pass.
 * - **The spring is interruptible** ([TvMotion.panelSpring]): leaving and re-entering mid-animation
 *   retargets it from its current value and velocity. With the animator scale at 0 it snaps.
 * - **Entering lands on the selected destination**, whichever row focus came from; leaving toward
 *   the content goes to [contentFocus], which the app wires to restore the content's last focused
 *   element. Back while the rail has focus does the same instead of leaving the screen.
 * - **Selection and focus are different marks.** The selected destination has the accent dot and
 *   primary-coloured icon; the focused one is the `Control` pill (light fill, dark content, 1.05).
 *
 * Destinations are the app's own [BottomNavScreen] entries, matched by route — `ordinal` is an
 * identity there, not a position — and navigate exactly as `AppNavigationRail` does, so each tab's
 * back stack and saved state are kept. Settings is pinned to the bottom, for six entries at most.
 *
 * @param backdrop the recorded content, when the app records one and the device can blur it; the
 * open panel then uses [tvGlass] over it. Null draws the panel flat, which is the API 30 look.
 * @param contentFocus the content area's requester; see above.
 */
@Composable
fun TvNavigationRail(
    navController: NavController,
    showAnalyticsTab: Boolean,
    showMixForYouTab: Boolean,
    modifier: Modifier = Modifier,
    backdrop: PlatformBackdrop? = null,
    contentFocus: FocusRequester? = null,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit,
) {
    val motion = LocalTvMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val screens =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
            BottomNavScreen.Library,
            BottomNavScreen.Search,
        )

    // The tab the user is in. It survives pages opened inside the tab (an album opened from Home is
    // still "Home"), so it is remembered rather than derived, and re-synced whenever the current
    // destination is a tab's own root — which is how Back across tabs keeps the dot right.
    var selectedOrdinal by rememberSaveable { mutableIntStateOf(BottomNavScreen.Home.ordinal) }
    val currentRoot =
        screens.firstOrNull { screen ->
            currentBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(screen.destination::class) } == true
        }
    val onSettings = currentBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(SettingsDestination::class) } == true
    LaunchedEffect(currentRoot) {
        currentRoot?.let { selectedOrdinal = it.ordinal }
    }
    // A tab can leave the list under the user (tracking switched off, the YouTube session ended).
    LaunchedEffect(showAnalyticsTab, showMixForYouTab) {
        if (screens.none { it.ordinal == selectedOrdinal }) selectedOrdinal = BottomNavScreen.Home.ordinal
    }

    var hasFocus by remember { mutableStateOf(false) }
    val expansion = animateFloatAsState(if (hasFocus) 1f else 0f, motion.panelSpring, label = "tvRailExpansion")

    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    val selectedKey by rememberUpdatedState(if (onSettings) SETTINGS_KEY else selectedOrdinal)
    val contentRequester by rememberUpdatedState(contentFocus)
    // Entering the rail redirects to the selected item, and that request enters the rail again.
    val redirecting = remember { booleanArrayOf(false) }

    // P1 behaviour, kept: a TV has no touch to place focus, so the app starts with it on Home.
    LaunchedEffect(Unit) {
        runCatching { requesters[BottomNavScreen.Home.ordinal]?.requestFocus() }
    }

    if (hasFocus) {
        // Composed only while the rail has focus, so it registers after — and therefore ahead of —
        // the navigation graph's own back handling.
        PlatformBackHandler(enabled = true) {
            runCatching { contentRequester?.requestFocus() }
        }
    }

    Box(
        modifier
            // Above the content, which comes later in the row and would otherwise draw over the panel.
            .zIndex(1f)
            .width(TvDimens.RailCollapsed)
            .fillMaxHeight()
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusProperties {
                onEnter = {
                    if (!redirecting[0]) {
                        redirecting[0] = true
                        try {
                            val moved = runCatching { requesters[selectedKey]?.requestFocus() == true }.getOrDefault(false)
                            if (moved) cancelFocusChange()
                        } finally {
                            redirecting[0] = false
                        }
                    }
                }
                onExit = {
                    val towardContent = if (rtl) FocusDirection.Left else FocusDirection.Right
                    if (requestedFocusDirection == towardContent) {
                        val moved =
                            runCatching { contentRequester?.requestFocus(requestedFocusDirection) == true }.getOrDefault(false)
                        if (moved) cancelFocusChange()
                    }
                }
            }.focusGroup(),
    ) {
        Box(
            Modifier
                // Always the open width: the reserved 72 dp is reported to the parent, the rest
                // overflows onto the content. Nothing here is re-measured while it opens.
                .layout { measurable, constraints ->
                    val collapsed = TvDimens.RailCollapsed.roundToPx()
                    val expanded = TvDimens.RailExpanded.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(expanded, constraints.maxHeight))
                    layout(collapsed, placeable.height) {
                        placeable.place(if (rtl) collapsed - expanded else 0, 0)
                    }
                },
        ) {
            RailMaterial(expansion = expansion, rtl = rtl, backdrop = backdrop, open = hasFocus)
            Column(
                Modifier
                    .fillMaxSize()
                    .revealClip(expansion, rtl)
                    .padding(vertical = TvDimens.SafeVertical),
            ) {
                RailBrand(expansion)
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP)) {
                    screens.forEach { screen ->
                        RailItem(
                            label = stringResource(screen.title),
                            icon = screen.icon,
                            selected = !onSettings && selectedOrdinal == screen.ordinal,
                            expansion = expansion,
                            requester = requesters.getOrPut(screen.ordinal) { FocusRequester() },
                            onClick = {
                                val atRoot =
                                    currentBackStackEntry?.destination?.hierarchy?.any {
                                        it.hasRoute(screen.destination::class)
                                    } == true
                                if (selectedOrdinal == screen.ordinal && !onSettings) {
                                    if (atRoot) {
                                        reloadDestinationIfNeeded(screen.destination::class)
                                    } else {
                                        navController.navigate(screen.destination)
                                    }
                                } else {
                                    selectedOrdinal = screen.ordinal
                                    navController.navigate(screen.destination) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                RailItem(
                    label = stringResource(Res.string.settings),
                    icon = { Icon(SimpIcons.Settings, contentDescription = null) },
                    selected = onSettings,
                    expansion = expansion,
                    requester = requesters.getOrPut(SETTINGS_KEY) { FocusRequester() },
                    onClick = {
                        if (!onSettings) {
                            navController.navigate(SettingsDestination) { launchSingleTop = true }
                        }
                    },
                )
            }
        }
    }
}

/**
 * What the rail is made of. At rest: `Surface1` on the reserved strip, as the spec gives the rail.
 * Open: a scrim that is dense under the panel and fades out to its right, then the panel itself —
 * drawn flat where the backdrop cannot be blurred, [tvGlass] over [backdrop] where it can.
 */
@Composable
private fun RailMaterial(
    expansion: State<Float>,
    rtl: Boolean,
    backdrop: PlatformBackdrop?,
    open: Boolean,
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val progress = expansion.value.coerceIn(0f, 1f)
                val reveal = lerp(TvDimens.RailCollapsed.toPx(), size.width, progress)
                if (progress > 0f) {
                    val reach = size.width + RAIL_SCRIM_FADE.toPx()
                    val panelStop = size.width / reach
                    val dense = TvPalette.Background.copy(alpha = RAIL_SCRIM_DENSITY)
                    val stops = arrayOf(0f to dense, panelStop to dense, 1f to Color.Transparent)
                    if (rtl) {
                        drawRect(
                            Brush.horizontalGradient(*stops, startX = size.width, endX = size.width - reach),
                            topLeft = Offset(size.width - reach, 0f),
                            size = Size(reach, size.height),
                            alpha = progress,
                        )
                    } else {
                        drawRect(
                            Brush.horizontalGradient(*stops, startX = 0f, endX = reach),
                            size = Size(reach, size.height),
                            alpha = progress,
                        )
                    }
                }
                drawRect(
                    TvPalette.Surface1,
                    topLeft = Offset(if (rtl) size.width - reveal else 0f, 0f),
                    size = Size(reveal, size.height),
                )
            },
    ) {
        // Glass only while open, and only where the device can blur: it is the one part that
        // samples the content, and nothing reads it at rest. It is a sibling of the focusable
        // column, so composing it in and out cannot disturb focus.
        if (open && backdrop != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .revealClip(expansion, rtl)
                    .graphicsLayer { alpha = expansion.value.coerceIn(0f, 1f) }
                    .tvGlass(backdrop = backdrop, shape = RectangleShape, elevated = false),
            )
        }
    }
}

@Composable
private fun RailBrand(expansion: State<Float>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RAIL_BRAND_SIZE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width((TvDimens.RailCollapsed - RAIL_BRAND_SIZE) / 2))
        Image(
            painter = painterResource(Res.drawable.circle_app_icon),
            contentDescription = null,
            modifier = Modifier.size(RAIL_BRAND_SIZE),
        )
        Text(
            text = stringResource(Res.string.app_name),
            style = typo().titleLarge,
            color = TvPalette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(start = RAIL_LABEL_GAP)
                    .graphicsLayer { alpha = expansion.value.coerceIn(0f, 1f) },
        )
    }
}

@Composable
private fun RailItem(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    expansion: State<Float>,
    requester: FocusRequester,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val contentColor =
        when {
            focused -> TvPalette.OnFocus
            selected -> TvPalette.TextPrimary
            else -> TvPalette.TextSecondary
        }
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .padding(horizontal = RAIL_ITEM_INSET)
            .fillMaxWidth()
            .height(TvDimens.RailItemHeight)
            .focusRequester(requester)
            .tvFocus(TvFocusStyle.Control)
            .controlFill(focused = focused, restFill = Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The dot's slot. With the 12 dp inset it puts the 24 dp icon on the centre of the 72 dp strip.
        Box(
            Modifier
                .width(RAIL_DOT_SLOT)
                .fillMaxHeight()
                .drawBehind {
                    if (selected) {
                        drawCircle(
                            color = if (focused) TvPalette.OnFocus else accent,
                            radius = TvDimens.RailIndicator.toPx() / 2f,
                        )
                    }
                },
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(Modifier.size(RAIL_ICON_SIZE), contentAlignment = Alignment.Center) { icon() }
        }
        Text(
            text = label,
            style = typo().titleMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(start = RAIL_LABEL_GAP, end = 16.dp)
                    .graphicsLayer { alpha = expansion.value.coerceIn(0f, 1f) },
        )
    }
}

/** Shows the panel from its start edge up to the width the expansion has reached. Draw phase only. */
private fun Modifier.revealClip(
    expansion: State<Float>,
    rtl: Boolean,
): Modifier =
    drawWithContent {
        val reveal = lerp(TvDimens.RailCollapsed.toPx(), size.width, expansion.value.coerceIn(0f, 1f))
        if (rtl) {
            clipRect(left = size.width - reveal) { this@drawWithContent.drawContent() }
        } else {
            clipRect(right = reveal) { this@drawWithContent.drawContent() }
        }
    }

private const val SETTINGS_KEY = -1
private const val RAIL_SCRIM_DENSITY = 0.94f
private val RAIL_SCRIM_FADE = 160.dp
private val RAIL_ITEM_INSET = 12.dp
private val RAIL_ITEM_GAP = 8.dp
private val RAIL_DOT_SLOT = 12.dp
private val RAIL_ICON_SIZE = 24.dp
private val RAIL_LABEL_GAP = 16.dp
private val RAIL_BRAND_SIZE = 40.dp

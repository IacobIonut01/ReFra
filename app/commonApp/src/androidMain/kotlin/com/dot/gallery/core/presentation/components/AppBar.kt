/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.DeferredAnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.ExperimentalDeferredTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clipToBounds
import com.dot.gallery.app.presentation.settings.pane.SettingsHubPane
import com.dot.gallery.app.presentation.settings.pane.SettingsPanePlaceholder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dot.gallery.core.LocalScrollToTop
import com.dot.gallery.core.ScrollToTopController
import com.dot.gallery.core.LocalViewerDismissBridge
import com.dot.gallery.core.ViewerDismissBridge
import com.dot.gallery.feature.viewer.components.media.ViewerDismissExitVisual
import com.dot.gallery.feature.viewer.components.media.ViewerDismissFlightLayer
import com.dot.gallery.core.presentation.util.NavigationItem
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.presentation.util.rememberBottomBarInset
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Library
import com.dot.gallery.ui.theme.ComponentSize
import com.dot.gallery.ui.theme.Spacing
import com.dot.gallery.core.LocalMediaSelector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.ui.theme.GalleryChrome
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.core.util.or
import com.dot.gallery.feature.editor.adjustments.varfilter.Pop
import com.dot.gallery.feature.viewer.ViewerDismissState
import com.dot.gallery.app.presentation.albums.AlbumsScreen
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.feature.editor.adjustments.filters.None
import com.dot.gallery.app.presentation.library.LibraryScreen
import com.dot.gallery.app.presentation.timeline.TimelineScreen
import com.dot.gallery.core.Settings.Misc.rememberAutoHideNavBar
import com.dot.gallery.core.Settings.Misc.rememberOldNavbar
import com.dot.gallery.core.Settings.Misc.rememberSharedElements
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.library
import com.dot.gallery.generated.resources.nav_albums
import com.dot.gallery.generated.resources.nav_timeline


internal fun navigationRailSlideOffset(
    fullWidth: Int,
    layoutDirection: LayoutDirection,
): Int = if (layoutDirection == LayoutDirection.Ltr) -2 * fullWidth else 2 * fullWidth

private val SettingsHubMinWidth = 280.dp
private val SettingsPageMinWidth = 360.dp
private val SettingsSplitMinContentWidth = 720.dp

/** Immersive routes take the whole window: no rail, and no gallery pane beside them. */
internal fun isImmersiveRoute(route: String): Boolean {
    val path = route.substringBefore('?')
    return path.startsWith(Screen.MediaViewScreen.route) ||
        path.startsWith(Screen.StoryViewerScreen.route) ||
        path == Screen.SetupScreen.route
}

/** The floating rail leaves only for immersive routes; search morphs out of its own circle. */
internal fun isRailHiddenRoute(route: String): Boolean = isImmersiveRoute(route)

/**
 * Where the floating rail starts: past a side navigation bar, and past a display cutout on the
 * start edge when the cutout overlaps the rail's band (a hole hidden inside the status bar, as on
 * portrait-landscape top corners, needs no detour). Uses the cutout's real bounding rectangles so
 * the rail gives up only the width of the notch, not the whole inset band.
 */
@Composable
private fun rememberRailStartPadding(): Dp {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val view = LocalView.current
    val window = LocalWindowInfo.current.containerSize
    // Reading the insets subscribes to rotation and cutout changes.
    val navStart = if (layoutDirection == LayoutDirection.Ltr) {
        WindowInsets.navigationBars.getLeft(density, layoutDirection)
    } else WindowInsets.navigationBars.getRight(density, layoutDirection)
    val cutoutStart = if (layoutDirection == LayoutDirection.Ltr) {
        WindowInsets.displayCutout.getLeft(density, layoutDirection)
    } else WindowInsets.displayCutout.getRight(density, layoutDirection)
    val bandTop = WindowInsets.statusBars.getTop(density)
    val bandBottom = window.height - WindowInsets.navigationBars.getBottom(density)
    val holeEnd = if (cutoutStart <= 0) 0 else {
        ViewCompat.getRootWindowInsets(view)?.displayCutout?.boundingRects.orEmpty()
            .filter { it.bottom > bandTop && it.top < bandBottom }
            .maxOfOrNull { rect ->
                if (layoutDirection == LayoutDirection.Ltr) {
                    if (rect.left <= 0) rect.right else 0
                } else {
                    if (rect.right >= window.width) window.width - rect.left else 0
                }
            } ?: 0
    }
    return with(density) {
        when {
            holeEnd > navStart -> holeEnd.toDp() + RailCutoutGap
            navStart > 0 -> navStart.toDp() + RailCutoutGap
            else -> GalleryChrome.RailStartPadding
        }
    }
}

private val RailCutoutGap = 6.dp

@Composable
fun rememberNavigationItems(): List<NavigationItem> {
    val timelineTitle = stringResource(Res.string.nav_timeline)
    val libraryTitle = stringResource(Res.string.library)
    return remember(timelineTitle, libraryTitle) {
        listOf(
            NavigationItem(
                name = timelineTitle,
                route = Screen.TimelineScreen.route,
                icon = Icons.Outlined.Photo,
            ),
            NavigationItem(
                name = libraryTitle,
                route = Screen.LibraryScreen(),
                icon = GalleryIcons.Library
            )
        )
    }
}

const val AppBarOverlayContentTag = "AppBarContainer.Overlay"

@OptIn(
    ExperimentalMaterial3WindowSizeClassApi::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalDeferredTransitionApi::class,
)
@Stable
@Composable
fun AppBarContainer(
    navController: NavController,
    bottomBarState: Boolean,
    paddingValues: PaddingValues,
    isScrolling: Boolean,
    overlayVisible: Boolean = false,
    mediaOverlayPresent: Boolean = false,
    overlayContent: @Composable (SharedTransitionScope, AnimatedVisibilityScope, ViewerDismissBridge) -> Unit = { _, _, _ -> },
    content: @Composable (SharedTransitionScope) -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = LocalScrollToTop.current
    val bottomNavItems = rememberNavigationItems()
    val overlayDismissBridge = remember { ViewerDismissBridge() }
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    var dualPaneEnabled by rememberPreference(SettingsKeys.galleryDualPane)
    var paneFraction by rememberPreference(SettingsKeys.galleryPaneFraction)
    val currentRoute = backStackEntry?.destination?.route
    val windowWidth = with(density) { window.width.toDp() }
    // Any screen that opens the overlay viewer (timeline, albums, favorites, trash, search,
    // locations, people...) keeps its content beside it; the standalone viewer route cannot.
    val galleryDetailEligible = dualPaneEnabled &&
        currentRoute != null && !isImmersiveRoute(currentRoute) &&
        with(density) { galleryDetailPaneFits(windowWidth, window.height.toDp()) }
    // Sticky while the viewer's exit fade runs, so the pane never collapses under a visible viewer.
    val useGalleryDetail = galleryDetailPaneActive(
        mediaOverlayPresent, overlayVisible, overlayDismissBridge.transitionState.currentState,
    ) && galleryDetailEligible
    // Follows the request instead, so the gallery widens and navigation returns as dismissal starts.
    val galleryDetailRequested = mediaOverlayPresent && overlayVisible && galleryDetailEligible
    val wideWindow = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact
    val useNavRail = wideWindow && !galleryDetailRequested
    val useOldNavbar by rememberOldNavbar()
    val hideNavBarSetting by rememberAutoHideNavBar()
    val anySelectedRoute = remember(backStackEntry, bottomNavItems) {
        bottomNavItems.any { it.route == backStackEntry?.destination?.route }
    }

    // Wide windows use a rail for both navigation styles. Keeping a compact floating bar on a
    // tablet left content underneath the bar and made the primary destinations hard to reach.
    // The floating rail stays on every non-immersive screen (holding their back button and an
    // active selection); the classic rail keeps its primary-destination-only behaviour.
    val railHiddenRoute = currentRoute == null || isRailHiddenRoute(currentRoute)
    val showNavRail by remember(useNavRail, useOldNavbar, bottomBarState, anySelectedRoute, railHiddenRoute) {
        derivedStateOf {
            if (useOldNavbar) useNavRail && bottomBarState && anySelectedRoute
            else useNavRail && !railHiddenRoute
        }
    }
    // A split gallery pane is phone-sized, so it keeps the bottom navigation like a phone does.
    val showClassicNavbar by remember(useOldNavbar, useNavRail, bottomBarState, isScrolling, hideNavBarSetting, anySelectedRoute) {
        derivedStateOf {
            useOldNavbar && !useNavRail &&
                bottomBarState && (!isScrolling || !hideNavBarSetting) && anySelectedRoute
        }
    }
    val showMaterialNavbar by remember(useOldNavbar, useNavRail, bottomBarState, isScrolling, hideNavBarSetting, anySelectedRoute) {
        derivedStateOf {
            !useOldNavbar && !useNavRail && bottomBarState &&
                (!isScrolling || !hideNavBarSetting) && anySelectedRoute
        }
    }
    // The floating rail frames primary content in an inset, rounded pane like the split layout,
    // but only when there is height to spare: on short landscape windows the frame would cost a
    // permanent status-bar band, so content stays edge to edge and scrolls under the bar instead.
    val floatingRail = showNavRail && !useOldNavbar
    val compactHeight = with(density) { window.height.toDp() } < GalleryChrome.DetailMinWindowHeight
    val railPane = floatingRail && !compactHeight
    val railStart = rememberRailStartPadding()
    val animatedPadding by animateDpAsState(
        targetValue = when {
            !showNavRail -> 0.dp
            useOldNavbar -> ComponentSize.NavigationRailWidth
            else -> railStart + GalleryChrome.RailItemWidth + GalleryChrome.RailContentGap
        },
        label = "animatedPadding"
    )
    val railBackHost = remember { GalleryRailBackHost() }
    val railSelectionHost = remember { GalleryRailSelectionHost() }
    // Settings pages sit beside the settings hub when the content area is wide enough.
    val backStack by navController.currentBackStack.collectAsState()
    val inSettings = backStack.any { it.destination.route == Screen.SettingsScreen() }
    val settingsSplit = floatingRail && inSettings &&
        windowWidth - GalleryChrome.RailWidth >= SettingsSplitMinContentWidth
    // The list can be resized like the gallery pane; the page always keeps a usable width.
    var savedHubWidth by rememberPreference(SettingsKeys.settingsPaneWidth)
    var draggedHubWidth by remember { mutableStateOf<Dp?>(null) }
    val settingsContentWidth = windowWidth - GalleryChrome.RailWidth
    val hubMaxWidth = (settingsContentWidth - SettingsPageMinWidth).coerceAtLeast(SettingsHubMinWidth)
    val hubTargetWidth = (draggedHubWidth ?: savedHubWidth.dp).coerceIn(SettingsHubMinWidth, hubMaxWidth)
    LaunchedEffect(savedHubWidth) { draggedHubWidth = null }
    val settingsHubWidth by animateDpAsState(
        targetValue = if (settingsSplit) hubTargetWidth else 0.dp,
        // Follow the finger exactly while dragging; spring only for opening and closing.
        animationSpec = if (draggedHubWidth != null) snap() else spring(),
        label = "settingsHubWidth",
    )
    val railPaneCorner by animateDpAsState(
        targetValue = if (railPane) GalleryChrome.PaneCornerRadius else 0.dp,
        label = "railPaneCorner",
    )
    // Deferred transition for the media overlay. Swipe-dismiss does not use the transition's
    // MutableTransform — the viewer applies its drag offset to an inner card so the gesture
    // handler's coordinate space never moves; committing runs a root-level return flight
    // while this container's plain fade-out runs underneath.
    val overlayVisibilityState = overlayDismissBridge.transitionState
    // animateTo (not a bare targetState write — its setter is internal) resolves any still-armed
    // deferred gesture into the matching transition. Guarded so a recomposition during an armed
    // drag (targetState still true behind the pending false) doesn't cancel the defer.
    if (overlayVisibilityState.targetState != overlayVisible) {
        overlayVisibilityState.animateTo(overlayVisible)
    }
    val overlayTransition = rememberTransition(overlayVisibilityState)
    val viewerAnimationsEnabled by rememberSharedElements()
    val overlayActive =
        overlayVisibilityState.currentState || overlayVisibilityState.targetState
    val overlayIsolationModifier = if (overlayActive && !useGalleryDetail) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier
    }

    // Safety net: if the overlay disappears while a drag still holds the defer armed (e.g. a
    // back press mid-gesture), release the source cell's shared-element suppression.
    if (!overlayActive) {
        overlayDismissBridge.suppressedElementKey = null
    }
    // An enter flight only makes sense while the overlay is still opening — a mid-flight
    // dismiss drops the morph rather than letting it finish over the exiting container.
    if (!overlayVisible) {
        overlayDismissBridge.flight?.takeIf { it.isEnter }?.let {
            overlayDismissBridge.flight = null
        }
    }
    // Render the content exactly once. Toggling "use material navigation" must only swap the
    // navigation bars below, not the whole app content. Previously content() was nested inside
    // both the useOldNavbar and !useOldNavbar AnimatedVisibility blocks, so flipping the setting
    // cross-faded (and recomposed) the entire screen, making it blink.
    SharedTransitionLayout {
        CompositionLocalProvider(LocalViewerDismissBridge provides overlayDismissBridge) {
            Box(modifier = Modifier.fillMaxSize()) {
                GalleryPaneLayout(
                    split = useGalleryDetail,
                    splitRequested = galleryDetailRequested,
                    masterFraction = paneFraction,
                    onMasterFractionChange = { paneFraction = it },
                    masterContent = {
                        val railPaneModifier = if (railPane) {
                            Modifier
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .clip(RoundedCornerShape(topStart = railPaneCorner, topEnd = railPaneCorner))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        } else Modifier
                        Box(
                            modifier = Modifier
                                .padding(start = animatedPadding)
                                .then(railPaneModifier)
                                .then(overlayIsolationModifier),
                        ) {
                            CompositionLocalProvider(
                                LocalGalleryPaneChrome provides (railPane || LocalGalleryPaneChrome.current),
                                LocalGalleryRailEdge provides floatingRail,
                                LocalGalleryRailBackHost provides railBackHost.takeIf { floatingRail },
                                LocalGalleryRailSelectionHost provides railSelectionHost.takeIf { floatingRail },
                            ) {
                                // Content stays the first child so entering settings never
                                // recreates the navigation host; the hub only pads it aside.
                                Box(Modifier.padding(start = settingsHubWidth)) {
                                    content(this@SharedTransitionLayout)
                                    if (settingsSplit && currentRoute == Screen.SettingsScreen()) {
                                        SettingsPanePlaceholder()
                                    }
                                }
                                if (settingsHubWidth > 0.dp) {
                                    Box(
                                        Modifier
                                            .width(settingsHubWidth)
                                            .fillMaxHeight()
                                            .clipToBounds(),
                                    ) {
                                        SettingsHubPane(
                                            navController = navController,
                                            modifier = Modifier
                                                .requiredWidth(hubTargetWidth)
                                                .fillMaxHeight()
                                                .align(Alignment.CenterEnd),
                                        )
                                    }
                                    if (settingsSplit) {
                                        GalleryPaneHandle(
                                            modifier = Modifier
                                                .offset(x = settingsHubWidth - GalleryChrome.PaneHandleTouchWidth / 2)
                                                .width(GalleryChrome.PaneHandleTouchWidth)
                                                .fillMaxHeight(),
                                            fractionPercent = {
                                                (hubTargetWidth / settingsContentWidth * 100).roundToInt()
                                            },
                                            onDrag = { delta ->
                                                val signed = if (layoutDirection == LayoutDirection.Rtl) -delta else delta
                                                draggedHubWidth = ((draggedHubWidth ?: hubTargetWidth) + signed.toDp())
                                                    .coerceIn(SettingsHubMinWidth, hubMaxWidth)
                                            },
                                            onDragStopped = {
                                                // Hold the dragged width until the saved one arrives,
                                                // so the list never snaps back for a frame.
                                                draggedHubWidth?.let {
                                                    if (it.value == savedHubWidth) draggedHubWidth = null
                                                    else savedHubWidth = it.value
                                                }
                                            },
                                            onSetFraction = { fraction ->
                                                savedHubWidth = (settingsContentWidth * fraction)
                                                    .coerceIn(SettingsHubMinWidth, hubMaxWidth).value
                                            },
                                            onReset = {
                                                draggedHubWidth = null
                                                savedHubWidth = SettingsKeys.settingsPaneWidth.default
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(
                            modifier = overlayIsolationModifier,
                            visible = showNavRail,
                            enter = slideInHorizontally {
                                navigationRailSlideOffset(it, layoutDirection)
                            } + fadeIn(),
                            exit = slideOutHorizontally {
                                navigationRailSlideOffset(it, layoutDirection)
                            } + fadeOut(),
                        ) {
                            if (useOldNavbar) {
                                ClassicNavigationRail(
                                    backStackEntry = backStackEntry,
                                    navigationItems = bottomNavItems,
                                    onClick = { onNavItemClick(navController, it, scrollToTop) },
                                )
                            } else {
                                GalleryNavigationRailContent(
                                    selectedRoute = backStackEntry?.destination?.route,
                                    navigationItems = bottomNavItems,
                                    onClick = { onNavItemClick(navController, it, scrollToTop) },
                                    // On search itself the circle leaves, growing into the bar.
                                    onSearchClick = if (currentRoute == Screen.SearchScreen()) null
                                        else ({ navController.navigate(Screen.SearchScreen()) }),
                                    sharedTransitionScope = this@SharedTransitionLayout
                                        .takeIf { showNavRail },
                                    onBackClick = railBackHost.action.takeIf { !anySelectedRoute },
                                    selectionContent = railSelectionHost.content,
                                    startPadding = railStart,
                                )
                            }
                        }
                        AnimatedVisibility(
                            modifier = Modifier.align(Alignment.BottomCenter).then(overlayIsolationModifier),
                            visible = showClassicNavbar,
                            enter = slideInVertically { it * 2 },
                            exit = slideOutVertically { it * 2 },
                        ) {
                            ClassicNavBar(
                                backStackEntry = backStackEntry,
                                navigationItems = bottomNavItems,
                                onClick = { onNavItemClick(navController, it, scrollToTop) },
                            )
                        }
                        AnimatedVisibility(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = rememberBottomBarInset(paddingValues))
                                .then(overlayIsolationModifier),
                            visible = showMaterialNavbar,
                            enter = slideInVertically { it * 2 },
                            exit = slideOutVertically { it * 2 },
                        ) {
                            GalleryNavBar(
                                modifier = Modifier.fillMaxWidth(),
                                backStackEntry = backStackEntry,
                                navigationItems = bottomNavItems,
                                onClick = { onNavItemClick(navController, it, scrollToTop) },
                                onSearchClick = { navController.navigate(Screen.SearchScreen()) },
                                // Only the visible navigation owns the search bounds key.
                                sharedTransitionScope = this@SharedTransitionLayout
                                    .takeIf { showMaterialNavbar },
                                animatedVisibilityScope = this,
                            )
                        }
                        // Sits above whichever bottom chrome is showing: the floating dock, or the
                        // selection bar while a selection is active.
                        val selectionActive by LocalMediaSelector.current.isSelectionActive
                            .collectAsStateWithLifecycle()
                        val progressBottom by animateDpAsState(
                            targetValue = rememberBottomBarInset(paddingValues) + when {
                                selectionActive -> FloatingProgressSelectionClearance
                                showMaterialNavbar || showClassicNavbar ->
                                    ComponentSize.NavigationBarHeight + Spacing.Medium * 2
                                else -> Spacing.Medium
                            },
                            label = "floatingProgressBottom",
                        )
                        FloatingProgressHost(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = Spacing.ScreenHorizontal)
                                .padding(bottom = progressBottom)
                                .then(overlayIsolationModifier),
                        )
                    },
                    detailContent = {
                        overlayTransition.DeferredAnimatedVisibility(
                            visible = { it },
                            modifier = Modifier.fillMaxSize().testTag(AppBarOverlayContentTag),
                            // Keep the layer alive until the shared-bounds return flight finishes.
                            enter = if (viewerAnimationsEnabled) fadeIn(tween(320)) else EnterTransition.None,
                            exit = if (viewerAnimationsEnabled) fadeOut(tween(320)) else ExitTransition.None,
                        ) {
                            if (overlayVisible) {
                                overlayContent(this@SharedTransitionLayout, this, overlayDismissBridge)
                            } else {
                                // The exiting stand-in is not interactive, so fast re-taps reach the grid.
                                ViewerDismissExitVisual(overlayDismissBridge)
                            }
                        }
                    },
                )
                // The flight uses window coordinates, outside both independently clipped panes.
                ViewerDismissFlightLayer(overlayDismissBridge)
            }
        }
    }
}

/** Lift for the floating progress pill while the selection bar owns the bottom edge. */
private val FloatingProgressSelectionClearance = 112.dp

private fun onNavItemClick(
    navController: NavController,
    route: String,
    scrollToTop: ScrollToTopController,
) {
    // Re-tapping the already-selected tab scrolls that screen back to the top
    // instead of doing nothing. Otherwise navigate to the tab.
    if (route == navController.currentDestination?.route) {
        scrollToTop.requestScrollToTop(route)
    } else {
        navigate(navController, route)
    }
}

private fun navigate(navController: NavController, route: String) {
    navController.navigate(route) {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
        // Restore state when reselecting a previously selected item
        restoreState = true
    }
}

// The bar rendering itself lives in core/ui commonMain (GalleryNavigationBar-
// Content etc.) so docs previews render the identical bars without a
// NavController; these wrappers keep the original Android signatures.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryNavBar(
    modifier: Modifier,
    backStackEntry: NavBackStackEntry?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit,
    onSearchClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) = GalleryNavigationBarContent(
    modifier = modifier,
    selectedRoute = backStackEntry?.destination?.route,
    navigationItems = navigationItems,
    onClick = onClick,
    onSearchClick = onSearchClick,
    sharedTransitionScope = sharedTransitionScope,
    animatedVisibilityScope = animatedVisibilityScope,
)

@Composable
fun ClassicNavBar(
    backStackEntry: NavBackStackEntry?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit
) = ClassicNavigationBarContent(
    selectedRoute = backStackEntry?.destination?.route,
    navigationItems = navigationItems,
    onClick = onClick,
)

@Composable
private fun ClassicNavigationRail(
    backStackEntry: NavBackStackEntry?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit
) = ClassicNavigationRailContent(
    selectedRoute = backStackEntry?.destination?.route,
    navigationItems = navigationItems,
    onClick = onClick,
)
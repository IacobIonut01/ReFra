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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dot.gallery.R
import com.dot.gallery.core.LocalScrollToTop
import com.dot.gallery.core.ScrollToTopController
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberAutoHideNavBar
import com.dot.gallery.core.Settings.Misc.rememberOldNavbar
import com.dot.gallery.core.Settings.Misc.rememberSharedElements
import com.dot.gallery.feature_node.presentation.mediaview.LocalViewerDismissBridge
import com.dot.gallery.feature_node.presentation.mediaview.ViewerDismissBridge
import com.dot.gallery.feature_node.presentation.mediaview.components.media.ViewerDismissExitVisual
import com.dot.gallery.feature_node.presentation.mediaview.components.media.ViewerDismissFlightLayer
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import com.dot.gallery.feature_node.presentation.util.NavigationItem
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.rememberBottomBarInset
import com.dot.gallery.ui.core.icons.Albums
import com.dot.gallery.ui.theme.ComponentSize
import com.dot.gallery.ui.theme.Spacing
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle

internal fun navigationRailSlideOffset(
    fullWidth: Int,
    layoutDirection: LayoutDirection,
): Int = if (layoutDirection == LayoutDirection.Ltr) -2 * fullWidth else 2 * fullWidth

@Composable
fun rememberNavigationItems(): List<NavigationItem> {
    val timelineTitle = stringResource(R.string.nav_timeline)
    val albumsTitle = stringResource(R.string.nav_albums)
    val libraryTitle = stringResource(R.string.library)
    return remember(timelineTitle, albumsTitle, libraryTitle) {
        listOf(
            NavigationItem(
                name = timelineTitle,
                route = Screen.TimelineScreen.route,
                icon = Icons.Outlined.Photo,
            ),
            NavigationItem(
                name = albumsTitle,
                route = Screen.AlbumsScreen.route,
                icon = com.dot.gallery.ui.core.Icons.Albums,
            ),
            NavigationItem(
                name = libraryTitle,
                route = Screen.LibraryScreen(),
                icon = Icons.Outlined.PhotoLibrary
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
    overlayContent: @Composable (SharedTransitionScope, AnimatedVisibilityScope, ViewerDismissBridge) -> Unit = { _, _, _ -> },
    content: @Composable (SharedTransitionScope) -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = LocalScrollToTop.current
    val bottomNavItems = rememberNavigationItems()
    val useNavRail = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact
    val useOldNavbar by rememberOldNavbar()
    val hideNavBarSetting by rememberAutoHideNavBar()
    val anySelectedRoute = remember(backStackEntry, bottomNavItems) {
        bottomNavItems.any { it.route == backStackEntry?.destination?.route }
    }

    // Wide windows use a rail for both navigation styles. Keeping a compact floating bar on a
    // tablet left content underneath the bar and made the primary destinations hard to reach.
    val showNavRail by remember(useNavRail, bottomBarState, anySelectedRoute) {
        derivedStateOf { useNavRail && bottomBarState && anySelectedRoute }
    }
    val showClassicNavbar by remember(useOldNavbar, useNavRail, bottomBarState, isScrolling, hideNavBarSetting, anySelectedRoute) {
        derivedStateOf {
            useOldNavbar && !useNavRail && bottomBarState && (!isScrolling || !hideNavBarSetting) && anySelectedRoute
        }
    }
    val showMaterialNavbar by remember(useOldNavbar, useNavRail, bottomBarState, isScrolling, hideNavBarSetting, anySelectedRoute) {
        derivedStateOf {
            !useOldNavbar && !useNavRail && bottomBarState &&
                (!isScrolling || !hideNavBarSetting) && anySelectedRoute
        }
    }
    val animatedPadding by animateDpAsState(
        targetValue = if (showNavRail) ComponentSize.NavigationRailWidth else 0.dp,
        label = "animatedPadding"
    )
    // Deferred transition for the media overlay. Swipe-dismiss does not use the transition's
    // MutableTransform — the viewer applies its drag offset to an inner card so the gesture
    // handler's coordinate space never moves; committing runs a root-level return flight
    // while this container's plain fade-out runs underneath.
    val overlayDismissBridge = remember { ViewerDismissBridge() }
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
    val overlayIsolationModifier = if (overlayActive) {
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
    // cross-faded (and recomposed) the entire screen, making it blink (#973).
    SharedTransitionLayout {
        CompositionLocalProvider(LocalViewerDismissBridge provides overlayDismissBridge) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
            modifier = Modifier
                .padding(start = animatedPadding)
                .then(overlayIsolationModifier)
        ) {
            content(this@SharedTransitionLayout)
        }
        // Adaptive rail (wide) or the selected compact bottom-bar style.
        AnimatedVisibility(
            modifier = overlayIsolationModifier,
            visible = showNavRail,
            enter = slideInHorizontally {
                navigationRailSlideOffset(it, layoutDirection)
            },
            exit = slideOutHorizontally {
                navigationRailSlideOffset(it, layoutDirection)
            }
        ) {
            ClassicNavigationRail(
                backStackEntry = backStackEntry,
                navigationItems = bottomNavItems,
                onClick = { onNavItemClick(navController, it, scrollToTop) }
            )
        }
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter).then(overlayIsolationModifier),
            visible = showClassicNavbar,
            enter = slideInVertically { it * 2 },
            exit = slideOutVertically { it * 2 },
            content = {
                ClassicNavBar(
                    backStackEntry = backStackEntry,
                    navigationItems = bottomNavItems,
                    onClick = { onNavItemClick(navController, it, scrollToTop) },
                )
            }
        )
        // Material (new) navbar: floating bar
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = rememberBottomBarInset(paddingValues))
                .then(overlayIsolationModifier),
            visible = showMaterialNavbar,
            enter = slideInVertically { it * 2 },
            exit = slideOutVertically { it * 2 },
            content = {
                GalleryNavBar(
                    modifier = Modifier.fillMaxWidth(),
                    backStackEntry = backStackEntry,
                    navigationItems = bottomNavItems,
                    onClick = { onNavItemClick(navController, it, scrollToTop) }
                )
            }
        )
        overlayTransition.DeferredAnimatedVisibility(
            visible = { it },
            modifier = Modifier.fillMaxSize().testTag(AppBarOverlayContentTag),
            // Long enough for the sharedBounds return flight to finish before the overlay layer
            // is torn down. With media animations disabled there is no flight to cover — the
            // viewer appears/disappears instantly.
            enter = if (viewerAnimationsEnabled) fadeIn(tween(320)) else EnterTransition.None,
            exit = if (viewerAnimationsEnabled) fadeOut(tween(320)) else ExitTransition.None,
        ) {
            if (overlayVisible) {
                overlayContent(this@SharedTransitionLayout, this, overlayDismissBridge)
            } else {
                // Exiting: swap the interactive viewer for its non-interactive stand-in so
                // a fast re-tap falls through to the grid — the still-fading viewer's
                // gesture surfaces would otherwise swallow touches for the whole fade.
                ViewerDismissExitVisual(overlayDismissBridge)
            }
        }
        // Committed swipe-dismiss flight: a root-level layer morphs the media thumbnail from the
        // gesture's release bounds to the live source-cell bounds while the overlay exits. It
        // lives outside the DeferredAnimatedVisibility so the container's own fade/slide can't
        // affect it — the dismiss bookkeeping is owned by ViewerDismissState, not the shared
        // element machinery (whose deferred handoff can't track a fullscreen element against a
        // translating mosaic cell).
        ViewerDismissFlightLayer(overlayDismissBridge)
        }
        }
    }
}

private fun onNavItemClick(
    navController: NavController,
    route: String,
    scrollToTop: ScrollToTopController,
) {
    // Re-tapping the already-selected tab scrolls that screen back to the top
    // instead of doing nothing (#1039). Otherwise navigate to the tab.
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

@Composable
fun GalleryNavBar(
    modifier: Modifier,
    backStackEntry: NavBackStackEntry?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit,
) {
    val allowBlur by rememberAllowBlur()
    val surfaceColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val navigationShape = MaterialTheme.shapes.extraLarge
    val backgroundModifier = remember(allowBlur, surfaceColor, navigationShape) {
        if (!allowBlur) {
            Modifier.background(
                color = surfaceColor,
                shape = navigationShape,
            )
        } else {
            Modifier
        }
    }
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.ExtraLarge)
            .then(modifier)
            .height(ComponentSize.NavigationBarHeight)
            .clip(navigationShape)
            .then(backgroundModifier)
            .hazeEffectScaled(
                state = LocalHazeState.current,
                style = LocalHazeBlurStyle.current
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        navigationItems.forEach { item ->
            val selected = remember(item, backStackEntry) {
                item.route == backStackEntry?.destination?.route
            }
            GalleryNavBarItem(
                navItem = item,
                isSelected = selected,
                onClick = onClick
            )
        }
    }
}

@Stable
@Composable
private fun Label(item: NavigationItem) = Text(
    text = item.name,
    fontWeight = FontWeight.Medium,
    style = MaterialTheme.typography.bodyMedium,
)

@Stable
@Composable
private fun Icon(item: NavigationItem) = Icon(
    imageVector = item.icon,
    contentDescription = item.name,
)

@Composable
fun ClassicNavBar(
    backStackEntry: NavBackStackEntry?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        navigationItems.forEach { item ->
            val selected = item.route == backStackEntry?.destination?.route
            NavigationBarItem(
                selected = selected,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                onClick = { onClick(item.route) },
                label = { Label(item) },
                icon = { Icon(item) }
            )
        }
    }
}

@Composable
private fun ClassicNavigationRail(
    backStackEntry: NavBackStackEntry?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Spacer(Modifier.weight(1f))
        navigationItems.forEach { item ->
            val selected = item.route == backStackEntry?.destination?.route
            NavigationRailItem(
                selected = selected,
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                onClick = { onClick(item.route) },
                label = { Label(item) },
                icon = { Icon(item) }
            )
        }
        Spacer(Modifier.weight(1f))
    }

}

@Composable
fun RowScope.GalleryNavBarItem(
    navItem: NavigationItem,
    isSelected: Boolean,
    onClick: (route: String) -> Unit,
) {
    val selectedColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "selectedColor"
    )
    val selectedIconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "selectedIconColor"
    )
    Box(
        modifier = Modifier
            .height(ComponentSize.NavigationBarHeight)
            .weight(1f)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = { onClick(navItem.route) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(ComponentSize.NavigationBarHeight)
                .background(
                    color = selectedColor,
                    shape = RoundedCornerShape(percent = 100),
                ),
        )
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = navItem.icon,
            contentDescription = navItem.name,
            tint = selectedIconColor,
        )
    }
}
/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.util.NavigationItem
import com.dot.gallery.core.presentation.util.sharedSearchBounds
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.back_cd
import com.dot.gallery.core.ui.generated.resources.search_media
import com.dot.gallery.ui.theme.GalleryChrome
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

const val GalleryNavigationRailTag = "Gallery.NavigationRail"
const val GalleryNavigationRailSearchTag = "Gallery.NavigationRailSearch"
const val GalleryNavigationRailBackTag = "Gallery.NavigationRailBack"
const val GalleryNavigationRailSelectionTag = "Gallery.NavigationRailSelection"

/**
 * Wide-window counterpart of the floating dock: a blurred pill of destinations with the search
 * action in its own circle below, both anchored to the top start under the status bar.
 *
 * Secondary screens get a back button instead of the destinations, which only make sense on
 * the primary screens, so the rail never has to leave. While [selectionContent] is set, the
 * destinations pill morphs into a scrollable action panel holding it, and search steps aside
 * until the selection ends. Search is a morph source of its own: passing a null [onSearchClick]
 * (on the search screen) lets the circle grow into the search bar as it leaves.
 *
 * [startPadding] lets the host move the rail clear of a display cutout or side navigation bar.
 * Short windows (landscape phones) get compact items so everything fits without scrolling.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryNavigationRailContent(
    selectedRoute: String?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    onBackClick: (() -> Unit)? = null,
    selectionContent: (@Composable () -> Unit)? = null,
    startPadding: Dp = GalleryChrome.RailStartPadding,
) {
    val pillModifier = galleryFloatingSurface(RoundedCornerShape(GalleryChrome.RailCornerRadius))
    val circleModifier = galleryFloatingSurface()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(start = startPadding, bottom = 16.dp),
    ) {
        val metrics = railMetrics(maxHeight, navigationItems.size, hasBack = onBackClick != null)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .testTag(GalleryNavigationRailTag),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Keep the last action so the button still has something to run while animating out.
            val lastBack = remember { mutableStateOf(onBackClick) }
            if (onBackClick != null) lastBack.value = onBackClick
            AnimatedVisibility(
                visible = onBackClick != null,
                // No scale here: a scaled layer stops the blurred surface from drawing.
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                IconButton(
                    onClick = { lastBack.value?.invoke() },
                    modifier = Modifier
                        .padding(bottom = metrics.sectionGap)
                        .size(metrics.circleSize)
                        .then(circleModifier)
                        .testTag(GalleryNavigationRailBackTag),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(Res.string.back_cd),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // Keep the last selection content alive while the panel animates out.
            val lastSelection = remember { mutableStateOf(selectionContent) }
            if (selectionContent != null) lastSelection.value = selectionContent
            AnimatedContent(
                targetState = selectionContent != null,
                modifier = Modifier.weight(1f, fill = false),
                contentAlignment = Alignment.TopCenter,
                transitionSpec = {
                    val origin = TransformOrigin(0.5f, 0f)
                    (fadeIn() + scaleIn(initialScale = 0.92f, transformOrigin = origin))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 0.92f, transformOrigin = origin))
                        .using(SizeTransform(clip = false))
                },
                label = "railContent",
            ) { selecting ->
                if (selecting) {
                    Column(
                        modifier = Modifier
                            .width(GalleryChrome.RailItemWidth)
                            .then(pillModifier)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                            .testTag(GalleryNavigationRailSelectionTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        lastSelection.value?.invoke()
                    }
                } else {
                    AnimatedVisibility(
                        visible = onBackClick == null,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top) + scaleIn(initialScale = 0.9f),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top) + scaleOut(targetScale = 0.9f),
                    ) {
                        RailDestinations(
                            selectedRoute = selectedRoute,
                            navigationItems = navigationItems,
                            onClick = onClick,
                            pillModifier = pillModifier,
                            metrics = metrics,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = onSearchClick != null && selectionContent == null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                // This circle's own visibility drives the morph into and out of the search screen.
                val searchBoundsModifier =
                    if (sharedTransitionScope != null) {
                        Modifier.sharedSearchBounds(sharedTransitionScope, this)
                    } else Modifier
                IconButton(
                    onClick = { onSearchClick?.invoke() },
                    modifier = Modifier
                        .padding(top = metrics.sectionGap)
                        .then(searchBoundsModifier)
                        .size(metrics.circleSize)
                        .then(circleModifier)
                        .testTag(GalleryNavigationRailSearchTag),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(Res.string.search_media),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun RailDestinations(
    selectedRoute: String?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit,
    pillModifier: Modifier,
    metrics: RailMetrics,
) {
    val selectedIndex = navigationItems.indexOfFirst { it.route == selectedRoute }
    val target = selectedIndex.coerceAtLeast(0).toFloat()
    val position by animateFloatAsState(target, GalleryIndicatorSpring, label = "railIndicator")
    val indicatorAlpha by animateFloatAsState(
        if (selectedIndex >= 0) 1f else 0f,
        label = "railIndicatorAlpha",
    )
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = Modifier
            .then(pillModifier)
            .drawBehind {
                if (navigationItems.isEmpty() || indicatorAlpha <= 0f) return@drawBehind
                val top = metrics.pillPadding.toPx()
                val step = (metrics.itemHeight + metrics.itemGap).toPx()
                val itemHeight = metrics.itemHeight.toPx()
                val travel = abs(position - target).coerceAtMost(1f)
                val width = GalleryChrome.RailIndicatorWidth.toPx()
                val height = (metrics.itemHeight - 16.dp).coerceAtMost(GalleryChrome.RailIndicatorHeight).toPx() +
                    20.dp.toPx() * travel
                val centre = top + position * step + itemHeight / 2
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset((size.width - width) / 2, centre - height / 2),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(GalleryChrome.IndicatorCornerRadius.toPx()),
                    alpha = indicatorAlpha,
                )
            }
            .selectableGroup()
            .padding(vertical = metrics.pillPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.itemGap),
    ) {
        navigationItems.forEach { item ->
            GalleryRailItem(
                navItem = item,
                isSelected = item.route == selectedRoute,
                onClick = onClick,
                height = metrics.itemHeight,
            )
        }
    }
}

@Composable
private fun GalleryRailItem(
    navItem: NavigationItem,
    isSelected: Boolean,
    onClick: (route: String) -> Unit,
    height: Dp,
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "railIconColor",
    )
    Box(
        modifier = Modifier
            .size(width = GalleryChrome.RailItemWidth, height = height)
            .testTag(galleryNavigationItemTag(navItem.route))
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = { onClick(navItem.route) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = navItem.icon,
            contentDescription = navItem.name,
            tint = iconColor,
        )
    }
}

/** Item geometry for the available height; short windows trade padding for fitting on screen. */
internal fun railMetrics(maxHeight: Dp, itemCount: Int, hasBack: Boolean): RailMetrics {
    val regular = RailMetrics(
        itemHeight = GalleryChrome.RailItemHeight,
        itemGap = GalleryChrome.RailItemGap,
        pillPadding = GalleryChrome.RailVerticalPadding,
        circleSize = GalleryChrome.RailItemWidth,
        sectionGap = 12.dp,
    )
    // Compact keeps the 48 dp minimum touch target of the standard back button.
    val compact = RailMetrics(
        itemHeight = 48.dp,
        itemGap = 2.dp,
        pillPadding = 4.dp,
        circleSize = 48.dp,
        sectionGap = 8.dp,
    )
    return if (regular.requiredHeight(itemCount, hasBack) <= maxHeight) regular else compact
}

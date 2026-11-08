/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.NavigationItem
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.core.presentation.util.sharedSearchBounds
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.supportsBlur
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.ui.theme.ComponentSize
import com.dot.gallery.ui.theme.GalleryChrome
import com.dot.gallery.ui.theme.Spacing
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.search_media
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

const val GalleryNavigationDockTag = "Gallery.NavigationDock"
const val GalleryNavigationSearchTag = "Gallery.NavigationSearch"
private const val GalleryNavigationItemTagPrefix = "Gallery.NavigationItem."

fun galleryNavigationItemTag(route: String): String = GalleryNavigationItemTagPrefix + route

/** Spring shared by the dock and rail indicators so both feel identical while switching tabs. */
internal val GalleryIndicatorSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = Spring.StiffnessMediumLow,
)

/** Blurred floating surface shared by the dock, its search action and the wide-window rail. */
@Composable
fun galleryFloatingSurface(shape: Shape = CircleShape): Modifier {
    val allowBlur by rememberPreference(SettingsKeys.allowBlur.withDefault(supportsBlur))
    // A translucent base under the blur (the reference uses an 80% surface plus blur) keeps the
    // controls visible even where the blur has no content behind it to sample.
    val backgroundModifier = if (allowBlur) {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    }
    return Modifier
        .clip(shape)
        .then(backgroundModifier)
        .hazeEffectScaled(
            state = LocalHazeState.current,
            style = LocalHazeBlurStyle.current,
        )
}

/**
 * Floating destination group with a separate, thumb-reachable search action.
 * Its bounded width keeps the dock compact on both phones and large previews.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryNavigationBarContent(
    modifier: Modifier = Modifier,
    selectedRoute: String?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit,
    onSearchClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val surfaceModifier = galleryFloatingSurface()
    val selectedIndex = navigationItems.indexOfFirst { it.route == selectedRoute }
    val target = selectedIndex.coerceAtLeast(0).toFloat()
    val position by animateFloatAsState(target, GalleryIndicatorSpring, label = "dockIndicator")
    val indicatorAlpha by animateFloatAsState(
        if (selectedIndex >= 0) 1f else 0f,
        label = "dockIndicatorAlpha",
    )
    val indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val itemPadding = 12.dp
    Box(
        modifier = modifier
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Medium),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = GalleryChrome.DockMaxWidth)
                .fillMaxWidth()
                .testTag(GalleryNavigationDockTag),
            horizontalArrangement = Arrangement.spacedBy(GalleryChrome.DockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(ComponentSize.NavigationBarHeight)
                    .then(surfaceModifier)
                    .drawBehind {
                        if (navigationItems.isEmpty() || indicatorAlpha <= 0f) return@drawBehind
                        val pad = itemPadding.toPx()
                        val itemWidth = (size.width - pad * 2) / navigationItems.size
                        // Stretch along the travel direction while the indicator is still moving.
                        val travel = abs(position - target).coerceAtMost(1f)
                        val width = GalleryChrome.DockIndicatorWidth.toPx() + 20.dp.toPx() * travel
                        val height = GalleryChrome.DockIndicatorHeight.toPx()
                        val centre = pad + (position + 0.5f) * itemWidth
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - centre else centre
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(x - width / 2, (size.height - height) / 2),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(GalleryChrome.IndicatorCornerRadius.toPx()),
                            alpha = indicatorAlpha,
                        )
                    }
                    .selectableGroup()
                    .padding(horizontal = itemPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationItems.forEach { item ->
                    GalleryNavBarItem(
                        navItem = item,
                        isSelected = item.route == selectedRoute,
                        onClick = onClick,
                    )
                }
            }
            if (onSearchClick != null) {
                val searchBoundsModifier =
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        Modifier.sharedSearchBounds(sharedTransitionScope, animatedVisibilityScope)
                    } else Modifier
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .then(searchBoundsModifier)
                        .size(ComponentSize.NavigationBarHeight)
                        .then(surfaceModifier)
                        .testTag(GalleryNavigationSearchTag),
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
fun RowScope.GalleryNavBarItem(
    navItem: NavigationItem,
    isSelected: Boolean,
    onClick: (route: String) -> Unit,
) {
    val selectedIconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "selectedIconColor"
    )
    Box(
        modifier = Modifier
            .height(ComponentSize.NavigationBarHeight)
            .weight(1f)
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
            tint = selectedIconColor,
        )
    }
}

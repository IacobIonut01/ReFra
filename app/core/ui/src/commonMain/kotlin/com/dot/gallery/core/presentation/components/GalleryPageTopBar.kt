/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.core.presentation.util.rememberTopBarBlurStyle
import com.dot.gallery.core.ui.generated.resources.back_cd
import com.dot.gallery.ui.theme.GalleryGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import com.dot.gallery.ui.theme.GalleryChrome
import com.dot.gallery.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.isSpecified

/**
 * Header of a pushed gallery page in the Library style: a 48 dp circular back button, the page
 * title with an optional count on its baseline, and a joined group of [actions] built from
 * [GalleryPageTopBarAction]. At rest it paints the page colour so content scrolling underneath
 * stays hidden. With a [hazeState] and [scrolled] set, it turns into the transparent blur bar:
 * a progressive blur over the content plus a fade tint, and its round buttons take the
 * `glass/chrome` tint.
 */
@Composable
fun GalleryPageTopBar(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
    onBack: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationContentDescription: String? = null,
    onTitleClick: (() -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    hazeState: HazeState? = null,
    scrolled: Boolean = false,
    /**
     * Colour of the title and count; unspecified uses the theme's. Set it when the page knows
     * what sits behind the blurred bar, like the map, whose style can be darker than the theme.
     */
    titleColor: Color = Color.Unspecified,
    /** Blur of the scrolled bar; null uses [rememberTopBarBlurStyle]. */
    blurStyle: HazeBlurStyle? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val blurred = hazeState != null && scrolled
    val chromeColor = if (blurred) GalleryGlass.chrome else MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceModifier = if (hazeState != null && scrolled) {
        Modifier.hazeEffectScaled(
            state = hazeState,
            style = blurStyle ?: rememberTopBarBlurStyle(),
            glassContainerColor = MaterialTheme.colorScheme.surface,
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surface)
    }
    CompositionLocalProvider(LocalTopBarChromeColor provides chromeColor) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(surfaceModifier)
                .windowInsetsPadding(statusBarInsets)
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = GalleryChrome.ToolbarVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NavigationBackButton(
                modifier = Modifier.size(GalleryChrome.ToolbarButtonSize),
                paddingModifier = Modifier,
                icon = navigationIcon,
                contentDescription = navigationContentDescription ?: stringResource(Res.string.back_cd),
                containerColor = chromeColor,
                forcedAction = onBack,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onTitleClick != null) {
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = onTitleClick)
                                .padding(vertical = 8.dp)
                        } else Modifier
                    )
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignByBaseline()
                        .semantics { heading() },
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor.takeOrElse { MaterialTheme.colorScheme.onSurface },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count != null) {
                    Text(
                        modifier = Modifier.alignByBaseline(),
                        text = count,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (titleColor.isSpecified) titleColor.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (titleTrailing != null) {
                    Box(modifier = Modifier.align(Alignment.CenterVertically)) { titleTrailing() }
                }
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(GalleryChrome.ToolbarCornerRadius)),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Hairline),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/**
 * Whether content has scrolled under a [GalleryPageTopBar]: the `scrolled` flag for a page whose
 * list, grid or column scrolls with [state].
 */
@Composable
fun rememberScrolledUnderTopBar(state: ScrollableState): Boolean {
    val scrolled by remember(state) { derivedStateOf { state.canScrollBackward } }
    return scrolled
}

/** Container colour of the round buttons on the current [GalleryPageTopBar]. */
private val LocalTopBarChromeColor = compositionLocalOf<Color?> { null }

/** One cell of the [GalleryPageTopBar] action group. Wrap it in a Box to anchor a menu. */
@Composable
fun GalleryPageTopBarAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Icon colour; unspecified uses the bar's icon colour. Set it for a toggled-on state. */
    tint: Color = Color.Unspecified,
) {
    Box(
        modifier = modifier
            .size(GalleryChrome.ToolbarButtonSize)
            .background(LocalTopBarChromeColor.current ?: MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
            )
        }
    }
}

/**
 * Tonal filter chip shared by the Library pages: a pill at rest, tighter 12 dp corners and the
 * primary container once picked.
 */
@Composable
fun GalleryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corner by animateDpAsState(if (selected) 12.dp else 20.dp, label = "galleryChipCorner")
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "galleryChipContainer"
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "galleryChipContent"
    )
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

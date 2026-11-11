/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import com.dot.gallery.core.presentation.components.GalleryHomeAction
import com.dot.gallery.core.presentation.components.GalleryHomeTopBar
import com.dot.gallery.core.presentation.components.HomeShortcutVisibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.refra_mark
import com.dot.gallery.ui.theme.GalleryChrome
import org.jetbrains.compose.resources.painterResource

/**
 * A stand-in photo for settings previews: a soft two-tone gradient from the current palette, so
 * previews follow the user's theme without shipping sample images. [index] picks one of six
 * pairings; neighbouring indexes look different.
 */
@Composable
fun PreviewPhoto(index: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val pairs = listOf(
        c.primaryContainer to c.primary,
        c.tertiaryContainer to c.tertiary,
        c.secondaryContainer to c.secondary,
        c.primary to c.tertiaryContainer,
        c.tertiary to c.primaryContainer,
        c.surfaceContainerHighest to c.secondary,
    )
    val (start, end) = pairs[((index % pairs.size) + pairs.size) % pairs.size]
    Box(
        modifier = modifier.background(
            Brush.linearGradient(colors = listOf(start, end))
        )
    )
}

/**
 * A miniature of the Timeline home: the brand button and actions capsule, the highlights row,
 * a date label, the edge-to-edge grid and the floating navigation with its search button. It
 * keeps the 104:184 phone proportion and scales with the width it is given; every colour comes
 * from the current [MaterialTheme], so wrap it in a theme to preview another palette.
 */
@Composable
fun MiniHomeScreen(
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(104f / 184f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val u = maxWidth / 104f
        val c = MaterialTheme.colorScheme

        @Composable
        fun Block(
            x: Float, y: Float, w: Float, h: Float,
            radius: Float = 0f,
            color: Color? = null,
            photo: Int? = null,
        ) {
            val shape = RoundedCornerShape(u * radius)
            val base = Modifier
                .offset(u * x, u * y)
                .size(u * w, u * h)
                .clip(shape)
            if (photo != null) {
                PreviewPhoto(index = photo, modifier = base)
            } else {
                Box(base.background(color ?: Color.Transparent))
            }
        }

        // Edge-to-edge grid, one large photo leading.
        Block(0f, 98f, 69f, 69f, photo = 0)
        Block(70f, 98f, 34f, 34f, photo = 1)
        Block(70f, 133f, 34f, 34f, photo = 2)
        Block(0f, 168f, 34f, 34f, photo = 3)
        Block(35f, 168f, 34f, 34f, photo = 4)
        Block(70f, 168f, 34f, 34f, photo = 5)
        // Date label.
        Block(4f, 90f, 32f, 3.5f, radius = 1.75f, color = c.onSurface)
        // Highlights: two cards and the edge of a third.
        Block(4f, 24f, 54f, 58f, radius = 7.5f, photo = 3)
        Block(60f, 24f, 38f, 58f, radius = 7.5f, photo = 1)
        Block(100f, 24f, 30f, 58f, radius = 7.5f, photo = 4)
        Block(8f, 70f, 20f, 3.5f, radius = 1.75f, color = Color.White)
        Block(64f, 70f, 18f, 3.5f, radius = 1.75f, color = Color.White)
        // Header: brand button and the actions capsule.
        Box(
            modifier = Modifier
                .offset(u * 4f, u * 7f)
                .size(u * 13f)
                .clip(CircleShape)
                .background(c.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.refra_mark),
                contentDescription = null,
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(u * 6.5f).graphicsLayer { scaleX = 2.35f; scaleY = 2.35f },
            )
        }
        Row(
            modifier = Modifier
                .offset(u * if (showControls) 70f else 85f, u * 7f)
                .height(u * 13f)
                .clip(RoundedCornerShape(u * 6.5f))
                .background(c.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showControls) {
                Box(Modifier.size(u * 14.5f, u * 13f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.FilterList, null, tint = c.onSurfaceVariant, modifier = Modifier.size(u * 8f))
                }
                Box(Modifier.width(u * 0.5f).height(u * 13f).background(c.surface))
            }
            Box(Modifier.size(u * 14.5f, u * 13f), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MoreVert, null, tint = c.onSurfaceVariant, modifier = Modifier.size(u * 8f))
            }
        }
        // Floating navigation and search.
        Row(
            modifier = Modifier
                .offset(u * 10f, u * 162f)
                .size(u * 62f, u * 17f)
                .clip(RoundedCornerShape(u * 8.5f))
                .background(c.surfaceContainerHigh)
                .padding(horizontal = u * 9f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(u * 13f, u * 11f).clip(RoundedCornerShape(u * 5.5f)).background(c.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(u * 5f).clip(RoundedCornerShape(u)).background(c.onSecondaryContainer))
            }
            Box(Modifier.size(u * 6f, u * 5f).clip(RoundedCornerShape(u)).background(c.onSurfaceVariant))
            Box(Modifier.size(u * 6f, u * 5f).clip(RoundedCornerShape(u)).background(c.onSurfaceVariant))
        }
        Box(
            modifier = Modifier
                .offset(u * 76f, u * 162f)
                .size(u * 17f)
                .clip(CircleShape)
                .background(c.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(u * 5f).border(u * 1.2f, c.onSurfaceVariant, CircleShape))
        }
    }
}

/**
 * The top of the Timeline home at full size, drawn by the real [GalleryHomeTopBar] so buttons
 * grow and shrink in place exactly as they do on Timeline, over the tops of the highlight
 * cards. [shortcuts] decides Select, Favorites and Search; [showFilter] the sort and filter
 * button. It is a picture: touches are swallowed and accessibility reads only [description]
 * and [visibleButtons], the names of the buttons it currently shows.
 */
@Composable
fun PreviewHomeTopBar(
    shortcuts: HomeShortcutVisibility,
    showFilter: Boolean,
    description: String,
    visibleButtons: String,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = description
            stateDescription = visibleButtons
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            GalleryHomeTopBar(
                onBrandClick = {},
                brandDescription = "",
                statusBarInsets = WindowInsets(0, 0, 0, 0),
                actions = listOf(
                    GalleryHomeAction("select", Icons.Outlined.CheckCircle, "", shortcuts.select) {},
                    GalleryHomeAction("favorites", Icons.Outlined.FavoriteBorder, "", shortcuts.favorites) {},
                    GalleryHomeAction("search", Icons.Outlined.Search, "", shortcuts.search) {},
                ),
                filter = if (showFilter) {
                    { PreviewToolbarIcon(Icons.Outlined.FilterList, tint) }
                } else null,
                menu = { PreviewToolbarIcon(Icons.Outlined.MoreVert, tint) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PreviewPhoto(3, Modifier.weight(1.4f).height(120.dp).clip(RoundedCornerShape(28.dp)))
                PreviewPhoto(1, Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(28.dp)))
                PreviewPhoto(4, Modifier.weight(0.4f).height(120.dp).clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)))
            }
        }
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
        )
    }
}

@Composable
private fun PreviewToolbarIcon(icon: ImageVector, tint: Color) {
    Box(Modifier.size(GalleryChrome.ToolbarButtonSize), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

/** A round 64 dp `secondaryContainer` badge holding one [icon], for choices without a mock. */
@Composable
fun PreviewIconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** A single focused stand-in photo, 120 dp square with 16 dp corners. */
@Composable
fun PreviewFocusPhoto(
    index: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(16.dp))) {
        PreviewPhoto(index = index, modifier = Modifier.fillMaxSize())
        overlay()
    }
}

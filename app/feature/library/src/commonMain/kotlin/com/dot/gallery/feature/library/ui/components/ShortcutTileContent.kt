/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.library.ui.LibraryShortcutSize
import com.dot.gallery.feature.library.ui.RuntimeShortcut
import com.dot.gallery.ui.theme.BlackScrim

internal val TILE_SHAPE = RoundedCornerShape(28.dp)
internal val CARD_SHAPE = RoundedCornerShape(24.dp)

internal fun shortcutShape(size: LibraryShortcutSize) = if (size.isCard) CARD_SHAPE else TILE_SHAPE

private val PreviewScrim = Brush.verticalGradient(listOf(Color.Transparent, BlackScrim))
private val ThumbSize = 36.dp
private val ThumbOverlap = 10.dp

private fun shortcutCount(count: Int) = if (count > 99) "99+" else count.toString()

/**
 * What a shortcut shows at [size]: an icon at 1×1, icon and label on one row, and a card once
 * it is taller. Cards show [RuntimeShortcut.preview] photos when the shortcut has any and an
 * icon card otherwise, so a vault never shows its contents at any size.
 */
@Composable
internal fun ShortcutTile(
    data: RuntimeShortcut,
    size: LibraryShortcutSize,
    oldestFirst: Boolean,
    showIndicator: Boolean,
    modifier: Modifier = Modifier,
) {
    val count = data.indicatorCounter.takeIf { data.useIndicator && showIndicator && it > 0 }
    val media = data.preview(oldestFirst)
    Box(modifier = modifier.clip(shortcutShape(size))) {
        when {
            size == LibraryShortcutSize.ICON -> IconTile(data, count)
            size == LibraryShortcutSize.HALF -> LibrarySmallItem(
                title = data.title,
                icon = data.icon,
                contentColor = data.contentColor,
                shape = TILE_SHAPE,
                useIndicator = count != null,
                indicatorCounter = data.indicatorCounter,
                modifier = Modifier.fillMaxSize()
            )
            !size.isCard -> WideTile(data, count, media)
            media.isNotEmpty() -> PhotoCard(data, size, count, media)
            else -> IconCard(data, count)
        }
    }
}

@Composable
private fun ShortcutIcon(data: RuntimeShortcut, tint: Color, size: Dp, description: String? = null) {
    data.icon?.let {
        Icon(imageVector = it, contentDescription = description, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun IconTile(data: RuntimeShortcut, count: Int?) {
    Row(
        modifier = Modifier.fillMaxSize().background(data.contentColor.copy(alpha = 0.1f)).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data.icon != null) {
            ShortcutIcon(data, data.contentColor, 24.dp, description = data.title)
        } else {
            Text(
                text = data.title,
                style = MaterialTheme.typography.labelLarge,
                color = data.contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (count != null) {
            Text(
                text = shortcutCount(count),
                style = MaterialTheme.typography.bodySmall,
                color = data.contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WideTile(data: RuntimeShortcut, count: Int?, media: List<UriMedia>) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(data.contentColor.copy(alpha = 0.1f))
            .padding(start = 16.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortcutIcon(data, data.contentColor, 24.dp)
        Text(
            modifier = Modifier.weight(1f),
            text = data.title,
            style = MaterialTheme.typography.labelLarge,
            color = data.contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (media.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(-ThumbOverlap)) {
                media.take(3).forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(ThumbSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                    ) {
                        PreviewImage(item, data.dimPreview, Modifier.fillMaxSize())
                    }
                }
            }
        } else data.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = data.contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (count != null) {
            Text(
                text = shortcutCount(count),
                style = MaterialTheme.typography.bodySmall,
                color = data.contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PhotoCard(data: RuntimeShortcut, size: LibraryShortcutSize, count: Int?, media: List<UriMedia>) {
    val cells = when (size) {
        LibraryShortcutSize.TALL_CARD, LibraryShortcutSize.WIDE_CARD ->
            if (media.size >= 4) media.take(4) else media.take(1)
        else -> media.take(1)
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        when {
            cells.size == 1 -> PreviewImage(cells.first(), data.dimPreview, Modifier.fillMaxSize())
            size == LibraryShortcutSize.WIDE_CARD -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                cells.forEach { PreviewImage(it, data.dimPreview, Modifier.weight(1f).fillMaxHeight()) }
            }
            else -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                cells.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        row.forEach { PreviewImage(it, data.dimPreview, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        }
        CardLabel(
            data = data,
            count = count,
            subtitle = data.subtitle.takeIf { size != LibraryShortcutSize.CARD },
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(PreviewScrim)
                .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun IconCard(data: RuntimeShortcut, count: Int?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(data.contentColor.copy(alpha = 0.1f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ShortcutIcon(data, data.contentColor, 32.dp)
        CardLabel(data = data, count = count, subtitle = data.subtitle, color = data.contentColor)
    }
}

@Composable
private fun CardLabel(
    data: RuntimeShortcut,
    count: Int?,
    subtitle: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (subtitle == null) ShortcutIcon(data, color, 20.dp)
            Text(
                modifier = Modifier.weight(1f),
                text = data.title,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count != null) {
                Text(text = shortcutCount(count), style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreviewImage(media: UriMedia, dim: Boolean, modifier: Modifier) {
    LocalMediaImageRenderer.current.RenderImage(
        modifier = if (dim) {
            modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Gray, blendMode = BlendMode.Saturation)
                }
        } else modifier,
        contentScale = ContentScale.Crop,
        model = media.getUri(),
        contentDescription = null,
        signature = media,
    )
}

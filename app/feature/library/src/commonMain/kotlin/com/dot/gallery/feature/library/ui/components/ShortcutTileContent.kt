/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.library.ui.LibraryShortcutPref
import com.dot.gallery.feature.library.ui.LibraryShortcutSize
import com.dot.gallery.feature.library.ui.RuntimeShortcut
import com.dot.gallery.feature.library.ui.ShortcutPreviewStyle
import com.dot.gallery.ui.theme.BlackScrim

internal val TILE_SHAPE = RoundedCornerShape(28.dp)
internal val CARD_SHAPE = RoundedCornerShape(24.dp)

internal fun shortcutShape(size: LibraryShortcutSize) = if (size.isCard) CARD_SHAPE else TILE_SHAPE

/** A map, or the map's slot, drawn behind the Locations card. */
typealias ShortcutMapContent = @Composable BoxScope.(latitude: Double, longitude: Double) -> Unit

private val PreviewScrim = Brush.verticalGradient(listOf(Color.Transparent, BlackScrim))
private val ThumbSize = 36.dp
private val ThumbOverlap = 10.dp
private val PrintShape = RoundedCornerShape(6.dp)
private val SpreadShape = RoundedCornerShape(16.dp)

private fun shortcutCount(count: Int) = if (count > 99) "99+" else count.toString()

/**
 * What a shortcut shows at its size: an icon at 1×1, icon, label and count on one row, and a
 * card once it is taller. How a card shows its photos follows [RuntimeShortcut.previewStyle];
 * a card with nothing to show falls back to its icon. A vault only ever gets abstract shapes.
 */
@Composable
internal fun ShortcutTile(
    data: RuntimeShortcut,
    pref: LibraryShortcutPref,
    showIndicator: Boolean,
    modifier: Modifier = Modifier,
    mapContent: ShortcutMapContent? = null,
) {
    val size = pref.size
    val count = data.indicatorCounter.takeIf { data.useIndicator && showIndicator && it > 0 }
    val media = data.preview(pref.oldestFirst, pref.shuffle)
    val style = data.previewStyle
    Box(modifier = modifier.clip(shortcutShape(size))) {
        when {
            size == LibraryShortcutSize.ICON -> IconTile(data, count)
            !size.isCard -> WideTile(data, count, media, showPreviews = size != LibraryShortcutSize.HALF)
            style == ShortcutPreviewStyle.PLACEHOLDER -> PlaceholderCard(data, size, count)
            style == ShortcutPreviewStyle.MAP && (media.isNotEmpty() || data.mapLatitude != null) ->
                MapCard(data, count, media.firstOrNull(), mapContent)
            style == ShortcutPreviewStyle.FACES && data.previewFaces.isNotEmpty() -> FacesCard(data, size, count)
            media.isEmpty() -> IconCard(data, count)
            style == ShortcutPreviewStyle.SPREAD -> SpreadCard(data, size, count, media)
            pref.scrapbook -> ScrapbookCard(data, size, count, media)
            else -> PhotoCard(data, size, count, media)
        }
    }
}

@Composable
private fun ShortcutIcon(data: RuntimeShortcut, tint: Color, size: Dp, description: String? = null) {
    data.icon?.let {
        Icon(
            imageVector = it,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(size).testTag("library-shortcut-icon"),
        )
    }
}

/** The title with its count right after it, "Favorites · 12", so the count reads as the title's. */
@Composable
private fun TitleWithCount(
    title: String,
    count: Int?,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            text = title,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = "· ${shortcutCount(count)}",
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
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
private fun WideTile(data: RuntimeShortcut, count: Int?, media: List<UriMedia>, showPreviews: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(data.contentColor.copy(alpha = 0.1f))
            .padding(start = 16.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortcutIcon(data, data.contentColor, 24.dp)
        TitleWithCount(
            title = data.title,
            count = count,
            color = data.contentColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        if (!showPreviews) return@Row
        when {
            data.previewStyle == ShortcutPreviewStyle.PLACEHOLDER -> ThumbRow(3) { index, thumb ->
                PlaceholderSwatch(data, index, thumb)
            }
            data.previewStyle == ShortcutPreviewStyle.FACES && data.previewFaces.isNotEmpty() ->
                ThumbRow(data.previewFaces.size.coerceAtMost(3)) { index, thumb ->
                    Face(data.previewFaces[index], thumb)
                }
            media.isNotEmpty() -> ThumbRow(media.size.coerceAtMost(3)) { index, thumb ->
                PreviewImage(media[index], data.dimPreview, thumb)
            }
            else -> data.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = data.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** Up to three overlapping round thumbnails at the end of a one-row tile. */
@Composable
private fun ThumbRow(count: Int, thumb: @Composable (index: Int, modifier: Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(-ThumbOverlap)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(ThumbSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
            ) {
                thumb(index, Modifier.fillMaxSize())
            }
        }
    }
}

/** Four photos on the larger cards, one otherwise, as in the Favorites and Trash covers. */
private fun cardCells(size: LibraryShortcutSize, available: Int): Int = when (size) {
    LibraryShortcutSize.TALL_CARD, LibraryShortcutSize.WIDE_CARD -> if (available >= 4) 4 else 1
    else -> 1
}

@Composable
private fun PhotoGrid(size: LibraryShortcutSize, cells: Int, cell: @Composable (index: Int, modifier: Modifier) -> Unit) {
    when {
        cells == 1 -> cell(0, Modifier.fillMaxSize())
        size == LibraryShortcutSize.WIDE_CARD -> Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(cells) { cell(it, Modifier.weight(1f).fillMaxHeight()) }
        }
        else -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            (0 until cells).chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    row.forEach { cell(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ScrimLabel(data: RuntimeShortcut, size: LibraryShortcutSize, count: Int?) {
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

@Composable
private fun PhotoCard(data: RuntimeShortcut, size: LibraryShortcutSize, count: Int?, media: List<UriMedia>) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        PhotoGrid(size, cardCells(size, media.size)) { index, cell ->
            PreviewImage(media[index], data.dimPreview, cell)
        }
        ScrimLabel(data, size, count)
    }
}

/** Where each print sits, as a fraction of the card, and how far it is turned. */
private data class Print(val x: Float, val y: Float, val rotation: Float)

private val SquarePrints = listOf(
    Print(0.36f, 0.38f, -7f), Print(0.66f, 0.32f, 8f), Print(0.32f, 0.64f, 5f), Print(0.68f, 0.62f, -10f),
)
private val WidePrints = listOf(
    Print(0.20f, 0.44f, -7f), Print(0.42f, 0.38f, 6f), Print(0.62f, 0.50f, -4f), Print(0.82f, 0.40f, 9f),
)

/**
 * Favourites as small prints dropped on top of each other, the latest on top, covering the card
 * like a scrapbook page.
 */
@Composable
private fun ScrapbookCard(data: RuntimeShortcut, size: LibraryShortcutSize, count: Int?, media: List<UriMedia>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library-shortcut-scrapbook")
            .background(data.contentColor.copy(alpha = 0.12f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight * 1.5f
            val prints = if (wide) WidePrints else SquarePrints
            val printSize = minOf(maxWidth, maxHeight) * if (wide) 0.7f else 0.56f
            media.take(prints.size).withIndex().reversed().forEach { (index, item) ->
                val print = prints[index]
                Box(
                    modifier = Modifier
                        .offset(
                            x = maxWidth * print.x - printSize / 2,
                            y = maxHeight * print.y - printSize / 2,
                        )
                        .size(printSize)
                        .graphicsLayer { rotationZ = print.rotation }
                        .shadow(4.dp, PrintShape)
                        .background(Color.White, PrintShape)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    PreviewImage(item, data.dimPreview, Modifier.fillMaxSize())
                }
            }
        }
        ScrimLabel(data, size, count)
    }
}

/** Separate rounded thumbnails with room between them, so each reads as its own item. */
@Composable
private fun SpreadCard(data: RuntimeShortcut, size: LibraryShortcutSize, count: Int?, media: List<UriMedia>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(data.contentColor.copy(alpha = 0.1f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val spread = media.take(MAX_SPREAD)
        val rows = if (size == LibraryShortcutSize.WIDE_CARD || spread.size < 3) listOf(spread) else spread.chunked(2)
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { item ->
                        PreviewImage(item, data.dimPreview, Modifier.weight(1f).fillMaxHeight().clip(SpreadShape))
                    }
                }
            }
        }
        CardLabel(
            data = data,
            count = count,
            subtitle = null,
            color = data.contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private const val MAX_SPREAD = 4

/** Round faces in a row, as many as fit, over the People label. */
@Composable
private fun FacesCard(data: RuntimeShortcut, size: LibraryShortcutSize, count: Int?) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val faceSize = if (size == LibraryShortcutSize.CARD) 36.dp else 48.dp
    val overlap = faceSize / 6
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fit = ((maxWidth + overlap) / (faceSize - overlap)).toInt().coerceAtLeast(1)
            Row(horizontalArrangement = Arrangement.spacedBy(-overlap)) {
                data.previewFaces.take(fit).forEach { face ->
                    Box(
                        modifier = Modifier
                            .size(faceSize)
                            .clip(CircleShape)
                            .background(surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                    ) {
                        Face(face, Modifier.fillMaxSize())
                    }
                }
            }
        }
        CardLabel(data = data, count = count, subtitle = null, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Face(model: String?, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            LocalMediaImageRenderer.current.RenderImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.People,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The latest place on a map, with the label in a pill so the map stays the content. */
@Composable
private fun MapCard(data: RuntimeShortcut, count: Int?, media: UriMedia?, mapContent: ShortcutMapContent?) {
    Box(modifier = Modifier.fillMaxSize()) {
        MapPreviewCard(
            modifier = Modifier.fillMaxSize(),
            height = null,
            thumbnailSize = 56.dp,
            // Keeps the photo clear of the label pill on the shorter cards.
            thumbnailBottomInset = 40.dp,
            latestMedia = media,
            latitude = data.mapLatitude,
            longitude = data.mapLongitude,
            mapContent = mapContent,
        )
        TitleWithCount(
            title = data.title,
            count = count,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * Laid out like a photo card, but every cell is blurred colour, so the tile looks like the
 * others without showing anything from inside it, locked or not.
 */
@Composable
private fun PlaceholderCard(data: RuntimeShortcut, size: LibraryShortcutSize, count: Int?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library-shortcut-placeholder")
            .background(data.contentColor.copy(alpha = 0.1f))
    ) {
        PhotoGrid(size, cardCells(size, MAX_SPREAD)) { index, cell -> PlaceholderSwatch(data, index, cell) }
        CardLabel(
            data = data,
            count = count,
            subtitle = data.subtitle.takeIf { size != LibraryShortcutSize.CARD },
            color = data.contentColor,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp),
        )
    }
}

/** Soft blobs of the tile's colours, placed differently per [index] so cells do not repeat. */
@Composable
private fun PlaceholderSwatch(data: RuntimeShortcut, index: Int, modifier: Modifier) {
    val first = data.contentColor
    val second = MaterialTheme.colorScheme.tertiary
    Box(modifier = modifier.background(first.copy(alpha = 0.14f))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(24.dp)
                .drawBehind {
                    val shift = (index % 4) * 0.17f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(first.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * (0.25f + shift), size.height * (0.3f + shift / 2)),
                            radius = size.maxDimension * 0.6f,
                        ),
                        radius = size.maxDimension * 0.6f,
                        center = Offset(size.width * (0.25f + shift), size.height * (0.3f + shift / 2)),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(second.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(size.width * (0.8f - shift), size.height * (0.75f - shift / 3)),
                            radius = size.maxDimension * 0.5f,
                        ),
                        radius = size.maxDimension * 0.5f,
                        center = Offset(size.width * (0.8f - shift), size.height * (0.75f - shift / 3)),
                    )
                }
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
        // The card's own icon is above, so the label does not repeat it.
        CardLabel(data = data, count = count, subtitle = data.subtitle, color = data.contentColor, showIcon = false)
    }
}

@Composable
private fun CardLabel(
    data: RuntimeShortcut,
    count: Int?,
    subtitle: String?,
    color: Color,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon && subtitle == null) ShortcutIcon(data, color, 20.dp)
            TitleWithCount(
                title = data.title,
                count = count,
                color = color,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
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

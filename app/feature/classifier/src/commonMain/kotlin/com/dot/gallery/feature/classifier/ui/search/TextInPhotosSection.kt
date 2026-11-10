/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.insight.ImageQuad
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.search_photos_count
import com.dot.gallery.feature.classifier.generated.resources.search_photos_that_match
import com.dot.gallery.feature.classifier.generated.resources.search_text_in_photos
import com.dot.gallery.feature.classifier.generated.resources.search_text_match_server
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val COLUMNS = 3

/**
 * Search's "Text in photos" section: a header with the match count over the same full-bleed,
 * 1 dp-gap grid as the Timeline. Each cell is cropped around the matching line with the matching
 * words highlighted; server matches carry a cloud badge. [otherCount] titles the grid that follows.
 */
@Composable
fun TextInPhotosSection(
    matches: List<TextPhotoMatch>,
    otherCount: Int,
    onOpen: (TextPhotoMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(Res.string.search_text_in_photos),
            count = pluralStringResource(Res.plurals.search_photos_count, matches.size, matches.size),
            leading = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            matches.chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    row.forEach { match ->
                        TextMatchCell(match, onClick = { onOpen(match) }, modifier = Modifier.weight(1f))
                    }
                    repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if (otherCount > 0) {
            SectionHeader(
                title = stringResource(Res.string.search_photos_that_match),
                count = pluralStringResource(Res.plurals.search_photos_count, otherCount, otherCount),
                leading = false,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: String, leading: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leading) Icon(Icons.Outlined.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One square cell, zoomed so the matching line fills most of its width, with the words highlighted. */
@Composable
private fun TextMatchCell(match: TextPhotoMatch, onClick: () -> Unit, modifier: Modifier) {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        val cell = constraints.maxWidth.toFloat()
        val placement = cropPlacement(cell, match.aspectRatio, match.line)
        val sizeDp = with(LocalDensity.current) { DpSize(placement.width.toDp(), placement.height.toDp()) }
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset { IntOffset(placement.x.roundToInt(), placement.y.roundToInt()) }
                .requiredSize(sizeDp)
        ) {
            LocalMediaImageRenderer.current.RenderImage(
                modifier = Modifier.fillMaxSize(),
                model = match.media.getUri().toString(),
                contentScale = if (match.aspectRatio > 0f) ContentScale.FillBounds else ContentScale.Crop,
                contentDescription = match.media.label,
                signature = match.media,
            )
            val regions = match.words.ifEmpty { listOfNotNull(match.line) }
            if (regions.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    regions.forEach { quad ->
                        drawRoundRect(
                            color = highlight,
                            topLeft = Offset(quad.left * size.width, quad.top * size.height),
                            size = Size((quad.right - quad.left) * size.width, (quad.bottom - quad.top) * size.height),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }
            }
        }
        if (match.fromServer) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Cloud,
                    contentDescription = stringResource(Res.string.search_text_match_server),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The image's size and top-left offset in a [cell]-pixel square, centred on [line] and clamped to the image. */
internal fun cropPlacement(cell: Float, aspectRatio: Float, line: ImageQuad?): CropPlacement {
    if (aspectRatio <= 0f) return CropPlacement(cell, cell, 0f, 0f)
    // Cover the cell first, then zoom so the line fills about 90 % of the width (at most 4x).
    val baseWidth = if (aspectRatio >= 1f) cell * aspectRatio else cell
    val baseHeight = if (aspectRatio >= 1f) cell else cell / aspectRatio
    val zoom = line?.let { ((cell * 0.9f) / ((it.right - it.left) * baseWidth).coerceAtLeast(1f)).coerceIn(1f, 4f) } ?: 1f
    val width = baseWidth * zoom
    val height = baseHeight * zoom
    val centerX = line?.let { (it.left + it.right) / 2f } ?: 0.5f
    val centerY = line?.let { (it.top + it.bottom) / 2f } ?: 0.5f
    val x = (cell / 2f - centerX * width).coerceIn(cell - width, 0f)
    val y = (cell / 2f - centerY * height).coerceIn(cell - height, 0f)
    return CropPlacement(width, height, x, y)
}

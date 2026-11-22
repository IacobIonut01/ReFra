/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dot.gallery.feature.albums.ui.util.formatSizeShort

const val AlbumCoverLabelTag = "Album.CoverLabel"
const val AlbumLabelCountTag = "Album.LabelCount"
const val AlbumLabelSizeTag = "Album.LabelSize"

/** Covers narrower than this lose the size line, keeping only "Name · count". */
val AlbumCoverSizeLineMinWidth = 80.dp

/** Text and spacing of an on-cover label, scaled to the cover's width. */
data class AlbumCoverLabelMetrics(
    val nameSize: TextUnit,
    val sizeSize: TextUnit,
    val padding: Dp,
    val scrimLift: Dp,
    val showSize: Boolean,
)

/**
 * Scales the on-cover label with the grid cell: names run from 10 sp on the smallest covers to
 * 16 sp, so a seven-column grid still shows a name and a one-column grid does not shout.
 */
fun albumCoverLabelMetrics(coverWidth: Dp): AlbumCoverLabelMetrics {
    val w = coverWidth.value
    val name = (w * 0.1f).coerceIn(10f, 16f)
    return AlbumCoverLabelMetrics(
        nameSize = name.sp,
        sizeSize = (name * 0.86f).coerceAtLeast(10f).sp,
        padding = (w * 0.07f).coerceIn(5f, 12f).dp,
        // The scrim starts a little above the text so it fades in instead of ending in an edge.
        scrimLift = (w * 0.18f).coerceIn(4f, 40f).dp,
        showSize = coverWidth >= AlbumCoverSizeLineMinWidth,
    )
}

/** The thumbnail's corner radius, shared by the cover label so its scrim follows the press. */
@Composable
internal fun albumCornerRadius(interactionSource: InteractionSource): Dp {
    val pressed by interactionSource.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = if (pressed) 32.dp else 16.dp,
        label = "cornerRadius",
    )
    return radius
}

private val CoverTextShadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 2f)
private val CoverScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.4f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Black.copy(alpha = 0.72f),
)

/** "Name · 12": the name ellipsizes, the count never does. */
@Composable
private fun AlbumNameRow(name: String, count: Long?, style: TextStyle, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            modifier = Modifier.weight(1f, fill = false),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null) {
            Text(
                text = " · $count",
                modifier = Modifier.testTag(AlbumLabelCountTag),
                style = style.copy(fontWeight = FontWeight.Normal),
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The label laid over the bottom of an album cover: a scrim, "Name · 12" and, on covers at least
 * [AlbumCoverSizeLineMinWidth] wide, the size with one decimal. Place it at the bottom of a box
 * clipped to the cover's shape.
 */
@Composable
fun AlbumCoverLabel(
    name: String,
    count: Long?,
    sizeBytes: Long,
    coverWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val metrics = albumCoverLabelMetrics(coverWidth)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CoverScrim)
            .padding(top = metrics.scrimLift)
            .padding(horizontal = metrics.padding + 2.dp, vertical = metrics.padding)
            .testTag(AlbumCoverLabelTag),
    ) {
        AlbumNameRow(
            name = name,
            count = count,
            style = TextStyle(
                fontSize = metrics.nameSize,
                lineHeight = 1.25.em,
                fontWeight = FontWeight.Medium,
                shadow = CoverTextShadow,
            ),
            color = Color.White,
        )
        if (metrics.showSize && sizeBytes > 0) {
            Text(
                text = formatSizeShort(sizeBytes),
                modifier = Modifier.testTag(AlbumLabelSizeTag),
                style = TextStyle(
                    fontSize = metrics.sizeSize,
                    lineHeight = 1.3.em,
                    shadow = CoverTextShadow,
                ),
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The default label under the cover: "Name · 12", then the size with one decimal. */
@Composable
fun AlbumBelowLabel(
    name: String,
    count: Long?,
    sizeBytes: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp)) {
        AlbumNameRow(
            name = name,
            count = count,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (sizeBytes > 0) {
            Text(
                text = formatSizeShort(sizeBytes),
                modifier = Modifier.padding(top = 2.dp).testTag(AlbumLabelSizeTag),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

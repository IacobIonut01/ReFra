/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.dot.gallery.ui.theme.GalleryChrome
import androidx.compose.foundation.shape.RoundedCornerShape

/** Segment size inside [GallerySegmentedActionGroup]; buttons fill it instead of drawing a circle. */
val LocalActionSegmentSize = staticCompositionLocalOf<Dp?> { null }

const val GallerySegmentedActionGroupTag = "Gallery.SegmentedActionGroup"

/**
 * Connected button group: every action sits in its own segment, separated by a hairline gap,
 * with fully rounded outer ends and small inner corners. [surfaceModifier] paints the group
 * (solid colour or blur); the gaps and inner corners are cut out of it afterwards, so a blurred
 * surface keeps its look and the photo shows through between segments.
 *
 * Children that measure to zero width (popup anchors, conditional slots) take no segment.
 */
@Composable
fun GallerySegmentedActionGroup(
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    segmentSize: Dp = GalleryChrome.ActionSegmentSize,
    content: @Composable () -> Unit,
) {
    var segments by remember { mutableStateOf(emptyList<Rect>()) }
    Layout(
        content = {
            CompositionLocalProvider(LocalActionSegmentSize provides segmentSize, content = content)
        },
        modifier = modifier
            .testTag(GallerySegmentedActionGroupTag)
            .clip(RoundedCornerShape(GalleryChrome.ActionGroupCornerRadius))
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val inner = CornerRadius(GalleryChrome.ActionSegmentInnerRadius.toPx())
                val outer = CornerRadius(size.height / 2f)
                val visible = Path()
                segments.forEachIndexed { index, rect ->
                    val first = index == 0
                    val last = index == segments.lastIndex
                    visible.addRoundRect(
                        RoundRect(
                            rect = rect,
                            topLeft = if (first) outer else inner,
                            bottomLeft = if (first) outer else inner,
                            topRight = if (last) outer else inner,
                            bottomRight = if (last) outer else inner,
                        ),
                    )
                }
                val cutout = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    op(this, visible, PathOperation.Difference)
                }
                onDrawWithContent {
                    drawContent()
                    if (segments.size > 1) {
                        drawPath(cutout, Color.Black, blendMode = BlendMode.Clear)
                    }
                }
            }
            .then(surfaceModifier),
    ) { measurables, constraints ->
        val segmentPx = segmentSize.roundToPx()
        val gap = GalleryChrome.ActionSegmentGap.roundToPx()
        val placeables = measurables.map {
            it.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        }
        val segmentWidths = placeables.map { p ->
            if (p.width == 0) 0 else maxOf(p.width, segmentPx)
        }
        val height = maxOf(segmentPx, placeables.maxOfOrNull { it.height } ?: 0)
        val count = segmentWidths.count { it > 0 }
        val width = segmentWidths.sum() + gap * (count - 1).coerceAtLeast(0)
        val rects = ArrayList<Rect>(count)
        layout(width, height) {
            var x = 0
            placeables.forEachIndexed { index, placeable ->
                val segmentWidth = segmentWidths[index]
                if (segmentWidth == 0) {
                    placeable.placeRelative(0, 0)
                    return@forEachIndexed
                }
                placeable.placeRelative(
                    x + (segmentWidth - placeable.width) / 2,
                    (height - placeable.height) / 2,
                )
                rects += Rect(x.toFloat(), 0f, (x + segmentWidth).toFloat(), height.toFloat())
                x += segmentWidth + gap
            }
            if (rects != segments) segments = rects
        }
    }
}

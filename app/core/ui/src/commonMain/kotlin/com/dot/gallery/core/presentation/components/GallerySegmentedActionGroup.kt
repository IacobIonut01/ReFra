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
import androidx.compose.ui.graphics.PathFillType
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
                val cutout = Path()
                onDrawWithContent {
                    drawContent()
                    val current = segments
                    if (current.size > 1) {
                        // Bounds plus every segment, filled even-odd: segments never overlap, so
                        // this is the bounds minus the segments without Path.op, which can spin
                        // on the UI thread when the segment widths animate.
                        cutout.reset()
                        cutout.fillType = PathFillType.EvenOdd
                        cutout.addRect(Rect(0f, 0f, size.width, size.height))
                        current.forEachIndexed { index, rect ->
                            val first = index == 0
                            val last = index == current.lastIndex
                            cutout.addRoundRect(
                                RoundRect(
                                    rect = rect,
                                    topLeft = if (first) outer else inner,
                                    bottomLeft = if (first) outer else inner,
                                    topRight = if (last) outer else inner,
                                    bottomRight = if (last) outer else inner,
                                ),
                            )
                        }
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
        layout(width, height) {
            // Computed afresh in every placement pass: placement can re-run without a new
            // measure, and a list allocated outside this block then collected every segment
            // twice. Under the even-odd fill above, a duplicated segment cancels itself out, so
            // the cut-out became the whole bounds and erased the group (the viewer's
            // quick-action pill stayed invisible on first open until a scroll re-measured it).
            val rects = actionSegmentRects(segmentWidths, height, gap)
            var segment = 0
            placeables.forEachIndexed { index, placeable ->
                if (segmentWidths[index] == 0) {
                    placeable.placeRelative(0, 0)
                    return@forEachIndexed
                }
                val rect = rects[segment++]
                placeable.placeRelative(
                    rect.left.toInt() + (rect.width.toInt() - placeable.width) / 2,
                    (height - placeable.height) / 2,
                )
            }
            if (rects != segments) segments = rects
        }
    }
}

/**
 * One rect per non-zero width in [segmentWidths], laid out left to right with [gap] between
 * them, each [height] tall. Zero widths (popup anchors, hidden slots) take no segment.
 */
fun actionSegmentRects(segmentWidths: List<Int>, height: Int, gap: Int): List<Rect> {
    val rects = ArrayList<Rect>(segmentWidths.count { it > 0 })
    var x = 0
    segmentWidths.forEach { segmentWidth ->
        if (segmentWidth == 0) return@forEach
        rects += Rect(x.toFloat(), 0f, (x + segmentWidth).toFloat(), height.toFloat())
        x += segmentWidth + gap
    }
    return rects
}

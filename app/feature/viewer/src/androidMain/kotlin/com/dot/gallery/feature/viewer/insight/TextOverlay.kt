/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.insight.ImageQuad
import com.dot.gallery.core.insight.TextInsight
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.text_select_line
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Draws recognized lines over the zoomed photo, in screen space like [CodeOverlay].
 *
 * Without a [selection] the lines get a subtle primary tint. In selection mode the photo dims, the
 * selected lines brighten and two handles extend the selection by line in reading order. Each line is
 * exposed to TalkBack as its own node with a Select action, so the text reads as a list.
 */
@Composable
fun TextOverlay(
    insight: TextInsight,
    zoomable: ZoomableState,
    selection: TextSelection?,
    animationsEnabled: Boolean,
    translationY: () -> Float,
    onSelectionChange: (TextSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val reveal = remember(insight) { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(insight, animationsEnabled) {
        if (animationsEnabled) reveal.animateTo(1f, tween(durationMillis = 180))
    }
    val path = remember { Path() }
    val currentOnChange by rememberUpdatedState(onSelectionChange)
    val selectLabel = stringResource(Res.string.text_select_line)
    // Opened from a search result: the lines with the query's words stand out.
    val highlightTokens = remember(TextSearchHighlight.query) { TextSearchHighlight.tokens(TextSearchHighlight.query) }
    val matched = remember(insight, highlightTokens) {
        insight.lines.indices.filter { TextSearchHighlight.matches(insight.lines[it].text, highlightTokens) }.toSet()
    }

    // Draw-only: the handles are dragged through the page's own gesture pipeline
    // (ZoomablePagerImage), so pinch, pan and taps keep reaching the zoomable underneath.
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.translationY = translationY() }
            .semantics { isTraversalGroup = true }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val rect = zoomable.contentDisplayRectF
            if (rect.width <= 0f || zoomable.transform.rotation % 360f != 0f) return@Canvas
            val progress = reveal.value
            // The whole viewport dims, letterbox included, so only the lines stand out.
            if (selection != null) drawRect(Color.Black.copy(alpha = 0.45f))
            insight.lines.forEachIndexed { index, line ->
                path.reset()
                for (i in 0..3) {
                    val p = point(line.quad, i, rect)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                val color = when {
                    selection == null && index in matched -> primary.copy(alpha = 0.55f * progress)
                    selection == null -> primary.copy(alpha = 0.26f * progress)
                    index in selection.range -> primary.copy(alpha = 0.45f)
                    else -> Color.White.copy(alpha = 0.22f)
                }
                drawPath(path, color)
            }
            if (selection != null && insight.lines.isNotEmpty()) {
                val first = insight.lines[selection.range.first.coerceIn(insight.lines.indices)].quad
                val last = insight.lines[selection.range.last.coerceIn(insight.lines.indices)].quad
                listOf(true to first, false to last).forEach { (isStart, quad) ->
                    val top = if (isStart) point(quad, 0, rect) else point(quad, 1, rect)
                    val bottom = if (isStart) point(quad, 3, rect) else point(quad, 2, rect)
                    drawLine(primary, top, bottom, strokeWidth = 2.dp.toPx())
                    val knob = 9.dp.toPx()
                    drawCircle(primary, knob, Offset(bottom.x + if (isStart) -knob * 0.6f else knob * 0.6f, bottom.y + knob))
                }
            }
        }
        // One semantics node per line, in reading order and at the line's on-screen bounds, so
        // TalkBack reads the text as a list and its focus ring sits on the line.
        insight.lines.forEachIndexed { index, line ->
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val bounds = quadBounds(line.quad, zoomable.contentDisplayRectF)
                        val width = bounds.width.roundToInt().coerceAtLeast(1)
                        val height = bounds.height.roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) {
                            placeable.place(bounds.left.roundToInt(), bounds.top.roundToInt())
                        }
                    }
                    .semantics {
                        traversalIndex = index.toFloat()
                        contentDescription = line.text
                        customActions = listOf(
                            CustomAccessibilityAction(selectLabel) { currentOnChange(TextSelection.single(index)); true },
                        )
                    }
            )
        }
    }
}

private fun point(quad: ImageQuad, index: Int, rect: Rect): Offset =
    Offset(rect.left + quad.x(index) * rect.width, rect.top + quad.y(index) * rect.height)

private fun quadBounds(quad: ImageQuad, rect: Rect): Rect {
    if (rect.width <= 0f) return Rect.Zero
    val points = (0..3).map { point(quad, it, rect) }
    return Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
}

/**
 * Which selection handle a press at [position] lands on: true for the start handle, false for the
 * end one, null for neither. [slop] is the touch radius in pixels.
 */
fun TextInsight.selectionHandleAt(zoomable: ZoomableState, selection: TextSelection, position: Offset, slop: Float): Boolean? {
    val rect = zoomable.contentDisplayRectF
    if (rect.width <= 0f || lines.isEmpty()) return null
    val start = point(lines[selection.range.first.coerceIn(lines.indices)].quad, 3, rect)
    val end = point(lines[selection.range.last.coerceIn(lines.indices)].quad, 2, rect)
    val toStart = (position - start).getDistance()
    val toEnd = (position - end).getDistance()
    return when {
        toStart > slop && toEnd > slop -> null
        else -> toStart <= toEnd
    }
}

/** The line nearest to [position] in screen space, favouring lines on the same row. */
fun TextInsight.nearestLineAtScreen(zoomable: ZoomableState, position: Offset): Int {
    val rect = zoomable.contentDisplayRectF
    if (rect.width <= 0f) return 0
    val x = (position.x - rect.left) / rect.width
    val y = (position.y - rect.top) / rect.height
    return lines.indices.minByOrNull { i ->
        val q = lines[i].quad
        val dx = q.centerX - x
        val dy = q.centerY - y
        dx * dx + dy * dy * 4f
    } ?: 0
}

/** Line index at a tap in screen space, or -1. */
fun TextInsight.lineAtScreen(zoomable: ZoomableState, position: Offset): Int {
    val rect = zoomable.contentDisplayRectF
    if (rect.width <= 0f) return -1
    return lineAt((position.x - rect.left) / rect.width, (position.y - rect.top) / rect.height)
}

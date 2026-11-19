package com.dot.gallery.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The drawn image's rectangle in canvas-region dp coordinates — the
 * fit-centred box the image draws into inside the media insets at
 * zoom 1, relative to the region's top-left (the `imageOverlay`
 * scope's origin). [EditorCanvas] hands this to its `imageOverlay` so
 * overlays (crop frame, perspective guides) can sit exactly on the
 * image without re-deriving the fit maths.
 *
 * The `field*` rectangle is the media card the image is fitted into (and
 * clipped to), in the same coordinates — tools that frame a sub-region
 * (the crop tool's fixed, centred frame) fit against it. It defaults to
 * the image box for callers that have no separate card.
 */
@Immutable
internal data class EditorImageLayout(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
    val fieldLeft: Dp = left,
    val fieldTop: Dp = top,
    val fieldWidth: Dp = width,
    val fieldHeight: Dp = height,
    /**
     * The image box as it sits on screen under the current zoom and pan.
     * Tools that stay interactive while zoomed draw and hit-test against it.
     */
    val zoomedLeft: Dp = left,
    val zoomedTop: Dp = top,
    val zoomedWidth: Dp = width,
    val zoomedHeight: Dp = height,
    /**
     * The zoom transform's scale origin in the same viewing coordinates:
     * scaling by `s` moves [zoomedLeft] by `pivot * (1 - s)` plus the pan.
     */
    val zoomPivotLeft: Dp = 0.dp,
    val zoomPivotTop: Dp = 0.dp
)

/**
 * Places the content at ([left], [top]) in its parent at exactly
 * [width] x [height], even when that is larger than the parent. A plain
 * `size`/`requiredSize` is clamped or centred to the parent's bounds, which
 * shifts a zoomed image off its on-screen rect.
 */
internal fun Modifier.placedAt(left: Dp, top: Dp, width: Dp, height: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints.fixed(width.roundToPx(), height.roundToPx())
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(left.roundToPx(), top.roundToPx())
        }
    }

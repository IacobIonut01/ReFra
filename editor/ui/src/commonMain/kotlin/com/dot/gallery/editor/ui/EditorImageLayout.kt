package com.dot.gallery.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

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
    val zoomedHeight: Dp = height
)

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.mediaview.ViewerDismissBridge
import com.dot.gallery.feature_node.presentation.mediaview.ViewerDismissFlight
import com.dot.gallery.feature_node.presentation.mediaview.ViewerExitVisual
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.resize.Precision
import kotlin.math.roundToInt

/**
 * Content of the viewer's shared element. The shared element node must exist from the first
 * transition frame for sharedBounds to animate cell↔viewer bounds, but the real viewer media
 * (ZoomablePagerImage/VideoPlayer) is gated behind content-readiness and load state. This layer
 * renders the same low-res preview request the grid prefetches on tap (resize 600, LESS_PIXELS,
 * mediaVersion, mediaKeyPreviewEnc), so it resolves from Sketch's memory cache instantly and
 * morphs live inside the animated bounds until the full viewer fades in on top of it.
 */
@Composable
internal fun <T : Media> ViewerSharedElementThumbnail(
    media: T,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val mediaVersion = remember(media) { "${media.timestamp}:${media.size}" }
    AsyncImage(
        request = ComposableImageRequest(media.getUri().toString()) {
            resize(width = 600, height = 600, precision = Precision.LESS_PIXELS)
            crossfade(false)
            setExtra("realMimeType", media.mimeType)
            setExtra(key = "mediaVersion", value = mediaVersion)
            if (media.isEncrypted) {
                setExtra(key = "mediaKeyPreviewEnc", value = media.idLessKey)
            }
        },
        contentScale = contentScale,
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Committed swipe-dismiss flight: a root-level layer morphs the media thumbnail from the
 * gesture's release bounds to the live source-cell bounds while the overlay exits underneath.
 * It lives outside the viewer's AnimatedVisibility so the container's own fade/slide can't
 * affect it — the morph is driven by [ViewerDismissBridge.flight], not the shared-element
 * machinery (whose deferred handoff can't track a fullscreen element against a translating
 * mosaic cell). Host it as a sibling after the viewer's AnimatedVisibility inside the overlay
 * host's root Box.
 */
@Composable
internal fun ViewerDismissFlightLayer(bridge: ViewerDismissBridge) {
    bridge.flight?.let { flight ->
        val flightScale = remember(flight) { FlightContentScale(flight) }
        Box(
            modifier = Modifier.layout { measurable, _ ->
                val w = flight.bounds.width.roundToInt().coerceAtLeast(1)
                val h = flight.bounds.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(placeable.width, placeable.height) {
                    placeable.place(
                        flight.bounds.left.roundToInt(),
                        flight.bounds.top.roundToInt(),
                    )
                }
            }
        ) {
            ViewerSharedElementThumbnail(
                media = flight.media,
                modifier = Modifier.fillMaxSize(),
                contentScale = flightScale,
            )
        }
    }
}

/**
 * Non-interactive stand-in for the media viewer while its overlay exits: the scrim the
 * viewer stamped on [ViewerDismissBridge.exitVisual] (with its drag-faded alpha) plus the
 * media thumbnail when no return flight owns it. The interactive viewer is swapped for this
 * at the start of the exit so the fade keeps its visuals without any pointer-input nodes —
 * a fast re-tap falls through to the grid underneath instead of being swallowed by the
 * still-fading viewer. Host it inside the overlay's exit transition in place of the viewer.
 */
@Composable
internal fun ViewerDismissExitVisual(bridge: ViewerDismissBridge) {
    val visual: ViewerExitVisual = bridge.exitVisual ?: return
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(visual.scrim))
        if (bridge.flight == null) {
            visual.media?.let { media ->
                ViewerSharedElementThumbnail(
                    media = media,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/**
 * Interpolates between [ContentScale.Fit] (how the viewer letterboxes the media) and
 * [ContentScale.Crop] (how the grid cell fills its bounds) as the flight progresses, so the
 * morphing thumbnail matches the destination's rendering on landing: dismiss flights run
 * Fit→Crop, enter flights Crop→Fit.
 */
private class FlightContentScale(
    private val flight: ViewerDismissFlight,
) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        val fit = ContentScale.Fit.computeScaleFactor(srcSize, dstSize)
        val crop = ContentScale.Crop.computeScaleFactor(srcSize, dstSize)
        val p = flight.progress
        return if (flight.isEnter) {
            ScaleFactor(lerp(crop.scaleX, fit.scaleX, p), lerp(crop.scaleY, fit.scaleY, p))
        } else {
            ScaleFactor(lerp(fit.scaleX, crop.scaleX, p), lerp(fit.scaleY, crop.scaleY, p))
        }
    }
}

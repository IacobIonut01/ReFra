/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.animation.core.DeferredTransitionState
import androidx.compose.animation.core.ExperimentalDeferredTransitionApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * Bridges a viewer's swipe-down dismiss gesture to the overlay host.
 *
 * The drag offset itself is applied to a card box *inside* the viewer content (never to the
 * container or the gesture-handling node), so [transitionState] only drives the plain
 * enter/exit fades. On commit the return flight is driven manually: a root-level [flight]
 * layer morphs the media thumbnail from the release bounds to the source cell's live
 * [cellBounds] while the overlay fades out underneath — the shared element's bounds animation
 * is bypassed entirely for the dismiss direction (and on the enter direction, where a manual
 * cell->fullscreen flight replaces it), because its deferred handoff bookkeeping cannot track a
 * fullscreen element whose match partner lives behind a translating mosaic grid.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
@Stable
class ViewerDismissBridge {
    val transitionState = DeferredTransitionState(initialState = false)

    /**
     * Key of the shared element suppressed while a dismiss drag is armed. Both the source cell
     * and the viewer element drop out of the match, so the media renders in place inside the
     * offset container instead of chasing the cell through the bounds animation mid-gesture.
     */
    var suppressedElementKey by mutableStateOf<Any?>(null)
        set

    /** Root-space bounds of shared-element source cells/cards, keyed by element key. */
    val cellBounds = mutableStateMapOf<Any, Rect>()

    /**
     * Resting corner radius of shared-element source cells/cards, keyed by element key. Lets a
     * manual flight round its corners toward a rounded source (story cards) while staying square
     * toward a flat viewer, instead of snapping between the two.
     */
    val cellCornerRadii = mutableStateMapOf<Any, Dp>()

    /**
     * Lookahead size in px of shared-element source cells/cards, keyed by element key. Unlike
     * [cellBounds], this is the element's own layout size and stays stable while the shared
     * bounds animation is morphing its on-screen rect, so corner interpolation can anchor to it.
     */
    val cellCornerSizes = mutableStateMapOf<Any, IntSize>()

    /** Resting corner radius of the viewer/target side, keyed by element key. */
    val targetCornerRadii = mutableStateMapOf<Any, Dp>()

    /** Lookahead size in px of the viewer/target side, keyed by element key. */
    val targetCornerSizes = mutableStateMapOf<Any, IntSize>()

    /** The manual flight currently morphing media bounds — dismiss (release->cell) or the
     * overlay's enter (cell->fullscreen). */
    var flight by mutableStateOf<ViewerDismissFlight?>(null)
        set

    /** The non-interactive stand-in the host draws while the overlay exits — stamped every
     * composition by the live viewer so it is current at the moment the exit starts. */
    var exitVisual by mutableStateOf<ViewerExitVisual?>(null)
        set
}

/** The active overlay host's dismiss bridge, provided to source cells so they can suppress their
 * shared element while it is the armed drag's match target. Null outside overlay hosts. */
val LocalViewerDismissBridge = staticCompositionLocalOf<ViewerDismissBridge?> { null }

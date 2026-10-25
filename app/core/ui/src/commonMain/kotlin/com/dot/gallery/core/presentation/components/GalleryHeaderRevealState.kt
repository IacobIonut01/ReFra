/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import com.dot.gallery.ui.theme.GalleryChrome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Drives the expandable status area of the home header. The area follows the finger while the
 * header is dragged or the grid is pulled down past its top, then settles open or closed. Once
 * open, scrolling the grid up collapses it before the grid itself scrolls, like a collapsing
 * toolbar, so the photos are never more than one gesture away.
 */
@Stable
class GalleryHeaderRevealState(
    private val scope: CoroutineScope,
    initiallyExpanded: Boolean = false,
) {
    /** 0 = collapsed, 1 = fully revealed. Updated every frame while dragging or settling. */
    var fraction by mutableFloatStateOf(if (initiallyExpanded) 1f else 0f)
        private set

    /** Whether there is anything to reveal. Gestures pass through untouched while false. */
    var enabled by mutableStateOf(false)

    /** Settled target; drag in progress is reflected by [fraction] only. */
    var expanded by mutableStateOf(initiallyExpanded)
        private set

    /** Full height of the revealed content, reported by its layout. */
    var revealHeightPx by mutableFloatStateOf(0f)

    /** Distance the grid has to be pulled before an expansion is committed. */
    var pullThresholdPx by mutableFloatStateOf(0f)

    private var settleJob: Job? = null
    private var dragging = false

    fun expand() = settleTo(1f)

    fun collapse() = settleTo(0f)

    fun toggle() = if (expanded) collapse() else expand()

    /** Moves the reveal by [delta] px, positive opening. Returns the consumed distance. */
    fun dragBy(delta: Float): Float {
        if (!enabled) return 0f
        val height = revealHeightPx.takeIf { it > 0f } ?: pullThresholdPx.takeIf { it > 0f } ?: return 0f
        settleJob?.cancel()
        dragging = true
        val old = fraction
        val new = (old + delta / height).coerceIn(0f, 1f)
        fraction = new
        return (new - old) * height
    }

    /** Picks the closer end, or the direction of a decisive fling. */
    fun settle(velocity: Float = 0f) {
        dragging = false
        val target = when {
            velocity > FlingThreshold -> 1f
            velocity < -FlingThreshold -> 0f
            else -> if (fraction >= 0.5f) 1f else 0f
        }
        settleTo(target, velocity)
    }

    /** Snaps closed without animation, e.g. when the status disappears. */
    fun reset() {
        settleJob?.cancel()
        dragging = false
        expanded = false
        fraction = 0f
    }

    private fun settleTo(target: Float, velocity: Float = 0f) {
        if (!enabled && target > 0f) return
        expanded = target == 1f
        settleJob?.cancel()
        val height = revealHeightPx.takeIf { it > 0f } ?: 1f
        settleJob = scope.launch {
            animate(
                initialValue = fraction,
                targetValue = target,
                initialVelocity = velocity / height,
                animationSpec = RevealSpring,
            ) { value, _ -> fraction = value }
        }
    }

    /**
     * Placed between the grid and pull-to-refresh: a pull at the top of the grid opens the
     * status before it can start a refresh, and an upward scroll closes it first.
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y < 0f && fraction > 0f) {
                return Offset(0f, dragBy(available.y))
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y > 0f && fraction < 1f) {
                return Offset(0f, dragBy(available.y))
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!dragging) return Velocity.Zero
            val midway = fraction > 0f && fraction < 1f
            settle(available.y)
            return if (midway) available else Velocity.Zero
        }
    }

    private companion object {
        const val FlingThreshold = 1_200f
        val RevealSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    }
}

/** Provided by screens whose grid can pull the header's status open. */
val LocalGalleryHeaderReveal = staticCompositionLocalOf<GalleryHeaderRevealState?> { null }

@Composable
fun rememberGalleryHeaderRevealState(): GalleryHeaderRevealState {
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { GalleryChrome.HeaderPullThreshold.toPx() }
    return remember(scope) { GalleryHeaderRevealState(scope) }.also { it.pullThresholdPx = threshold }
}

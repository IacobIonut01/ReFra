/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * Whether a pointer x position falls inside the system-gesture edge strips:
 * the start strip is `[0, startPx)` and the end strip is `(widthPx - endPx, widthPx]`.
 * Zero or negative widths produce empty strips; positions outside the node never match.
 * Callers resolve RTL to absolute start/end before calling, so no layout direction is needed here.
 */
internal fun edgeGuardZone(
    x: Float,
    widthPx: Float,
    startPx: Float,
    endPx: Float,
): Boolean =
    (x >= 0f && x < startPx) || (x > widthPx - endPx && x <= widthPx)

/**
 * Whether accumulated drag deltas have become a horizontal pull worth blocking:
 * past touch slop and more horizontal than vertical. Equal deltas stay unblocked.
 */
internal fun shouldBlockEdgeDrag(dx: Float, dy: Float, touchSlop: Float): Boolean =
    abs(dx) >= touchSlop && abs(dx) > abs(dy)

/**
 * Consumes horizontal drags that begin inside the system-gesture edge strips so a
 * slow edge pull triggers system back without also paging the viewer. Runs on the
 * Initial pass from an ancestor of the pager, so once blocking starts every later
 * change arrives already consumed and the pager's scroll handling never sees it.
 *
 * Nothing is consumed until the drag crosses [shouldBlockEdgeDrag]'s threshold:
 * taps (side-tap navigation, chrome toggle), long presses and vertical drags
 * (swipe to dismiss) all pass through to the content untouched.
 */
internal fun Modifier.edgeHorizontalDragGuard(
    enabled: Boolean,
    startWidth: Dp,
    endWidth: Dp,
): Modifier = pointerInput(enabled, startWidth, endWidth) {
    if (!enabled) return@pointerInput
    val startPx = with(density) { startWidth.toPx() }
    val endPx = with(density) { endWidth.toPx() }
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        if (!edgeGuardZone(down.position.x, size.width.toFloat(), startPx, endPx)) {
            return@awaitEachGesture
        }
        var blocking = false
        while (true) {
            val changes = awaitPointerEvent(PointerEventPass.Initial).changes
            if (!blocking) {
                changes.firstOrNull { it.id == down.id }?.let { drag ->
                    if (
                        shouldBlockEdgeDrag(
                            dx = drag.position.x - down.position.x,
                            dy = drag.position.y - down.position.y,
                            touchSlop = viewConfiguration.touchSlop,
                        )
                    ) {
                        blocking = true
                    }
                }
            }
            if (blocking) changes.forEach { it.consume() }
            // changedToUp() reports false for the consumed up event, which would
            // leave this gesture — and every later one — stuck in the blocking loop.
            if (changes.all { !it.pressed }) break
        }
    }
}

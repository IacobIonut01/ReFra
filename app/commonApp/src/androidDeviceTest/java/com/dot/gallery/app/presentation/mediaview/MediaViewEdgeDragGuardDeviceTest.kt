/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for issue #1415: the old edge guards were sibling Spacers
 * that consumed every pointer event in the system-gesture strips, so taps there
 * never reached the pager (side-tap navigation died at the outermost edge).
 * [edgeHorizontalDragGuard] instead only blocks horizontal drags past touch slop,
 * letting taps, long presses and vertical drags through.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class MediaViewEdgeDragGuardDeviceTest {

    @get:Rule
    val rule = createComposeRule()

    private val taps = AtomicInteger(0)
    private val horizontalDragPx = AtomicReference(0.0)
    private val verticalDragPx = AtomicReference(0.0)

    private fun setContent(
        enabled: Boolean = true,
        stripWidth: Dp = 40.dp,
        oldStyleBlocker: Boolean = false,
    ) {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = enabled,
                    stripWidth = stripWidth,
                )
                if (oldStyleBlocker) {
                    // Faithful copy of the deleted mediaViewerBackGestureGuard spacers:
                    // full-height siblings on top that eat every Initial-pass event.
                    Box(Modifier.align(Alignment.CenterStart).consumeEveryInitialEvent(stripWidth))
                    Box(Modifier.align(Alignment.CenterEnd).consumeEveryInitialEvent(stripWidth))
                }
            }
        }
    }

    @Composable
    private fun Surface(modifier: Modifier, enabled: Boolean, stripWidth: Dp) {
        Box(
            modifier
                .size(400.dp, 300.dp)
                .edgeHorizontalDragGuard(enabled, stripWidth, stripWidth)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("child")
                    .pointerInput(Unit) {
                        detectTapGestures { taps.incrementAndGet() }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(onDrag = { _, amount ->
                            horizontalDragPx.updateAndGet { it + amount.x.toDouble() }
                            verticalDragPx.updateAndGet { it + amount.y.toDouble() }
                        })
                    }
            )
        }
    }

    private fun Modifier.consumeEveryInitialEvent(width: Dp): Modifier =
        fillMaxHeight()
            .width(width)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                            .changes.forEach { it.consume() }
                    }
                }
            }

    private fun px(dp: Float): Float = with(rule.density) { dp.dp.toPx() }

    private fun tapAt(xDp: Float, yDp: Float = 150f) {
        rule.onNodeWithTag("child").performTouchInput {
            click(position = Offset(px(xDp), px(yDp)))
        }
        rule.waitForIdle()
    }

    private fun swipeAt(
        fromX: Float,
        toX: Float,
        fromY: Float = 150f,
        toY: Float = 150f,
        durationMillis: Long = 300,
    ) {
        rule.onNodeWithTag("child").performTouchInput {
            swipe(
                start = Offset(px(fromX), px(fromY)),
                end = Offset(px(toX), px(toY)),
                durationMillis = durationMillis,
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun tapInsideEdgeStripReachesChild() {
        setContent()

        // 20.dp is inside the 40.dp start strip — the exact spot the old spacer ate.
        tapAt(xDp = 20f)
        assertEquals(1, taps.get())
    }

    @Test
    fun tapInsideEdgeStripIsEatenByOldStyleBlocker() {
        // Documents the pre-fix behaviour this test guards against: with the old
        // consume-everything sibling on top, the same strip tap never lands.
        setContent(enabled = false, oldStyleBlocker = true)

        tapAt(xDp = 20f)
        assertEquals(0, taps.get())
    }

    @Test
    fun horizontalSwipeFromEdgeStripDoesNotDrag() {
        setContent()

        swipeAt(fromX = 20f, toX = 300f, durationMillis = 400)

        // Only the sub-slop lead-in reaches the child before the guard blocks;
        // a real drag would accumulate most of the ~280.dp travel.
        assertTrue(
            "edge drag leaked to child: ${horizontalDragPx.get()}",
            horizontalDragPx.get() < 120.0,
        )
    }

    @Test
    fun horizontalSwipeFromCenterDrags() {
        setContent()

        swipeAt(fromX = 200f, toX = 360f, durationMillis = 300)

        assertTrue(
            "centre drag did not reach child: ${horizontalDragPx.get()}",
            horizontalDragPx.get() > 300.0,
        )
    }

    @Test
    fun verticalSwipeFromEdgeStripDrags() {
        setContent()

        swipeAt(fromX = 20f, toX = 20f, fromY = 220f, toY = 60f, durationMillis = 300)

        // Upward swipe accumulates negative dy; magnitude is what matters.
        assertTrue(
            "vertical drag did not reach child: ${verticalDragPx.get()}",
            kotlin.math.abs(verticalDragPx.get()) > 150.0,
        )
        assertTrue(
            "vertical drag leaked horizontally: ${horizontalDragPx.get()}",
            horizontalDragPx.get() < 120.0,
        )
    }

    @Test
    fun blockedEdgeDragDoesNotSwallowLaterGestures() {
        setContent()

        // A horizontal pull inside the strip blocks; the consumed up event must
        // still end the gesture or the guard keeps eating every later touch.
        swipeAt(fromX = 20f, toX = 300f, durationMillis = 400)
        tapAt(xDp = 20f)

        assertEquals(1, taps.get())
    }

    @Test
    fun disabledGuardLetsEdgeSwipeDrag() {
        setContent(enabled = false)

        swipeAt(fromX = 20f, toX = 300f, durationMillis = 400)

        assertTrue(
            "disabled guard still blocked: ${horizontalDragPx.get()}",
            horizontalDragPx.get() > 300.0,
        )
    }
}

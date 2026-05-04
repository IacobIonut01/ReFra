/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.previews

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Computes a smooth fade-in / fade-out alpha for gesture indicators.
 * Returns 0 during pause (progress ≈ 0) and 1 during the middle of the gesture.
 */
fun gestureAlpha(progress: Float): Float = when {
    progress < 0.05f -> 0f
    progress < 0.15f -> (progress - 0.05f) / 0.1f
    progress > 0.85f -> (1f - progress) / 0.15f
    else -> 1f
}

/**
 * Animated pinch gesture indicator (two circles moving toward/apart).
 * [progress] should animate from 0f (fingers apart) to 1f (fingers together).
 */
@Composable
fun PinchGestureOverlay(modifier: Modifier, progress: Float) {
    Canvas(modifier = modifier) {
        val alpha = gestureAlpha(progress)
        if (alpha == 0f) return@Canvas
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxDistance = size.width * 0.25f
        val currentDistance = maxDistance * (1f - progress)
        val fingerRadius = 12.dp.toPx()
        val fingerColor = Color.White.copy(alpha = 0.7f * alpha)

        drawCircle(fingerColor, fingerRadius, Offset(centerX - currentDistance, centerY - currentDistance))
        drawCircle(fingerColor, fingerRadius, Offset(centerX + currentDistance, centerY + currentDistance))
    }
}

/**
 * Animated swipe gesture indicator (single circle moving in [direction]).
 * [progress] should animate from 0f (start position) to 1f (end position).
 */
@Composable
fun SwipeGestureOverlay(
    modifier: Modifier,
    progress: Float,
    direction: SwipeDirection = SwipeDirection.LEFT
) {
    Canvas(modifier = modifier) {
        val alpha = gestureAlpha(progress)
        if (alpha == 0f) return@Canvas
        val startX = if (direction == SwipeDirection.LEFT) size.width * 0.7f else size.width * 0.3f
        val endX = if (direction == SwipeDirection.LEFT) size.width * 0.3f else size.width * 0.7f
        val startY = if (direction == SwipeDirection.UP) size.height * 0.7f else size.height * 0.3f
        val endY = if (direction == SwipeDirection.UP) size.height * 0.3f else size.height * 0.7f
        val fingerRadius = 12.dp.toPx()
        val fingerColor = Color.White.copy(alpha = 0.6f * alpha)

        when (direction) {
            SwipeDirection.LEFT, SwipeDirection.RIGHT -> {
                val currentX = lerp(startX, endX, progress)
                drawCircle(fingerColor, fingerRadius, Offset(currentX, size.height * 0.6f))
            }
            SwipeDirection.UP, SwipeDirection.DOWN -> {
                val currentY = lerp(startY, endY, progress)
                drawCircle(fingerColor, fingerRadius, Offset(size.width * 0.5f, currentY))
            }
        }
    }
}

/**
 * Animated tap gesture indicator with a press-and-release ripple effect.
 * [tapX] and [tapY] are normalized coordinates (0..1) within the composable.
 */
@Composable
fun TapGestureOverlay(
    modifier: Modifier,
    progress: Float,
    tapX: Float = 0.5f,
    tapY: Float = 0.4f
) {
    Canvas(modifier = modifier) {
        val alpha = gestureAlpha(progress)
        if (alpha == 0f) return@Canvas
        val x = size.width * tapX
        val y = size.height * tapY
        val baseRadius = 14.dp.toPx()

        // press down (0→0.4), hold (0.4→0.6), release+ripple (0.6→1)
        val radius: Float
        val circleAlpha: Float
        when {
            progress < 0.4f -> {
                val t = progress / 0.4f
                radius = baseRadius * (1f - t * 0.15f)
                circleAlpha = 0.7f * alpha
            }
            progress < 0.6f -> {
                radius = baseRadius * 0.85f
                circleAlpha = 0.7f * alpha
            }
            else -> {
                val t = (progress - 0.6f) / 0.4f
                radius = baseRadius * (0.85f + t * 0.4f)
                circleAlpha = 0.7f * alpha * (1f - t)
            }
        }
        drawCircle(Color.White.copy(alpha = circleAlpha), radius, Offset(x, y))
    }
}

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

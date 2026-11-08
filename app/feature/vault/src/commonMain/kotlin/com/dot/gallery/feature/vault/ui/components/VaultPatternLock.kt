/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import com.dot.gallery.core.util.rememberFeedbackManager

/**
 * A 3×3 pattern lock grid.
 * Reports the pattern as a string of node indices (0-8) when the user lifts their finger.
 * Example: "012345678" for a full Z-pattern.
 */
@Composable
fun VaultPatternLock(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onProgress: (connected: Int) -> Unit = {},
    onPatternComplete: (pattern: String) -> Unit
) {
    val feedbackManager = rememberFeedbackManager()
    val dotColor = MaterialTheme.colorScheme.outline
    val activeColor = MaterialTheme.colorScheme.primary
    val haloColor = MaterialTheme.colorScheme.primaryContainer
    val errorColor = MaterialTheme.colorScheme.error
    val errorHaloColor = MaterialTheme.colorScheme.errorContainer

    var selectedNodes by remember { mutableStateOf(listOf<Int>()) }
    LaunchedEffect(selectedNodes.size) { onProgress(selectedNodes.size) }
    var currentDragPos by remember { mutableStateOf<Offset?>(null) }
    var dragFingerX by remember { mutableFloatStateOf(0f) }
    var dragFingerY by remember { mutableFloatStateOf(0f) }

    val errorAnim = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            feedbackManager.vibrateStrong()
            errorAnim.snapTo(1f)
            errorAnim.animateTo(0f, tween(800))
            selectedNodes = emptyList()
        }
    }

    val lineColor = if (errorAnim.value > 0f) errorColor else activeColor
    val selectedDotColor = if (errorAnim.value > 0f) errorColor else activeColor
    val selectedHaloColor = if (errorAnim.value > 0f) errorHaloColor else haloColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            selectedNodes = emptyList()
                            currentDragPos = offset
                            dragFingerX = offset.x
                            dragFingerY = offset.y
                            val hitNode = hitTest(offset, size.width.toFloat(), size.height.toFloat())
                            if (hitNode != null) {
                                feedbackManager.vibrate()
                                selectedNodes = listOf(hitNode)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragFingerX = change.position.x
                            dragFingerY = change.position.y
                            currentDragPos = change.position
                            val hitNode = hitTest(
                                change.position,
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                            if (hitNode != null && hitNode !in selectedNodes) {
                                feedbackManager.vibrate()
                                selectedNodes = selectedNodes + hitNode
                            }
                        },
                        onDragEnd = {
                            currentDragPos = null
                            if (selectedNodes.size >= 4) {
                                feedbackManager.vibrateStrong()
                                onPatternComplete(selectedNodes.joinToString(""))
                            }
                            if (selectedNodes.size < 4) {
                                selectedNodes = emptyList()
                            }
                        },
                        onDragCancel = {
                            currentDragPos = null
                            selectedNodes = emptyList()
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            // Proportions of the 288 dp design pad: 12 dp dots, 16 dp picked dots on a 48 dp halo,
            // and a 6 dp trace.
            val dotRadius = w / 48f
            val activeDotRadius = w / 36f
            val haloRadius = w / 12f
            val traceWidth = w / 48f

            for (node in selectedNodes) {
                drawCircle(color = selectedHaloColor, radius = haloRadius, center = nodeCenter(node, w, h))
            }

            // Draw connection lines
            for (i in 0 until selectedNodes.size - 1) {
                val from = nodeCenter(selectedNodes[i], w, h)
                val to = nodeCenter(selectedNodes[i + 1], w, h)
                drawLine(
                    color = lineColor,
                    start = from,
                    end = to,
                    strokeWidth = traceWidth,
                    cap = StrokeCap.Round
                )
            }

            // Draw line from last node to finger
            if (selectedNodes.isNotEmpty() && currentDragPos != null) {
                val lastNode = nodeCenter(selectedNodes.last(), w, h)
                drawLine(
                    color = lineColor.copy(alpha = 0.5f),
                    start = lastNode,
                    end = Offset(dragFingerX, dragFingerY),
                    strokeWidth = traceWidth,
                    cap = StrokeCap.Round
                )
            }

            // Draw dots
            for (i in 0 until 9) {
                val center = nodeCenter(i, w, h)
                val isSelected = i in selectedNodes
                drawCircle(
                    color = if (isSelected) selectedDotColor else dotColor,
                    radius = if (isSelected) activeDotRadius else dotRadius,
                    center = center
                )
            }
        }
    }
}

private fun nodeCenter(index: Int, width: Float, height: Float): Offset {
    val col = index % 3
    val row = index / 3
    val cellW = width / 3f
    val cellH = height / 3f
    return Offset(cellW * col + cellW / 2f, cellH * row + cellH / 2f)
}

private fun hitTest(pos: Offset, width: Float, height: Float): Int? {
    val hitRadius = width / 6f
    for (i in 0 until 9) {
        val center = nodeCenter(i, width, height)
        val dist = (pos - center).getDistance()
        if (dist < hitRadius) return i
    }
    return null
}

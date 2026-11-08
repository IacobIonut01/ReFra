/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.dot.gallery.core.Constants.mosaicColumnsList


@Stable
class MosaicPinchZoomState(
    initialColumnsIndex: Int,
    val gridState: LazyGridState,
) {
    var currentColumnsIndex by mutableIntStateOf(initialColumnsIndex.coerceIn(0, mosaicColumnsList.lastIndex))
        private set

    val currentColumns: Int
        get() = mosaicColumnsList[currentColumnsIndex]

    var isZooming by mutableStateOf(false)
        set

    val scaleAnimatable = Animatable(1f)
    var accumulatedScale by mutableFloatStateOf(1f)

    /**
     * Progress towards a column change during a gesture.
     * Range: -1f..1f where:
     *   negative = progressing towards zoom-out (more columns)
     *   positive = progressing towards zoom-in (fewer columns)
     *   0 = no gesture / within dead zone
     */
    var zoomProgress by mutableFloatStateOf(0f)
        set

    fun updateColumnsIndex(index: Int) {
        currentColumnsIndex = index.coerceIn(0, mosaicColumnsList.lastIndex)
    }
}

@Composable
fun rememberMosaicPinchZoomState(
    initialColumnsIndex: Int = mosaicColumnsList.indexOf(4),
    gridState: LazyGridState = rememberLazyGridState(),
): MosaicPinchZoomState {
    return remember(gridState) {
        MosaicPinchZoomState(initialColumnsIndex, gridState)
    }
}

@Composable
fun MosaicPinchZoomLayout(
    state: MosaicPinchZoomState,
    modifier: Modifier = Modifier,
    indicatorTopPadding: Dp = 32.dp,
    /** Called when a pinch out goes past the widest grid; null keeps the widest grid as the end. */
    onPinchOutPastMin: (() -> Unit)? = null,
    content: @Composable (columns: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val pinchOutPastMin by androidx.compose.runtime.rememberUpdatedState(onPinchOutPastMin)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state, onPinchOutPastMin != null) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var zoom = 1f
                        var pastTouchSlop = false
                        var lastSnapZone = 0

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled && event.changes.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (!pastTouchSlop) {
                                    zoom *= zoomChange
                                    val centroidSize =
                                        event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = abs(1 - zoom) * centroidSize
                                    if (zoomMotion > touchSlop) {
                                        pastTouchSlop = true
                                        state.isZooming = true
                                        state.accumulatedScale = 1f
                                        state.zoomProgress = 0f
                                        lastSnapZone = 0
                                    }
                                }

                                if (pastTouchSlop && zoomChange != 1f) {
                                    state.accumulatedScale *= zoomChange
                                    scope.launch {
                                        state.scaleAnimatable.snapTo(state.accumulatedScale)
                                    }
                                    // Update progress: map scale to -1..1
                                    val s = state.accumulatedScale
                                    state.zoomProgress = when {
                                        s > 1f -> ((s - 1f) / 0.15f).coerceIn(0f, 1f)
                                        s < 1f -> -((1f - s) / 0.15f).coerceIn(0f, 1f)
                                        else -> 0f
                                    }

                                    val snapZone = when {
                                        s > 1.15f && state.currentColumnsIndex < mosaicColumnsList.lastIndex -> 1
                                        s < 0.85f && (state.currentColumnsIndex > 0 || onPinchOutPastMin != null) -> -1
                                        else -> 0
                                    }
                                    if (snapZone != lastSnapZone) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        lastSnapZone = snapZone
                                    }

                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })

                        if (pastTouchSlop && onPinchOutPastMin != null &&
                            state.accumulatedScale < 0.85f && state.currentColumnsIndex == 0
                        ) {
                            state.zoomProgress = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            pinchOutPastMin?.invoke()
                            scope.launch {
                                state.scaleAnimatable.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                                state.isZooming = false
                            }
                        } else if (pastTouchSlop) {
                            // Determine target column index based on accumulated scale
                            val scale = state.accumulatedScale
                            val targetIndex = if (scale > 1.15f) {
                                (state.currentColumnsIndex + 1).coerceAtMost(mosaicColumnsList.lastIndex)
                            } else if (scale < 0.85f) {
                                (state.currentColumnsIndex - 1).coerceAtLeast(0)
                            } else {
                                state.currentColumnsIndex
                            }

                            val columnsChanged = targetIndex != state.currentColumnsIndex
                            state.zoomProgress = 0f
                            if (columnsChanged) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                val oldCols = state.currentColumns
                                state.updateColumnsIndex(targetIndex)
                                val newCols = state.currentColumns
                                val compensationScale =
                                    oldCols.toFloat() / newCols.toFloat()
                                scope.launch {
                                    state.scaleAnimatable.snapTo(compensationScale)
                                    state.scaleAnimatable.animateTo(
                                        1f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    state.isZooming = false
                                }
                            } else {
                                scope.launch {
                                    state.scaleAnimatable.animateTo(
                                        1f,
                                        spring(stiffness = Spring.StiffnessMedium)
                                    )
                                    state.isZooming = false
                                }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    val scale = state.scaleAnimatable.value
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            content(state.currentColumns)
        }

        MosaicZoomIndicator(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = indicatorTopPadding)
        )
    }
}

private const val NUM_BARS = 5

@Composable
private fun MosaicZoomIndicator(
    state: MosaicPinchZoomState,
    modifier: Modifier = Modifier,
) {
    val isActive by remember {
        derivedStateOf { state.isZooming && state.zoomProgress != 0f }
    }

    AnimatedVisibility(
        visible = isActive,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            )
        ),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(
            targetScale = 0.8f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        )
    ) {
        val idx = state.currentColumnsIndex
        val currentCols = state.currentColumns
        // Left = zoom-in target (fewer cols), Right = zoom-out target (more cols)
        val leftCols = if (idx < mosaicColumnsList.lastIndex) mosaicColumnsList[idx + 1] else null
        val rightCols = if (idx > 0) mosaicColumnsList[idx - 1] else null

        val animatedProgress by animateFloatAsState(
            targetValue = state.zoomProgress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "zoomBarProgress"
        )

        // positive progress = zoom in (toward left / fewer cols)
        val leftFill = if (animatedProgress > 0f) animatedProgress else 0f
        // negative progress = zoom out (toward right / more cols)
        val rightFill = if (animatedProgress < 0f) -animatedProgress else 0f

        val activeColor = MaterialTheme.colorScheme.primary
        val inactiveColor = MaterialTheme.colorScheme.outlineVariant
        val numberColor = MaterialTheme.colorScheme.onSurface
        val dimColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Left number (zoom-in target)
            Text(
                text = leftCols?.toString() ?: "",
                color = if (leftCols != null) {
                    if (leftFill >= 1f) activeColor else numberColor
                } else dimColor,
                fontSize = 13.sp,
                fontWeight = if (leftFill >= 1f) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(14.dp)
            )

            // Left bars: fill from center (right) outward (left)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until NUM_BARS) {
                    val barFill = if (leftCols != null) {
                        (leftFill * NUM_BARS - (NUM_BARS - 1 - i)).coerceIn(0f, 1f)
                    } else 0f
                    ZoomBar(fill = barFill, activeColor = activeColor, inactiveColor = inactiveColor)
                }
            }

            // Center number (current)
            Text(
                text = "$currentCols",
                color = numberColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(14.dp)
            )

            // Right bars: fill from center (left) outward (right)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until NUM_BARS) {
                    val barFill = if (rightCols != null) {
                        (rightFill * NUM_BARS - i).coerceIn(0f, 1f)
                    } else 0f
                    ZoomBar(fill = barFill, activeColor = activeColor, inactiveColor = inactiveColor)
                }
            }

            // Right number (zoom-out target)
            Text(
                text = rightCols?.toString() ?: "",
                color = if (rightCols != null) {
                    if (rightFill >= 1f) activeColor else numberColor
                } else dimColor,
                fontSize = 13.sp,
                fontWeight = if (rightFill >= 1f) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(14.dp)
            )
        }
    }
}

@Composable
private fun ZoomBar(
    fill: Float,
    activeColor: Color,
    inactiveColor: Color,
) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(14.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(lerp(inactiveColor, activeColor, fill))
    )
}

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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


@Stable
class GridPinchZoomState(
    initialCellsIndex: Int,
    val cellsList: List<GridCells>,
    val gridState: LazyGridState,
) {
    /** Precomputed column counts for each entry in [cellsList]. */
    val columnCounts: List<Int> = cellsList.map { cells ->
        if (cells is GridCells.Fixed) -cells.hashCode() else 4
    }

    var currentCellsIndex by mutableIntStateOf(initialCellsIndex.coerceIn(0, cellsList.lastIndex))
        private set

    val currentCells: GridCells
        get() = cellsList[currentCellsIndex]

    var isZooming by mutableStateOf(false)
        set

    val scaleAnimatable = Animatable(1f)
    var accumulatedScale by mutableFloatStateOf(1f)

    var zoomProgress by mutableFloatStateOf(0f)
        set

    fun updateCellsIndex(index: Int) {
        currentCellsIndex = index.coerceIn(0, cellsList.lastIndex)
    }
}

@Composable
fun rememberGridPinchZoomState(
    cellsList: List<GridCells>,
    initialCellsIndex: Int = 0,
    gridState: LazyGridState = rememberLazyGridState(),
): GridPinchZoomState {
    return remember(gridState, cellsList) {
        GridPinchZoomState(initialCellsIndex, cellsList, gridState)
    }
}

@Composable
fun GridPinchZoomLayout(
    state: GridPinchZoomState,
    modifier: Modifier = Modifier,
    indicatorTopPadding: Dp = 32.dp,
    /** Called when a pinch out goes past the widest grid; null keeps the widest grid as the end. */
    onPinchOutPastMin: (() -> Unit)? = null,
    content: @Composable GridPinchZoomScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val pinchOutPastMin by androidx.compose.runtime.rememberUpdatedState(onPinchOutPastMin)
    val pinchScope = remember(state) {
        GridPinchZoomScopeImpl(state)
    }

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
                        // Track snap zone: -1 = zooming out (more cols), 0 = neutral, 1 = zooming in (fewer cols)
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
                                    val s = state.accumulatedScale
                                    state.zoomProgress = when {
                                        s > 1f -> ((s - 1f) / 0.15f).coerceIn(0f, 1f)
                                        s < 1f -> -((1f - s) / 0.15f).coerceIn(0f, 1f)
                                        else -> 0f
                                    }

                                    // Determine current snap zone and vibrate on transitions
                                    val snapZone = when {
                                        s > 1.15f && state.currentCellsIndex < state.cellsList.lastIndex -> 1
                                        s < 0.85f && (state.currentCellsIndex > 0 || onPinchOutPastMin != null) -> -1
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
                            state.accumulatedScale < 0.85f && state.currentCellsIndex == 0
                        ) {
                            state.zoomProgress = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            pinchOutPastMin?.invoke()
                            scope.launch {
                                state.scaleAnimatable.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                                state.isZooming = false
                            }
                        } else if (pastTouchSlop) {
                            val scale = state.accumulatedScale
                            val targetIndex = if (scale > 1.15f) {
                                (state.currentCellsIndex + 1).coerceAtMost(state.cellsList.lastIndex)
                            } else if (scale < 0.85f) {
                                (state.currentCellsIndex - 1).coerceAtLeast(0)
                            } else {
                                state.currentCellsIndex
                            }

                            val changed = targetIndex != state.currentCellsIndex
                            state.zoomProgress = 0f
                            if (changed) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                val oldCount = state.columnCounts[state.currentCellsIndex]
                                state.updateCellsIndex(targetIndex)
                                val newCount = state.columnCounts[state.currentCellsIndex]
                                val compensationScale =
                                    oldCount.toFloat() / newCount.toFloat()
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
            pinchScope.content()
        }

        GridZoomIndicator(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = indicatorTopPadding)
        )
    }
}

private class GridPinchZoomScopeImpl(
    private val state: GridPinchZoomState
) : GridPinchZoomScope {
    override val gridState: LazyGridState
        get() = state.gridState

    override val gridCells: GridCells
        get() = state.currentCells
}

private const val GRID_NUM_BARS = 5

@Composable
private fun GridZoomIndicator(
    state: GridPinchZoomState,
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
        val idx = state.currentCellsIndex
        val counts = state.columnCounts
        val currentCount = counts[idx]
        val leftCount = if (idx < counts.lastIndex) counts[idx + 1] else null
        val rightCount = if (idx > 0) counts[idx - 1] else null

        val animatedProgress by animateFloatAsState(
            targetValue = state.zoomProgress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "gridZoomBarProgress"
        )

        val leftFill = if (animatedProgress > 0f) animatedProgress else 0f
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
            Text(
                text = leftCount?.toString() ?: "",
                color = if (leftCount != null) {
                    if (leftFill >= 1f) activeColor else numberColor
                } else dimColor,
                fontSize = 13.sp,
                fontWeight = if (leftFill >= 1f) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(14.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until GRID_NUM_BARS) {
                    val barFill = if (leftCount != null) {
                        (leftFill * GRID_NUM_BARS - (GRID_NUM_BARS - 1 - i)).coerceIn(0f, 1f)
                    } else 0f
                    GridZoomBar(fill = barFill, activeColor = activeColor, inactiveColor = inactiveColor)
                }
            }

            Text(
                text = "$currentCount",
                color = numberColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(14.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until GRID_NUM_BARS) {
                    val barFill = if (rightCount != null) {
                        (rightFill * GRID_NUM_BARS - i).coerceIn(0f, 1f)
                    } else 0f
                    GridZoomBar(fill = barFill, activeColor = activeColor, inactiveColor = inactiveColor)
                }
            }

            Text(
                text = rightCount?.toString() ?: "",
                color = if (rightCount != null) {
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
private fun GridZoomBar(
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

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.previews

import android.provider.Settings.Global
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Reusable animation state machine for help preview composables.
 *
 * Cycles through [stepCount] steps, pausing between each,
 * with smooth progress transitions.
 *
 * Respects system reduce-motion setting — when enabled, stays on step 0.
 */
@Stable
class PreviewAnimationState(val stepCount: Int) {
    var currentStep by mutableIntStateOf(0)
        internal set
    var stepProgress by mutableFloatStateOf(0f)
        internal set
}

@Composable
fun rememberPreviewAnimation(
    stepCount: Int,
    stepDurationMs: Long = PreviewConstants.DEFAULT_STEP_DURATION_MS,
    pauseDurationMs: Long = PreviewConstants.DEFAULT_PAUSE_DURATION_MS,
): PreviewAnimationState {
    val state = remember(stepCount) { PreviewAnimationState(stepCount) }

    val context = LocalContext.current
    val reduceMotion = remember {
        Global.getFloat(
            context.contentResolver,
            Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    if (!reduceMotion && stepCount > 1) {
        LaunchedEffect(stepCount) {
            while (true) {
                delay(pauseDurationMs)
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = stepDurationMs.toInt(),
                        easing = EaseInOutCubic
                    )
                ) { value, _ ->
                    state.stepProgress = value
                }
                state.currentStep = (state.currentStep + 1) % stepCount
                state.stepProgress = 0f
            }
        }
    }

    return state
}

object PreviewConstants {
    const val DEFAULT_STEP_DURATION_MS = 1200L
    const val DEFAULT_PAUSE_DURATION_MS = 1500L
    val DEFAULT_EASING = EaseInOutCubic
}

/**
 * A layout wrapper that gives its child extra space (so lazy lists/grids
 * compose more items) and then translates the rendered content based on
 * [scrollProgress] (0 → 1). The parent must have `clipToBounds()` to
 * hide the overflow, creating a real scroll effect.
 *
 * @param scrollProgress Normalized progress (0 = top, 1 = scrolled by [scrollDistanceDp]).
 * @param scrollDistanceDp How far the content scrolls.
 * @param horizontal If true, scrolls horizontally instead of vertically.
 */
@Composable
fun AutoScrollBox(
    scrollProgress: Float,
    modifier: Modifier = Modifier,
    scrollDistanceDp: Dp = 120.dp,
    horizontal: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val extraPx = with(density) { scrollDistanceDp.roundToPx() }
    val offsetPx = -extraPx * scrollProgress

    Layout(
        content = content,
        modifier = modifier.graphicsLayer {
            if (horizontal) translationX = offsetPx else translationY = offsetPx
        }
    ) { measurables, constraints ->
        val childConstraints = if (horizontal) {
            constraints.copy(maxWidth = constraints.maxWidth + extraPx)
        } else {
            constraints.copy(maxHeight = constraints.maxHeight + extraPx)
        }
        val placeables = measurables.map { it.measure(childConstraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

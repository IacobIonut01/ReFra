/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.scrollbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Pure layout for the vertical scrollbar: a thumb, an optional indicator anchored to the
 * thumb centre, and a full-height draggable hit-area. All values are normalised [0f, 1f].
 *
 * [thumbSizeNormalized] and [thumbOffsetNormalized] are passed as lambdas and read inside the
 * measure/placement phase on purpose: they change every scroll frame, so reading them here
 * (instead of at composition scope) re-runs only layout, never recomposition.
 */
@Composable
internal fun VerticalScrollbarLayout(
    thumbSizeNormalized: () -> Float,
    thumbOffsetNormalized: () -> Float,
    thumbIsInAction: Boolean,
    thumbIsSelected: Boolean,
    settings: ScrollbarSettings,
    draggableModifier: Modifier,
    indicator: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val settingsUpdated by rememberUpdatedState(settings)
    val thumbIsInActionUpdated by rememberUpdatedState(thumbIsInAction)

    val isInActionSelectable = remember { mutableStateOf(thumbIsInAction) }
    LaunchedEffect(thumbIsInAction) {
        if (thumbIsInAction) {
            isInActionSelectable.value = true
        } else {
            delay(settingsUpdated.durationAnimationMillis.toLong() + settingsUpdated.hideDelayMillis.toLong())
            isInActionSelectable.value = false
        }
    }

    val activeDraggable by remember {
        derivedStateOf {
            when (settingsUpdated.selectionActionable) {
                ScrollbarSelectionActionable.Always -> true
                ScrollbarSelectionActionable.WhenVisible -> isInActionSelectable.value
            }
        }
    }

    val thumbColor by animateColorAsState(
        targetValue = if (thumbIsSelected) settingsUpdated.thumbSelectedColor else settingsUpdated.thumbUnselectedColor,
        animationSpec = tween(durationMillis = 50),
        label = "scrollbarThumbColor"
    )

    val durationMillis by remember {
        derivedStateOf {
            settingsUpdated.durationAnimationMillis / if (thumbIsInActionUpdated) 4 else 1
        }
    }

    val hideAlpha by animateFloatAsState(
        targetValue = if (thumbIsInActionUpdated) 1f else 0f,
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = if (thumbIsInActionUpdated) 0 else settingsUpdated.hideDelayMillis,
            easing = settingsUpdated.hideEasingAnimation
        ),
        label = "scrollbarAlpha"
    )

    val hideDisplacement by animateDpAsState(
        targetValue = if (thumbIsInActionUpdated) 0.dp else settingsUpdated.hideDisplacement,
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = if (thumbIsInActionUpdated) 0 else settingsUpdated.hideDelayMillis,
            easing = settingsUpdated.hideEasingAnimation
        ),
        label = "scrollbarDisplacement"
    )

    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(
                        start = if (settings.side == ScrollbarLayoutSide.Start) settings.scrollbarPadding else 0.dp,
                        end = if (settings.side == ScrollbarLayoutSide.End) settings.scrollbarPadding else 0.dp,
                    )
                    .graphicsLayer { alpha = hideAlpha }
                    .clip(settings.thumbShape)
                    .width(settings.thumbThickness)
                    .background(thumbColor)
            )
            when (indicator) {
                null -> Box(Modifier)
                else -> Box(Modifier.graphicsLayer { alpha = hideAlpha }) { indicator() }
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(settings.scrollbarPadding * 2 + settings.thumbThickness)
                    .run { if (activeDraggable) then(draggableModifier) else this }
            )
        },
        measurePolicy = { measurables, constraints ->
            // Read thumb size in the measure phase (state read -> re-measure, not recompose).
            val sizeFraction = thumbSizeNormalized().coerceIn(0f, 1f)
            val thumbHeight = (constraints.maxHeight * sizeFraction).toInt().coerceAtLeast(0)
            val placeableThumb = measurables[0].measure(
                constraints.copy(minHeight = thumbHeight, maxHeight = thumbHeight)
            )
            val placeableIndicator = measurables[1].measure(constraints)
            val placeableScrollbarArea = measurables[2].measure(constraints)
            layout(constraints.minWidth, constraints.minHeight) {
                // Read thumb offset in the placement phase (state read -> re-place, not recompose).
                val offset = (constraints.maxHeight.toFloat() * thumbOffsetNormalized()).toInt()
                val hideDisplacementPx = when (settings.side) {
                    ScrollbarLayoutSide.Start -> -hideDisplacement.roundToPx()
                    ScrollbarLayoutSide.End -> +hideDisplacement.roundToPx()
                }
                val indicatorOffsetPx = when (settings.side) {
                    ScrollbarLayoutSide.Start -> -settings.indicatorOffset.roundToPx()
                    ScrollbarLayoutSide.End -> +settings.indicatorOffset.roundToPx()
                }

                placeableThumb.placeRelative(
                    x = when (settings.side) {
                        ScrollbarLayoutSide.Start -> 0
                        ScrollbarLayoutSide.End -> constraints.maxWidth - placeableThumb.width
                    } + hideDisplacementPx,
                    y = offset
                )
                placeableIndicator.placeRelative(
                    x = when (settings.side) {
                        ScrollbarLayoutSide.Start -> placeableThumb.width
                        ScrollbarLayoutSide.End -> constraints.maxWidth - placeableThumb.width - placeableIndicator.width
                    } + indicatorOffsetPx + hideDisplacementPx,
                    y = offset + placeableThumb.height / 2 - placeableIndicator.height / 2
                )
                placeableScrollbarArea.placeRelative(
                    x = when (settings.side) {
                        ScrollbarLayoutSide.Start -> 0
                        ScrollbarLayoutSide.End -> constraints.maxWidth - placeableScrollbarArea.width
                    },
                    y = 0
                )
            }
        }
    )
}

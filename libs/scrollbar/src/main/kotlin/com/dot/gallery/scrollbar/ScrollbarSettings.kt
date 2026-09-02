/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 *
 * Focused, performance-oriented re-implementation of the vertical LazyGrid
 * scrollbar originally provided by `LazyColumnScrollbar`
 * (https://github.com/nanihadesuka/LazyColumnScrollbar), trimmed to the single
 * code path the Gallery timeline uses and modernised for the latest Compose APIs.
 */
package com.dot.gallery.scrollbar

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** On which side of the viewport the scrollbar thumb is drawn. */
enum class ScrollbarLayoutSide { Start, End }

/** Where dragging is allowed to begin from. */
enum class ScrollbarSelectionMode {
    /** Dragging can start anywhere on the scrollbar track or the thumb. */
    Full,

    /** Dragging can only start on the thumb itself. */
    Thumb,

    /** Dragging is disabled entirely. */
    Disabled
}

/** Whether the drag area is actionable always, or only while the thumb is visible. */
enum class ScrollbarSelectionActionable { Always, WhenVisible }

/**
 * Visual + behavioural configuration for [LazyVerticalGridScrollbar].
 *
 * @param thumbMinLength Thumb minimum length proportional to the total scrollbar length
 * (e.g. 0.1 -> 10% of the track). Mirrors the original library semantics.
 */
@Immutable
data class ScrollbarSettings(
    val enabled: Boolean = true,
    val side: ScrollbarLayoutSide = ScrollbarLayoutSide.End,
    val alwaysShowScrollbar: Boolean = false,
    val scrollbarPadding: Dp = 8.dp,
    val indicatorOffset: Dp = 0.dp,
    val thumbThickness: Dp = 6.dp,
    val thumbShape: Shape = CircleShape,
    val thumbMinLength: Float = 0.1f,
    val thumbMaxLength: Float = 1.0f,
    val thumbUnselectedColor: Color = Color(0xFF2A59B6),
    val thumbSelectedColor: Color = Color(0xFF5281CA),
    val selectionMode: ScrollbarSelectionMode = ScrollbarSelectionMode.Thumb,
    val selectionActionable: ScrollbarSelectionActionable = ScrollbarSelectionActionable.Always,
    val hideDelayMillis: Int = 400,
    val hideDisplacement: Dp = 14.dp,
    val hideEasingAnimation: Easing = FastOutSlowInEasing,
    val durationAnimationMillis: Int = 500,
) {
    init {
        require(thumbMinLength <= thumbMaxLength) {
            "thumbMinLength ($thumbMinLength) must be less or equal to thumbMaxLength ($thumbMaxLength)"
        }
    }

    companion object {
        @Stable
        val Default = ScrollbarSettings()
    }
}

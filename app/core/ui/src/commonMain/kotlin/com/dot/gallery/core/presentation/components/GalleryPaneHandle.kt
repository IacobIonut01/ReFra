/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.gallery_pane_reset
import com.dot.gallery.core.ui.generated.resources.gallery_pane_resize
import com.dot.gallery.core.ui.generated.resources.gallery_pane_resize_state
import com.dot.gallery.ui.theme.GalleryChrome
import org.jetbrains.compose.resources.stringResource

const val GalleryPaneHandleTag = "Gallery.PaneHandle"

/**
 * Gutter handle shared by every resizable split: a short pill that widens while dragged, with
 * a haptic tick on grab, double-tap (or the accessibility action) to reset, and progress
 * semantics reporting the leading pane's share of the width.
 */
@Composable
fun GalleryPaneHandle(
    modifier: Modifier,
    fractionPercent: () -> Int,
    onDrag: Density.(Float) -> Unit,
    onDragStopped: () -> Unit,
    onSetFraction: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val handleWidth by animateDpAsState(
        if (dragged) GalleryChrome.PaneHandleActiveWidth else GalleryChrome.PaneHandleWidth,
        label = "paneHandleWidth",
    )
    val handleHeight by animateDpAsState(
        if (dragged) GalleryChrome.PaneHandleHeight + 24.dp else GalleryChrome.PaneHandleHeight,
        label = "paneHandleHeight",
    )
    val handleColor by animateColorAsState(
        if (dragged) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        label = "paneHandleColor",
    )
    LaunchedEffect(dragged) {
        if (dragged) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val description = stringResource(Res.string.gallery_pane_resize)
    val resetLabel = stringResource(Res.string.gallery_pane_reset)
    val percent = fractionPercent()
    val stateText = stringResource(Res.string.gallery_pane_resize_state, percent)
    Box(
        modifier = modifier
            .testTag(GalleryPaneHandleTag)
            .semantics {
                contentDescription = description
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
                setProgress { requested ->
                    onSetFraction(requested.coerceIn(0f, 1f))
                    true
                }
                customActions = listOf(CustomAccessibilityAction(resetLabel) { onReset(); true })
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onReset()
                })
            }
            .draggable(
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                state = rememberDraggableState { delta -> density.onDrag(delta) },
                onDragStopped = { onDragStopped() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = handleWidth, height = handleHeight)
                .background(handleColor, CircleShape),
        )
    }
}

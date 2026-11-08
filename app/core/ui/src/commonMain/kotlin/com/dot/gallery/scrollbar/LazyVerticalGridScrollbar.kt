/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.scrollbar

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.foundation.gestures.scrollBy
import kotlin.math.abs
import kotlin.math.floor

/** Targets further than this many screens away jump by index instead of scrolling. */
private const val FarJumpViewports = 3f

/**
 * Draws a fast-scroll scrollbar over a vertical [androidx.compose.foundation.lazy.grid.LazyVerticalGrid].
 *
 * Focused, performance-oriented replacement for the upstream `LazyVerticalGridScrollbar`
 * with three concrete improvements for large media grids:
 *  1. The thumb tracks the finger 1:1 while dragging (no layout-induced lag).
 *  2. The indicator label reflects the *target* index immediately during a drag.
 *  3. Scroll commits are coalesced via `collectLatest`, so a fast flick of the thumb
 *     no longer queues a `scrollToItem` per pointer event.
 */
@Composable
fun LazyVerticalGridScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    settings: ScrollbarSettings = ScrollbarSettings.Default,
    indicatorContent: (@Composable (index: Int, isThumbSelected: Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!settings.enabled) {
        content()
    } else Box(modifier) {
        content()
        InternalLazyVerticalGridScrollbar(
            state = state,
            settings = settings,
            indicatorContent = indicatorContent,
        )
    }
}

/**
 * Use this variation to place the scrollbar independently of the list (e.g. as a sibling
 * overlay) rather than wrapping the content.
 */
@Composable
fun InternalLazyVerticalGridScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    settings: ScrollbarSettings = ScrollbarSettings.Default,
    snapIndices: IntArray = IntArray(0),
    snapScrollOffset: Int = 0,
    onSnap: () -> Unit = {},
    onDraggingChanged: (Boolean) -> Unit = {},
    indicatorContent: (@Composable (index: Int, isThumbSelected: Boolean) -> Unit)? = null,
) {
    val controller = rememberGridScrollbarController(
        state = state,
        thumbMinLength = settings.thumbMinLength,
        thumbMaxLength = settings.thumbMaxLength,
        alwaysShowScrollBar = settings.alwaysShowScrollbar,
        selectionMode = settings.selectionMode,
        snapIndices = snapIndices,
        snapScrollOffset = snapScrollOffset,
        onSnap = onSnap,
    )

    // PERF: a single collector commits only the latest target. collectLatest cancels any
    // superseded (and already obsolete) scroll, eliminating the per-event coroutine storm.
    LaunchedEffect(controller, state) {
        snapshotFlow { controller.scrollTarget.value }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { target ->
                // Close targets are reached with a relative pixel scroll measured from the real
                // layout, so a drag moves the grid steadily even where headers and mosaic tiles
                // make rows uneven. Only a far jump (a fast flick of the thumb) goes by index.
                val delta = target.progress - controller.currentProgress()
                val span = controller.visibleSpan()
                val viewport = state.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
                if (span > 0f && viewport > 0 && abs(delta) <= span * FarJumpViewports) {
                    state.scrollBy(delta * viewport / span)
                } else {
                    state.scrollToItem(floor(target.progress).toInt().coerceAtLeast(0))
                }
            }
    }

    // thumbIsInAction / isSelected flip rarely (scroll start-stop, drag select), so reading them
    // at composition scope is fine. The continuously-changing size/offset are deferred to the
    // layout phase via lambdas to avoid recomposing every scroll frame.
    val thumbIsInAction by controller.thumbIsInAction
    val isSelected by controller.isSelected

    // Surface thumb-drag state so callers can react (e.g. pause image loading only while
    // the user is fast-scrolling via the scrollbar, not on every fling).
    LaunchedEffect(isSelected) {
        onDraggingChanged(isSelected)
    }

    BoxWithConstraints(modifier) {
        val maxLengthPixels = constraints.maxHeight.toFloat()

        VerticalScrollbarLayout(
            thumbSizeNormalized = { controller.thumbSizeNormalized.value },
            thumbOffsetNormalized = { controller.thumbOffsetNormalized.value },
            thumbIsInAction = thumbIsInAction,
            thumbIsSelected = isSelected,
            settings = settings,
            indicator = indicatorContent?.let {
                { it(controller.indicatorValue(), isSelected) }
            },
            draggableModifier = Modifier.draggable(
                state = rememberDraggableState { deltaPixels ->
                    controller.onDraggableState(deltaPixels, maxLengthPixels)
                },
                orientation = Orientation.Vertical,
                enabled = settings.selectionMode != ScrollbarSelectionMode.Disabled,
                startDragImmediately = true,
                onDragStarted = { offset ->
                    controller.onDragStarted(offset.y, maxLengthPixels)
                },
                onDragStopped = { controller.onDragStopped() }
            )
        )
    }
}

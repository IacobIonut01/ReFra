/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow


/**
 * Broadcasts "scroll to top" requests keyed by navigation route.
 *
 * Re-tapping the currently selected bottom-navigation item (Timeline, Albums,
 * Library) emits the current route here; the matching screen collects the event
 * and scrolls its list/grid back to the top.
 */
@Stable
class ScrollToTopController {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun requestScrollToTop(route: String) {
        _events.tryEmit(route)
    }
}

val LocalScrollToTop = staticCompositionLocalOf { ScrollToTopController() }

/**
 * Collects [ScrollToTopController] events for [route] and runs [onScrollToTop]
 * whenever a request for that route arrives.
 */
@Composable
fun ScrollToTopHandler(route: String, onScrollToTop: suspend () -> Unit) {
    val controller = LocalScrollToTop.current
    LaunchedEffect(controller, route) {
        controller.events.collect { requested ->
            if (requested == route) onScrollToTop()
        }
    }
}

/**
 * Scrolls to the very top. Jumps instantly when far away (a long smooth scroll
 * through hundreds of items feels sluggish) and animates when already close.
 */
suspend fun LazyGridState.animateOrJumpToTop() {
    if (firstVisibleItemIndex > 15) scrollToItem(0) else animateScrollToItem(0)
}

suspend fun LazyListState.animateOrJumpToTop() {
    if (firstVisibleItemIndex > 15) scrollToItem(0) else animateScrollToItem(0)
}

/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.dot.gallery.core.presentation.components.GalleryHeaderRevealState
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryHeaderRevealStateTest {
    private fun state(enabled: Boolean = true) = GalleryHeaderRevealState(TestScope()).apply {
        this.enabled = enabled
        revealHeightPx = 200f
    }

    @Test
    fun nothingToRevealLetsEveryGesturePassThrough() {
        val reveal = state(enabled = false)
        val consumed = reveal.nestedScrollConnection.onPostScroll(
            Offset.Zero, Offset(0f, 80f), NestedScrollSource.UserInput,
        )
        assertEquals(Offset.Zero, consumed)
        assertEquals(0f, reveal.fraction)
    }

    @Test
    fun pullPastTheGridTopFollowsTheFinger() {
        val reveal = state()
        val consumed = reveal.nestedScrollConnection.onPostScroll(
            Offset.Zero, Offset(0f, 50f), NestedScrollSource.UserInput,
        )
        assertEquals(Offset(0f, 50f), consumed)
        assertEquals(0.25f, reveal.fraction)
    }

    @Test
    fun flingOverscrollNeverOpensTheStatus() {
        val reveal = state()
        val consumed = reveal.nestedScrollConnection.onPostScroll(
            Offset.Zero, Offset(0f, 50f), NestedScrollSource.SideEffect,
        )
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun scrollingUpClosesAnOpenStatusBeforeTheGridScrolls() {
        val reveal = state()
        reveal.dragBy(200f)
        val consumed = reveal.nestedScrollConnection.onPreScroll(
            Offset(0f, -120f), NestedScrollSource.UserInput,
        )
        assertEquals(-120f, consumed.y, 0.001f)
        assertEquals(0.4f, reveal.fraction, 0.0001f)
        // Only the remaining revealed height is consumed; the rest scrolls the grid.
        val rest = reveal.nestedScrollConnection.onPreScroll(
            Offset(0f, -200f), NestedScrollSource.UserInput,
        )
        assertEquals(-80f, rest.y, 0.001f)
        assertEquals(0f, reveal.fraction)
    }

    @Test
    fun closedStatusDoesNotInterceptUpwardScrolling() {
        val reveal = state()
        val consumed = reveal.nestedScrollConnection.onPreScroll(
            Offset(0f, -40f), NestedScrollSource.UserInput,
        )
        assertEquals(Offset.Zero, consumed)
    }
}

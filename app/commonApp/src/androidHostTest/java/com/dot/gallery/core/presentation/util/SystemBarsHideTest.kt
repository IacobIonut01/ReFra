/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import androidx.core.graphics.Insets
import org.junit.Assert.assertEquals
import org.junit.Test


class SystemBarsHideTest {

    @Test
    fun hideDurationIsFastWhenAnimatorsEnabled() {
        assertEquals(SYSTEM_BARS_HIDE_DURATION_MS, systemBarsHideDurationMs(animatorsEnabled = true))
        assertEquals(120L, SYSTEM_BARS_HIDE_DURATION_MS)
    }

    @Test
    fun hideDurationIsZeroWhenAnimatorsDisabled() {
        assertEquals(0L, systemBarsHideDurationMs(animatorsEnabled = false))
    }

    @Test
    fun lerpInsetsReturnsEndpoints() {
        val from = Insets.of(4, 96, 8, 48)
        val to = Insets.of(0, 0, 0, 0)
        assertEquals(from, lerpInsets(from, to, 0f))
        assertEquals(to, lerpInsets(from, to, 1f))
    }

    @Test
    fun lerpInsetsInterpolatesEachSide() {
        val result = lerpInsets(
            from = Insets.of(0, 96, 0, 48),
            to = Insets.of(0, 0, 0, 0),
            fraction = 0.5f,
        )
        assertEquals(Insets.of(0, 48, 0, 24), result)
    }

    @Test
    fun lerpInsetsRoundsToInt() {
        val result = lerpInsets(
            from = Insets.of(10, 0, 0, 0),
            to = Insets.of(0, 0, 0, 0),
            fraction = 0.3f,
        )
        assertEquals(7, result.left)
    }

    @Test
    fun lerpInsetsClampsFraction() {
        val from = Insets.of(0, 96, 0, 48)
        val to = Insets.of(0, 0, 0, 0)
        assertEquals(from, lerpInsets(from, to, -1f))
        assertEquals(to, lerpInsets(from, to, 2f))
    }
}

/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.RailMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class RailMetricsTest {
    private val regular = RailMetrics(
        itemHeight = 64.dp, itemGap = 8.dp, pillPadding = 16.dp, circleSize = 64.dp, sectionGap = 16.dp,
    )

    @Test
    fun primaryScreensBudgetForDestinationsAndSearch() {
        // 16 + 3 * 64 + 2 * 8 + 16 pill, then a 16 dp gap and the 64 dp search circle.
        assertEquals(320.dp, regular.requiredHeight(itemCount = 3, hasBack = false))
    }

    @Test
    fun secondaryScreensOnlyNeedBackAndSearch() {
        assertEquals(160.dp, regular.requiredHeight(itemCount = 3, hasBack = true))
    }
}

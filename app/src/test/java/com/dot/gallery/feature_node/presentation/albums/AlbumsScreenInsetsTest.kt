/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albums

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumsScreenInsetsTest {

    @Test
    fun horizontalInsetsPreserveBothEdgesAndExcludeVerticalInsets() {
        LayoutDirection.entries.forEach { layoutDirection ->
            val insets = albumScreenHorizontalInsets(
                paddingValues = PaddingValues.Absolute(
                    left = 24.dp,
                    top = 10.dp,
                    right = 16.dp,
                    bottom = 12.dp,
                ),
                layoutDirection = layoutDirection,
            )
            val expectedStart = if (layoutDirection == LayoutDirection.Ltr) 24.dp else 16.dp
            val expectedEnd = if (layoutDirection == LayoutDirection.Ltr) 16.dp else 24.dp

            assertEquals(expectedStart, insets.calculateStartPadding(layoutDirection))
            assertEquals(expectedEnd, insets.calculateEndPadding(layoutDirection))
            assertEquals(0.dp, insets.calculateTopPadding())
            assertEquals(0.dp, insets.calculateBottomPadding())
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Stable
@NonRestartableComposable
@Composable
fun StickyHeaderGrid(
    modifier: Modifier = Modifier,
    showSearchBar: Boolean,
    headerOffset: Int,
    stickyHeader: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val toolbarOffset = with(LocalDensity.current) {
        val statusBarHeightPx = WindowInsets.statusBars.getTop(this)
        return@with remember(LocalDensity.current) {
            if (showSearchBar) 0 else 64.dp.roundToPx() + statusBarHeightPx
        }
    }
    val offsetAnimation by animateIntOffsetAsState(
        remember(headerOffset, toolbarOffset) {
            IntOffset(
                x = 0,
                y = headerOffset + toolbarOffset
            )
        }, label = "offsetAnimation"
    )

    val alphaAnimation by animateFloatAsState(
        targetValue = remember(offsetAnimation) { if (offsetAnimation.y < -100) 0f else 1f },
        label = "alphaAnimation",
        animationSpec = tween(100, 10),
    )

    Box(modifier = modifier) {
        content()
        Box(
            modifier = Modifier
                .alpha(alphaAnimation)
                .offset { offsetAnimation }
        ) {
            stickyHeader()
        }
    }
}
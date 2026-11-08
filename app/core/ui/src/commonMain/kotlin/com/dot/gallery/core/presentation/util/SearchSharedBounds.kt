/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val SearchSharedBoundsKey = "search_screen_bounds"

/** The floating search action and search destination share one container transition. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedSearchBounds(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier = with(sharedTransitionScope) {
    sharedBounds(
        sharedContentState = rememberSharedContentState(SearchSharedBoundsKey),
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

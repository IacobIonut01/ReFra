/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Name size and side padding of the label below a cover. */
data class AlbumBelowLabelMetrics(val nameSize: TextUnit, val horizontalPadding: Dp)

/**
 * Narrow cells shrink the name from 16 sp to 13 sp and the side padding from 16 dp, so
 * "Camera · 7" still fits whole on a three-column phone grid instead of reading "Came… · 7".
 */
fun albumBelowLabelMetrics(coverWidth: Dp): AlbumBelowLabelMetrics {
    val w = coverWidth.value
    return AlbumBelowLabelMetrics(
        nameSize = (w * 0.12f).coerceIn(13f, 16f).sp,
        horizontalPadding = (w * 0.07f).coerceIn(4f, 16f).dp,
    )
}

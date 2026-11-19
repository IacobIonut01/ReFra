/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable


/**
 * Where each badge of a grid cell sits. A null corner means the badge isn't shown.
 *
 * @param status The metadata (motion, night, panorama) and cloud indicators, which share a corner.
 */
@Immutable
data class CellBadgeLayout(
    val favorite: CellCorner?,
    val video: CellCorner?,
    val stack: CellCorner?,
    val status: CellCorner?,
)

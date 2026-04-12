/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
sealed class Dimens(val size: Dp) {
    data object Photo : Dimens(size = 100.dp)
    data object Album : Dimens(size = 178.dp)

    operator fun invoke(): Dp = size
}
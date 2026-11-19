/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Composable


/** What a [SheetOptionRow] shows at its end. */
sealed interface SheetRowTrailing {
    data object None : SheetRowTrailing
    data object Chevron : SheetRowTrailing
    data class Radio(val selected: Boolean) : SheetRowTrailing
    data class Switch(val checked: Boolean) : SheetRowTrailing
    data class Checkbox(val checked: Boolean) : SheetRowTrailing
    class Custom(val content: @Composable () -> Unit) : SheetRowTrailing
}

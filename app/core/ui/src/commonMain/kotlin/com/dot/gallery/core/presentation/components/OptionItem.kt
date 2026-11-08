/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector


data class OptionItem(
    val icon: ImageVector? = null,
    val text: String,
    val summary: String? = null,
    val onClick: (summary: String) -> Unit,
    val enabled: Boolean = true,
    /** Marks an option as needing attention, mirroring the header's unread badge. */
    val attention: Boolean = false,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
)

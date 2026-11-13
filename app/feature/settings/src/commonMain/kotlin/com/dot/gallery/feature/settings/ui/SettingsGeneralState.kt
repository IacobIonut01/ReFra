/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.lazy.LazyListState

data class SettingsGeneralState(
    /** `SdkCompat.supportsTrash` on Android; hides the trash section when false. */
    val supportsTrash: Boolean = true,
    val listState: LazyListState? = null,
)

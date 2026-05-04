/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class ReleaseNotes(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val highlights: List<ReleaseHighlight>
)

@Immutable
data class ReleaseHighlight(
    val tipId: String? = null,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: HelpIcon
)

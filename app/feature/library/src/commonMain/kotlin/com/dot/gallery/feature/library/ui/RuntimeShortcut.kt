/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.domain.model.UriMedia


/**
 * Runtime data for a shortcut, independent of the persisted layout.
 *
 * Larger tiles also show [subtitle] and photos: [previewMedia] newest first, or
 * [oldestPreviewMedia] when the tile is set to show the oldest. [dimPreview] greys them out.
 */
@Immutable
data class RuntimeShortcut(
    val shortcut: LibraryShortcut,
    val title: String,
    val icon: ImageVector?,
    val contentColor: Color,
    val useIndicator: Boolean,
    val indicatorCounter: Int,
    val route: String,
    val available: Boolean,
    val subtitle: String? = null,
    val previewMedia: List<UriMedia> = emptyList(),
    val oldestPreviewMedia: List<UriMedia> = emptyList(),
    val dimPreview: Boolean = false,
) {
    /** Only shortcuts that can show oldest photos offer the latest/oldest choice. */
    val hasCoverOrder: Boolean get() = oldestPreviewMedia.isNotEmpty()

    fun preview(oldestFirst: Boolean): List<UriMedia> =
        if (oldestFirst && hasCoverOrder) oldestPreviewMedia else previewMedia

    fun toItem(pref: LibraryShortcutPref) = LibraryShortcutItem(
        shortcut = shortcut,
        title = title,
        icon = icon,
        contentColor = contentColor,
        useIndicator = useIndicator,
        indicatorCounter = indicatorCounter,
        route = route,
        available = available,
        pref = pref
    )
}

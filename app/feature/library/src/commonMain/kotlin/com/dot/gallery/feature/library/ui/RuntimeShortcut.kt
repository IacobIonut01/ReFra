/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.domain.model.UriMedia


/** What a larger shortcut tile draws behind its label. */
enum class ShortcutPreviewStyle {
    /** The tile's photos, edge to edge. */
    PHOTOS,

    /** The photos as separate rounded thumbnails, so each reads as its own item (Categories). */
    SPREAD,

    /** Round face thumbnails from [RuntimeShortcut.previewFaces] (People). */
    FACES,

    /** A map at [RuntimeShortcut.mapLatitude], [RuntimeShortcut.mapLongitude] (Locations). */
    MAP,

    /** Abstract blurred shapes and never any photo, so the tile shows nothing it guards (Vault). */
    PLACEHOLDER,
}

/**
 * Runtime data for a shortcut, independent of the persisted layout.
 *
 * Larger tiles also show [subtitle] and photos: [previewMedia] newest first,
 * [oldestPreviewMedia] when the tile is set to show the oldest, or [shufflePreviewMedia] when it
 * is set to shuffle. [dimPreview] greys them out. [previewStyle] decides how they are drawn.
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
    val shufflePreviewMedia: List<UriMedia> = emptyList(),
    val dimPreview: Boolean = false,
    val previewStyle: ShortcutPreviewStyle = ShortcutPreviewStyle.PHOTOS,
    /** Face thumbnail models for [ShortcutPreviewStyle.FACES]; null draws a generic face. */
    val previewFaces: List<String?> = emptyList(),
    val mapLatitude: Double? = null,
    val mapLongitude: Double? = null,
) {
    /** Only shortcuts that can show oldest photos offer the latest/oldest choice. */
    val hasCoverOrder: Boolean get() = oldestPreviewMedia.isNotEmpty()

    val hasShuffle: Boolean get() = shufflePreviewMedia.isNotEmpty()

    /** The photos to draw. A placeholder tile never returns any, whatever it was given. */
    fun preview(oldestFirst: Boolean, shuffle: Boolean = false): List<UriMedia> = when {
        previewStyle == ShortcutPreviewStyle.PLACEHOLDER -> emptyList()
        shuffle && hasShuffle -> shufflePreviewMedia
        oldestFirst && hasCoverOrder -> oldestPreviewMedia
        else -> previewMedia
    }

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

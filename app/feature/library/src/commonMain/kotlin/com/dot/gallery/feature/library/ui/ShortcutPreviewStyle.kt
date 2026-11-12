/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui


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

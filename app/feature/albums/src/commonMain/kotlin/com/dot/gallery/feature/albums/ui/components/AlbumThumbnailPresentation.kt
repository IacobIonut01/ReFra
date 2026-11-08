/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

enum class AlbumThumbnailPresentation(
    val allowsMediaRequest: Boolean
) {
    MEDIA(allowsMediaRequest = true),
    LOCKED_PLACEHOLDER(allowsMediaRequest = false)
}

fun albumThumbnailPresentation(isLocked: Boolean): AlbumThumbnailPresentation =
    if (isLocked) {
        AlbumThumbnailPresentation.LOCKED_PLACEHOLDER
    } else {
        AlbumThumbnailPresentation.MEDIA
    }

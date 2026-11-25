/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.util

enum class MediaStoreWriteCollection { TYPED, FILES, UNSUPPORTED }

/**
 * Picks the MediaStore collection that accepts a RELATIVE_PATH write for a file of [mimeType]
 * placed in [relativePath], mirroring the primary directories MediaProvider allows per collection.
 */
fun mediaStoreWriteCollection(
    mimeType: String,
    relativePath: String,
    hasAllFilesAccess: Boolean
): MediaStoreWriteCollection {
    val isImage = mimeType.startsWith("image/")
    val isVideo = mimeType.startsWith("video/")
    val topDir = relativePath.split('/').firstOrNull { it.isNotBlank() }.orEmpty()
    val typedDirs = when {
        isImage -> listOf("DCIM", "Pictures")
        isVideo -> listOf("DCIM", "Movies", "Pictures")
        else -> emptyList()
    }
    return when {
        typedDirs.any { it.equals(topDir, ignoreCase = true) } -> MediaStoreWriteCollection.TYPED
        topDir.equals("Download", ignoreCase = true) ||
            topDir.equals("Documents", ignoreCase = true) -> MediaStoreWriteCollection.FILES
        hasAllFilesAccess -> if (isImage || isVideo) MediaStoreWriteCollection.TYPED
        else MediaStoreWriteCollection.FILES
        else -> MediaStoreWriteCollection.UNSUPPORTED
    }
}

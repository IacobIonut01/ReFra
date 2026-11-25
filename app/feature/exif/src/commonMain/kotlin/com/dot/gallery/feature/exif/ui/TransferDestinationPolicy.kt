/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.MediaStoreWriteCollection
import com.dot.gallery.domain.util.mediaStoreWriteCollection

/**
 * A copy only reads the source through MediaStore and inserts a brand new file at the
 * destination, so restricted sources (another app's `Android/media/<package>` folder, or a
 * different storage volume) do not need write access. Only the destination has to be writable.
 */
fun isAlbumCopyDestinationEnabled(
    hasFullMediaAccess: Boolean,
    albumRelativePath: String,
    isCloudAlbum: Boolean = false,
    hasAllFilesAccess: Boolean,
    sources: List<AlbumDestinationSource> = emptyList(),
): Boolean {
    if (isCloudAlbum) return false
    if (sources.any {
            mediaStoreWriteCollection(it.mimeType, albumRelativePath, hasAllFilesAccess) ==
                MediaStoreWriteCollection.UNSUPPORTED
        }
    ) return false
    if (hasFullMediaAccess) return true
    return !albumRelativePath.isAndroidMediaPath()
}

/**
 * A move needs the original gone once the destination holds it. Sources inside another app's
 * `Android/media/<package>` folder cannot be renamed into an album, so they take the copy +
 * delete-request path instead: the destination rules are the same as for a copy, plus a source
 * that actually differs from the destination folder.
 */
fun isAlbumMoveDestinationEnabled(
    hasFullMediaAccess: Boolean,
    albumVolume: String,
    albumRelativePath: String,
    sources: List<AlbumDestinationSource>,
    hasAllFilesAccess: Boolean,
    isCloudAlbum: Boolean = false,
): Boolean {
    if (!isAlbumCopyDestinationEnabled(
            hasFullMediaAccess, albumRelativePath, isCloudAlbum, hasAllFilesAccess, sources
        )
    ) {
        return false
    }
    if (!hasFullMediaAccess && sources.any { it.volume != albumVolume }) return false
    return sources.isEmpty() || sources.any {
        it.volume != albumVolume ||
            normalizeMediaStoreRelativePath(it.relativePath) !=
            normalizeMediaStoreRelativePath(albumRelativePath)
    }
}

/** Sources that only a copy + delete request can take out of their folder. */
fun <T : Media> restrictedMoveSources(
    hasFullMediaAccess: Boolean,
    mediaList: List<T>,
): List<T> = if (hasFullMediaAccess) emptyList()
    else mediaList.filter { it.relativePath.isAndroidMediaPath() }

fun String.isAndroidMediaPath(): Boolean = normalizeMediaStoreRelativePath(this).let {
    it == "android/media" || it.startsWith("android/media/")
}

fun normalizeMediaStoreRelativePath(path: String): String = path
    .split('/')
    .filter(String::isNotBlank)
    .joinToString("/")
    .lowercase()

fun resolveNewAlbumMovePath(albumName: String, hasAllFilesAccess: Boolean): String =
    if (hasAllFilesAccess) albumName else "Pictures/$albumName"

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.util

/**
 * Folder a vault item is restored into: its original [originalRelativePath] when MediaStore accepts
 * a typed write there, otherwise the default `Restored` folder for its kind.
 */
fun vaultRestoreRelativePath(
    mimeType: String,
    originalRelativePath: String,
    hasAllFilesAccess: Boolean
): String {
    val original = originalRelativePath.trim('/')
    val isVideo = mimeType.startsWith("video/")
    val restorable = original.isNotBlank() && !original.startsWith("cloud/", ignoreCase = true) &&
        mediaStoreWriteCollection(mimeType, original, hasAllFilesAccess) ==
        MediaStoreWriteCollection.TYPED
    return when {
        restorable -> original
        isVideo -> "Movies/Restored"
        else -> "Pictures/Restored"
    }
}

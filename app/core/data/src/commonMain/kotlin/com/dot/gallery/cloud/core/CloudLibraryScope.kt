/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core

import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.data.entity.CloudUploadPrefEntity

/**
 * Per-account limit on which remote folders count as the synced library for
 * path-based providers (WebDAV family). Stored in the legacy `syncFolders`
 * column — it predates this feature and was never written by any UI, so old
 * values like "Camera,Pictures" decode to [WholeAccount].
 */
sealed interface CloudLibraryScope {
    data object WholeAccount : CloudLibraryScope
    data object BackupFolders : CloudLibraryScope
    data class Folder(val path: String) : CloudLibraryScope

    companion object {
        fun decode(raw: String): CloudLibraryScope = when {
            raw == "backup:" -> BackupFolders
            raw.startsWith("folder:") -> {
                val path = UploadTargetResolver.sanitizePath(raw.removePrefix("folder:"))
                if (path.isEmpty()) WholeAccount else Folder(path)
            }
            else -> WholeAccount
        }
    }
}

fun CloudLibraryScope.encode(): String = when (this) {
    CloudLibraryScope.WholeAccount -> ""
    CloudLibraryScope.BackupFolders -> "backup:"
    is CloudLibraryScope.Folder -> {
        val path = UploadTargetResolver.sanitizePath(path)
        if (path.isEmpty()) "" else "folder:$path"
    }
}

val CloudServerConfigEntity.libraryScope: CloudLibraryScope
    get() = CloudLibraryScope.decode(syncFolders)

/**
 * Remote folder roots (relative paths, no leading slash) the library scan and
 * sync are limited to. `[""]` means the account root — the historical
 * whole-account behaviour.
 */
fun cloudLibraryRoots(
    scope: CloudLibraryScope,
    config: CloudServerConfigEntity,
    enabledPrefs: List<CloudUploadPrefEntity>,
    defaultUploadFolder: String
): List<String> = when (scope) {
    CloudLibraryScope.WholeAccount -> listOf("")
    is CloudLibraryScope.Folder -> listOf(scope.path)
    CloudLibraryScope.BackupFolders -> backupLibraryRoots(config, enabledPrefs, defaultUploadFolder)
}

private fun backupLibraryRoots(
    config: CloudServerConfigEntity,
    enabledPrefs: List<CloudUploadPrefEntity>,
    defaultUploadFolder: String
): List<String> {
    val candidates = mutableListOf<String>()
    for (pref in enabledPrefs) {
        for (isVideo in listOf(false, true)) {
            candidates += UploadTargetResolver.resolve(config, pref, isVideo)
                ?: defaultUploadFolder
        }
    }
    candidates += UploadTargetResolver.sanitizePath(config.uploadBasePath)
    candidates += UploadTargetResolver.sanitizePath(config.uploadVideosPath)
    val distinct = candidates
        .map { UploadTargetResolver.sanitizePath(it) }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf(UploadTargetResolver.sanitizePath(defaultUploadFolder)) }
        .distinct()
    return distinct
        .filter { root -> distinct.none { other -> other != root && root.startsWith("$other/") } }
        .sorted()
}

/**
 * Whether a remote path (relative, no leading slash) sits inside one of the
 * library roots. Case-sensitive, like the remote filesystem listing.
 */
fun isInCloudLibrary(remotePath: String, roots: List<String>): Boolean =
    roots.any { root ->
        root.isEmpty() || remotePath == root || remotePath.startsWith("$root/")
    }

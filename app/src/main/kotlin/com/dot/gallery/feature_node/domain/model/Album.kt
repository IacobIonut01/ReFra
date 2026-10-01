/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.domain.model

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import com.dot.gallery.cloud.core.CloudAlbumIdentity
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Stable
@Parcelize
data class Album(
    val id: Long = 0,
    val label: String,
    val uri: Uri,
    val pathToThumbnail: String,
    val relativePath: String,
    val timestamp: Long,
    var count: Long = 0,
    var size: Long = 0,
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val mergedAlbumIds: List<Long> = emptyList(),
    val mergeReasons: List<AlbumMergeReason> = emptyList(),
    val storageVolume: String? = null,
    val cloudIdentity: CloudAlbumIdentity? = null,
    // Cloud sharing metadata (from CloudAlbum.toAlbum) — drives the "Shared"
    // badge on album cards and album-manage menu gating.
    val isCloudShared: Boolean = false,
    val isCloudOwned: Boolean = true,
    val cloudShareRole: String = "",
    val cloudOwnerName: String = "",
    /** Raw remote album name, without the " (Provider)" label suffix. */
    val cloudName: String = "",
) : Parcelable {

    val sourceAlbumIds: List<Long> get() = mergedAlbumIds.ifEmpty { listOf(id) }

    val isMerged: Boolean get() = sourceAlbumIds.size > 1 || mergeReasons.isNotEmpty()

    val mergesSubfolders: Boolean get() = AlbumMergeReason.SUBFOLDERS in mergeReasons

    val mergesByName: Boolean get() = AlbumMergeReason.SAME_NAME in mergeReasons

    val key: String
        get() = "{$id, $uri, $timestamp}"

    val idLessKey: String
        get() = "{$uri, $timestamp}"

    @IgnoredOnParcel
    @Stable
    val volume: String = storageVolume
        ?: pathToThumbnail.substringBeforeLast("/").removeSuffix(relativePath.removeSuffix("/"))

    @IgnoredOnParcel
    @Stable
    val absolutePath: String = resolveAlbumAbsolutePath(pathToThumbnail, relativePath, storageVolume)

    @IgnoredOnParcel
    @Stable
    val isOnSdcard: Boolean =
        volume.toLowerCase(Locale.current).matches(SD_CARD_REGEX)

    companion object {
        private val SD_CARD_REGEX = ".*[0-9a-f]{4}-[0-9a-f]{4}".toRegex()

        val NewAlbum = Album(
            id = -200,
            label = "New Album",
            uri = Uri.EMPTY,
            pathToThumbnail = "",
            relativePath = "",
            timestamp = 0
        )
    }
}

internal fun resolveAlbumAbsolutePath(
    pathToThumbnail: String,
    relativePath: String,
    storageVolume: String? = null,
): String {
    val normalizedRelativePath = relativePath.trim('/')
    if (normalizedRelativePath.isEmpty()) return pathToThumbnail.substringBeforeLast('/')
    val filesystemRoot = pathToThumbnail.substringBefore("/$normalizedRelativePath/", "")
    if (filesystemRoot.isNotEmpty()) return "$filesystemRoot/$normalizedRelativePath/"
    return relativePath.takeIf {
        storageVolume == null || storageVolume == "external" || storageVolume == "external_primary"
    }.orEmpty()
}

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.cloudMediaId
import com.dot.gallery.domain.date.getDate
import com.dot.gallery.domain.date.parseTimestampFromFilename
import com.dot.gallery.domain.model.CaptureTimeOrigin
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.uri.parseUri


@Entity(
    tableName = "cloud_media",
    primaryKeys = ["remoteId", "providerType", "serverConfigId"],
    indices = [
        Index(value = ["serverConfigId"]),
        Index(value = ["syncState"]),
        Index(value = ["timestamp"]),
        Index(value = ["favorite"]),
        Index(value = ["trashed"]),
        Index(value = ["contentHash"]),
        Index(value = ["globalMediaId"], unique = true)
    ]
)
data class CloudMediaEntity(
    val remoteId: String,
    val providerType: ProviderType,
    val serverConfigId: Long,
    @ColumnInfo(defaultValue = "0")
    val globalMediaId: Long = cloudMediaId(providerType, serverConfigId, remoteId),
    val label: String = "",
    val path: String = "",
    val relativePath: String = "",
    val mimeType: String = "",
    val timestamp: Long = 0L,
    val takenTimestamp: Long? = null,
    val size: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val duration: String? = null,
    val favorite: Boolean = false,
    val trashed: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val archived: Boolean = false,
    val syncState: SyncState = SyncState.REMOTE_ONLY,
    @ColumnInfo(defaultValue = "")
    val localCopyPath: String = "",
    /**
     * True only when [localCopyPath] points at a file the app itself downloaded
     * (CloudDownloadWorker). Upload paths also fill [localCopyPath] — with the user's
     * ORIGINAL file — so this flag is what makes "delete local copies on remote delete"
     * safe: it can never select user-owned media.
     */
    @ColumnInfo(defaultValue = "0")
    val appLocalCopy: Boolean = false,
    val contentHash: String? = null,
    val thumbnailUrl: String = "",
    val originalUrl: String = "",
    val lastSyncedAt: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    @ColumnInfo(name = "state_name")
    val state: String? = null,
    val country: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    @ColumnInfo(defaultValue = "")
    val lensModel: String? = null,
    @ColumnInfo(defaultValue = "")
    val imageDescription: String? = null,
    val dateTimeOriginal: String? = null,
    val exposureTime: String? = null,
    val aperture: String? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    @ColumnInfo(defaultValue = "")
    val fileId: String = ""
) {
    fun toUriMedia(fullDate: String? = null): UriMedia {
        val effectiveGlobalMediaId = if (globalMediaId != 0L) globalMediaId else cloudMediaId(providerType, serverConfigId, remoteId)
        val base = "cloud://${providerType.name}/$remoteId?size=preview"
        // Thread the server's numeric file id through the URI so the image
        // pipelines (Sketch/Glide) can request server-side video previews,
        // which require `fileId` on ownCloud/Nextcloud's core/preview endpoint.
        // Also thread the owning account id (cfg) so the right provider instance
        // resolves the URL when several accounts of the same type are configured.
        val withFile = if (fileId.isNotBlank()) "$base&fileId=$fileId" else base
        val cloudUri = parseUri(if (serverConfigId > 0L) "$withFile&cfg=$serverConfigId" else withFile)
        // timestamp → seconds (MediaStore DATE_MODIFIED convention)
        val timestampSeconds = timestamp / 1000L
        // takenTimestamp stays in millis (MediaStore DATE_TAKEN convention)
        // Media.definedTimestamp will divide by 1000 when needed
        val displayName = providerType.displayName
        val displayPath = if (path.isNotBlank()) "$displayName/$path" else "$displayName/$label"
        // Capture-time tiering mirrors the local MediaStore flow
        // (MediaStore date-taken -> filename -> modified): providers that can't report a
        // capture date (WebDAV/Nextcloud/ownCloud/SMB/NFS) still get a filename-derived
        // one, which the capture-time index later refines with the real embedded value.
        val filenameTakenTimestamp = if (takenTimestamp == null) {
            label.parseTimestampFromFilename()
        } else null
        val resolvedTakenTimestamp = takenTimestamp ?: filenameTakenTimestamp
        val displayDateSeconds = resolvedTakenTimestamp?.let { it / 1000L } ?: timestampSeconds
        // Path-based providers (WebDAV/Nextcloud/ownCloud/SMB/NFS) can't report a video's duration,
        // but the item is still a video. `Media.isVideo` requires a non-null duration, so for video
        // mime types default a missing duration to "" (rendered as unknown length). Without this the
        // video would be mis-handled as a still image: no player, and no server poster thumbnail.
        val resolvedDuration = duration ?: if (mimeType.startsWith("video/")) "" else null
        return UriMedia(
            id = effectiveGlobalMediaId,
            label = label,
            uri = cloudUri,
            path = displayPath,
            relativePath = if (relativePath.isNotBlank()) "$displayName/$relativePath" else displayName,
            albumID = CLOUD_ALBUM_ID,
            albumLabel = displayName,
            timestamp = timestampSeconds,
            takenTimestamp = resolvedTakenTimestamp,
            captureTimeOrigin = when {
                takenTimestamp != null -> CaptureTimeOrigin.CLOUD_PROVIDER
                filenameTakenTimestamp != null -> CaptureTimeOrigin.FILENAME
                else -> CaptureTimeOrigin.MODIFIED_FALLBACK
            },
            fullDate = fullDate ?: displayDateSeconds.getDate(EXTENDED_DATE_FORMAT),
            mimeType = mimeType,
            favorite = if (favorite) 1 else 0,
            trashed = if (trashed) 1 else 0,
            size = size,
            duration = resolvedDuration
        )
    }

    companion object {
        const val CLOUD_ALBUM_ID = -500L
    }
}

private const val EXTENDED_DATE_FORMAT = "EEE, MMM d, yyyy"

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.data.entity

import android.net.Uri
import android.text.format.DateFormat
import androidx.core.net.toUri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.cloudMediaId
import com.dot.gallery.core.Constants
import com.dot.gallery.feature_node.domain.model.CaptureTimeOrigin
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.util.getCurrentAndroid
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.parseTimestampFromFilename
import java.util.Base64
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

import androidx.compose.ui.text.intl.Locale as ComposeLocale

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
    init {
        require(globalMediaId == cloudMediaId(providerType, serverConfigId, remoteId)) {
            "globalMediaId must be derived from providerType, serverConfigId, and remoteId"
        }
    }

    fun toUriMedia(fullDate: String? = null): Media.UriMedia {
        val base = "cloud://${providerType.name}/$remoteId?size=preview"
        // Thread the server's numeric file id through the URI so the image
        // pipelines (Sketch/Glide) can request server-side video previews,
        // which require `fileId` on ownCloud/Nextcloud's core/preview endpoint.
        // Also thread the owning account id (cfg) so the right provider instance
        // resolves the URL when several accounts of the same type are configured.
        val withFile = if (fileId.isNotBlank()) "$base&fileId=$fileId" else base
        val cloudUri = (if (serverConfigId > 0L) "$withFile&cfg=$serverConfigId" else withFile).toUri()
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
        // Use takenTimestamp (millis/1000) for display date if available, else fallback to timestamp seconds
        val displayDateSeconds = resolvedTakenTimestamp?.let { it / 1000L } ?: timestampSeconds
        // Path-based providers (WebDAV/Nextcloud/ownCloud/SMB/NFS) can't report a video's duration,
        // but the item is still a video. `Media.isVideo` requires a non-null duration, so for video
        // mime types default a missing duration to "" (rendered as unknown length). Without this the
        // video would be mis-handled as a still image: no player, and no server poster thumbnail.
        val resolvedDuration = duration ?: if (mimeType.startsWith("video/")) "" else null
        return Media.UriMedia(
            id = globalMediaId,
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
            fullDate = fullDate ?: displayDateSeconds.getDate(Constants.EXTENDED_DATE_FORMAT),
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

/**
 * Provider-reported capture time, else the filename-derived one — the same
 * resolution [toUriMedia] applies. Shared with [CloudMediaSnapshotMapper] so
 * the pre-computed display date matches what the mapped media ends up with.
 */
internal fun CloudMediaEntity.effectiveTakenTimestamp(): Long? =
    takenTimestamp ?: label.parseTimestampFromFilename()

internal class CloudMediaSnapshotMapper {
    private data class Entry(val source: CloudMediaEntity, val media: Media.UriMedia)
    private var entries = emptyMap<Long, Entry>()
    private var locale: Locale? = null
    private var timeZone: TimeZone? = null

    fun map(source: List<CloudMediaEntity>): List<Media.UriMedia> {
        val currentLocale = ComposeLocale.getCurrentAndroid()
        val currentTimeZone = TimeZone.getDefault()
        val sameFormatting = locale == currentLocale && timeZone?.id == currentTimeZone.id &&
            timeZone?.hasSameRules(currentTimeZone) == true
        val calendar by lazy { Calendar.getInstance(currentTimeZone, currentLocale) }
        val next = HashMap<Long, Entry>(source.size)
        val result = ArrayList<Media.UriMedia>(source.size)
        for (entity in source) {
            val previous = entries[entity.globalMediaId]
            val entry = if (sameFormatting && previous != null &&
                previous.source.hasSameMediaPresentation(entity)
            ) previous else {
                calendar.timeInMillis = (entity.effectiveTakenTimestamp()?.let { it / 1000L }
                    ?: (entity.timestamp / 1000L)) * 1000L
                val date = DateFormat.format(Constants.EXTENDED_DATE_FORMAT, calendar).toString()
                Entry(entity, entity.toUriMedia(fullDate = date))
            }
            next[entity.globalMediaId] = entry
            result += entry.media
        }
        entries = next
        locale = currentLocale
        timeZone = currentTimeZone
        return result
    }
}

private fun CloudMediaEntity.hasSameMediaPresentation(other: CloudMediaEntity): Boolean =
    globalMediaId == other.globalMediaId && providerType == other.providerType &&
    serverConfigId == other.serverConfigId && remoteId == other.remoteId && fileId == other.fileId &&
    label == other.label && path == other.path && relativePath == other.relativePath &&
    mimeType == other.mimeType && timestamp == other.timestamp && takenTimestamp == other.takenTimestamp &&
    size == other.size && duration == other.duration && favorite == other.favorite && trashed == other.trashed

@Entity(
    tableName = "cloud_backup_revision",
    primaryKeys = ["serverConfigId", "localUri"],
    indices = [
        Index(value = ["serverConfigId"]),
        Index(value = ["providerType"]),
        Index(value = ["serverConfigId", "remoteId"])
    ]
)
data class CloudBackupRevisionEntity(
    val serverConfigId: Long,
    val providerType: ProviderType,
    val localUri: String,
    val localSize: Long,
    val localTimestamp: Long,
    val remoteId: String,
    val remoteFingerprint: String,
    val verifiedAt: Long
)

fun CloudMediaEntity.backupFingerprint(): String =
    contentHash?.takeIf { it.isNotBlank() }?.let(::canonicalBackupChecksum) ?: "$size|$timestamp"

fun canonicalBackupChecksum(value: String): String {
    val normalized = value.lowercase()
    if (normalized.length == 40 && normalized.all { it in '0'..'9' || it in 'a'..'f' }) {
        return normalized
    }
    return runCatching { Base64.getDecoder().decode(value) }
        .getOrNull()
        ?.takeIf { it.size == 20 }
        ?.joinToString("") { "%02x".format(it) }
        ?: value
}

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/*
 * One serializable record type per line in `sections/<section>.jsonl`.
 * Records reference media/albums through portable refs (see MediaRefs.kt) and never carry
 * device-local ids as authoritative identity. Ids that only link records inside the archive
 * (category/collection/group/section ids) are kept as `sourceId` so restore can remap them.
 */

// ── SETTINGS ────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class SettingsRecord(val name: String, val value: SettingValue)

// ── ALBUMS ──────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class AlbumPinRecord(val album: AlbumRef)

@Serializable
data class AlbumLockRecord(val album: AlbumRef)

@Serializable
data class IgnoredAlbumRecord(
    val sourceId: Long = 0L,
    val label: String? = null,
    val wildcard: String? = null,
    val albums: List<AlbumRef> = emptyList(),
    val location: Int = 0,
    val matchedAlbums: List<String> = emptyList()
)

@Serializable
data class AlbumCoverRecord(
    val album: AlbumRef,
    val cover: MediaRef? = null,
    /** Original device URI; only used when the media ref cannot be resolved on the same device. */
    val coverUri: String? = null
)

@Serializable
data class AlbumGroupRecord(
    val sourceId: Long,
    val label: String,
    val timestamp: Long = 0L,
    val albums: List<AlbumRef> = emptyList()
)

@Serializable
data class AlbumSectionRecord(
    val sourceId: Long,
    val label: String,
    val type: Int,
    val displayOrder: Int,
    val isVisible: Boolean,
    val isExpanded: Boolean,
    val sortKind: String,
    val sortOrder: String,
    val albums: List<AlbumRef> = emptyList()
)

@Serializable
data class MergedSubfolderRecord(
    val sourceId: Long,
    val folderKey: String?,
    val volume: String,
    val relativePath: String,
    val displayMode: String
)

@Serializable
data class TimelineSettingsRecord(
    /** JSON-encoded `MediaOrder`, matching the Room converter's representation. */
    val timelineMediaOrder: String,
    val albumMediaOrder: String,
    val groupTimelineByMonth: Boolean,
    val groupTimelineByYear: Boolean,
    val groupTimelineInAlbums: Boolean
)

// ── COLLECTIONS ─────────────────────────────────────────────────────────────────────────────────

@Serializable
data class CollectionRecord(
    val sourceId: Long,
    val label: String,
    val cover: MediaRef? = null,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val media: List<CollectionMediaRecord> = emptyList(),
    val albums: List<CollectionAlbumRecord> = emptyList(),
    val excluded: List<MediaRef> = emptyList()
)

@Serializable
data class CollectionMediaRecord(val media: MediaRef, val addedAt: Long = 0L)

@Serializable
data class CollectionAlbumRecord(val album: AlbumRef, val addedAt: Long = 0L)

// ── CATEGORIES ──────────────────────────────────────────────────────────────────────────────────

@Serializable
data class CategoryRecord(
    val sourceId: Long,
    val name: String,
    val searchTerms: String,
    /** Base64-encoded little-endian FloatArray bytes, or null when no embedding was cached. */
    val embedding: String? = null,
    val references: List<MediaRef> = emptyList(),
    val threshold: Float,
    val isUserCreated: Boolean,
    val isPinned: Boolean,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val memberships: List<CategoryMembershipRecord> = emptyList()
)

@Serializable
data class CategoryMembershipRecord(
    val media: MediaRef,
    val similarityScore: Float = 0f,
    val addedAt: Long = 0L,
    val isManuallyAdded: Boolean = false,
    val resultRevision: String = ""
)

// ── METADATA ────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class MetadataRecord(
    val media: MediaRef,
    val core: MediaMetadataCoreRecord? = null,
    val video: MediaMetadataVideoRecord? = null,
    val flags: MediaMetadataFlagsRecord? = null,
    val captureTime: MediaCaptureTimeRecord? = null
)

@Serializable
data class MediaMetadataCoreRecord(
    val imageDescription: String? = null,
    val dateTimeOriginal: String? = null,
    val manufacturerName: String? = null,
    val modelName: String? = null,
    val lensModel: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val focalLength: Double? = null,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsLocationName: String? = null,
    val gpsLocationNameCountry: String? = null,
    val gpsLocationNameCity: String? = null,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageResolutionX: Double? = null,
    val imageResolutionY: Double? = null,
    val resolutionUnit: Int? = null
)

@Serializable
data class MediaMetadataVideoRecord(
    val durationMs: Long? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val frameRate: Float? = null,
    val bitRate: Int? = null
)

@Serializable
data class MediaMetadataFlagsRecord(
    val isNightMode: Boolean = false,
    val isPanorama: Boolean = false,
    val isPhotosphere: Boolean = false,
    val isLongExposure: Boolean = false,
    val isMotionPhoto: Boolean = false
)

@Serializable
data class MediaCaptureTimeRecord(
    val captureTimestampMillis: Long,
    val origin: String,
    val sourceModifiedSeconds: Long,
    val sourceTakenTimestampMillis: Long? = null,
    val sourceSize: Long,
    val sourcePath: String,
    val updatedAtMillis: Long
)

// ── SEARCH HISTORY ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class SearchHistoryRecord(
    val timestamp: Long,
    val query: String,
    val media: MediaRef? = null,
    val mediaLabel: String? = null,
    val mediaUri: String? = null
)

// ── PEOPLE ─────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class PersonRecord(
    val id: String,
    val name: String = "",
    val birthDate: String = "",
    val hidden: Boolean = false,
    val thumbnailMedia: MediaRef? = null
)

@Serializable
data class FaceExclusionRecord(
    val media: MediaRef,
    val personId: String,
    val createdAt: Long = 0L
)

@Serializable
data class FaceLinkRecord(
    val media: MediaRef,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val personId: String,
    /** `INCLUDE` or `EXCLUDE`, mirroring `FaceLinkKind`. */
    val kind: String,
    val createdAt: Long = 0L
)

@Serializable
data class FaceSuppressionRecord(
    val media: MediaRef,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val createdAt: Long = 0L
)

// ── CLOUD PREFERENCES ──────────────────────────────────────────────────────────────────────────

@Serializable
data class CloudAlbumSyncRecord(
    val sourceAccountId: String,
    val providerType: String,
    val remoteAlbumId: String,
    val albumName: String = "",
    val syncEnabled: Boolean = true
)

@Serializable
data class CloudUploadPrefRecord(
    val sourceAccountId: String,
    val providerType: String,
    val album: AlbumRef,
    val albumLabel: String = "",
    val uploadEnabled: Boolean = false,
    val deleteLocalAfterUpload: Boolean = false,
    val customPath: String = ""
)

@Serializable
data class CloudDeleteLocalPrefRecord(
    val album: AlbumRef,
    val albumLabel: String = "",
    val enabled: Boolean = false
)

@Serializable
data class CloudOfflinePinRecord(
    val sourceAccountId: String,
    val providerType: String,
    val label: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class FreeSpacePrefRecord(
    val sourceAccountId: String,
    val keepFavorites: Boolean,
    val cutoffDays: Int,
    val autoEnabled: Boolean,
    val autoIntervalDays: Int,
    val scheduled: Boolean
)

// ── EDIT BACKUPS ───────────────────────────────────────────────────────────────────────────────

@Serializable
data class EditBackupRecord(
    val media: MediaRef,
    val originalUri: String,
    val originalMimeType: String,
    val editTimestamp: Long,
    val fileName: String,
    val archiveSize: Long = -1L,
    val sha256: String = ""
)

// ── DEVICE UI ──────────────────────────────────────────────────────────────────────────────────

@Serializable
data class GridSizeRecord(val name: String, val value: Int)

@Serializable
data class WidgetRecord(
    /** Original SharedPreferences key (`widget_<appWidgetId>`). */
    val name: String = "",
    /** Original `WidgetData` JSON; restored only when the host widget id still exists. */
    val payload: String
)

@Serializable
data class PrivateFolderRecord(
    val uri: String,
    val enabled: Boolean,
    val gateMode: String? = null
)

// ── Mixed-section line wrappers ────────────────────────────────────────────────────────────────
// Albums, people, cloud preferences, and device UI write several record kinds into one section
// file, so each line carries a serial name discriminator.

@Serializable
sealed class AlbumRecord {
    @Serializable @kotlinx.serialization.SerialName("pin")
    data class Pin(val value: AlbumPinRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("lock")
    data class Lock(val value: AlbumLockRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("ignored")
    data class Ignored(val value: IgnoredAlbumRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("cover")
    data class Cover(val value: AlbumCoverRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("group")
    data class Group(val value: AlbumGroupRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("section")
    data class Section(val value: AlbumSectionRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("merged")
    data class Merged(val value: MergedSubfolderRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("timeline")
    data class Timeline(val value: TimelineSettingsRecord) : AlbumRecord()
}

@Serializable
sealed class PeopleRecord {
    @Serializable @kotlinx.serialization.SerialName("person")
    data class Person(val value: PersonRecord) : PeopleRecord()

    @Serializable @kotlinx.serialization.SerialName("exclusion")
    data class Exclusion(val value: FaceExclusionRecord) : PeopleRecord()

    @Serializable @kotlinx.serialization.SerialName("link")
    data class Link(val value: FaceLinkRecord) : PeopleRecord()

    @Serializable @kotlinx.serialization.SerialName("suppression")
    data class Suppression(val value: FaceSuppressionRecord) : PeopleRecord()
}

@Serializable
sealed class CloudPrefRecord {
    @Serializable @kotlinx.serialization.SerialName("album_sync")
    data class AlbumSync(val value: CloudAlbumSyncRecord) : CloudPrefRecord()

    @Serializable @kotlinx.serialization.SerialName("upload")
    data class Upload(val value: CloudUploadPrefRecord) : CloudPrefRecord()

    @Serializable @kotlinx.serialization.SerialName("delete_local")
    data class DeleteLocal(val value: CloudDeleteLocalPrefRecord) : CloudPrefRecord()

    @Serializable @kotlinx.serialization.SerialName("offline_pin")
    data class OfflinePin(val value: CloudOfflinePinRecord) : CloudPrefRecord()

    @Serializable @kotlinx.serialization.SerialName("free_space")
    data class FreeSpace(val value: FreeSpacePrefRecord) : CloudPrefRecord()
}

@Serializable
sealed class DeviceUiRecord {
    @Serializable @kotlinx.serialization.SerialName("grid")
    data class Grid(val value: GridSizeRecord) : DeviceUiRecord()

    @Serializable @kotlinx.serialization.SerialName("widget")
    data class Widget(val value: WidgetRecord) : DeviceUiRecord()

    @Serializable @kotlinx.serialization.SerialName("private_folder")
    data class PrivateFolder(val value: PrivateFolderRecord) : DeviceUiRecord()
}

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.immich.data.dto

import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.google.gson.annotations.SerializedName

data class ImmichAssetDto(
    val id: String = "",
    @SerializedName("deviceAssetId") val deviceAssetId: String = "",
    @SerializedName("ownerId") val ownerId: String = "",
    @SerializedName("deviceId") val deviceId: String = "",
    val type: String = "IMAGE",
    @SerializedName("originalPath") val originalPath: String = "",
    @SerializedName("originalFileName") val originalFileName: String = "",
    @SerializedName("originalMimeType") val originalMimeType: String? = null,
    val thumbhash: String? = null,
    @SerializedName("fileCreatedAt") val fileCreatedAt: String? = null,
    @SerializedName("fileModifiedAt") val fileModifiedAt: String? = null,
    @SerializedName("localDateTime") val localDateTime: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("isFavorite") val isFavorite: Boolean = false,
    @SerializedName("isTrashed") val isTrashed: Boolean = false,
    @SerializedName("isArchived") val isArchived: Boolean = false,
    val visibility: String? = null,
    val duration: String? = null,
    @SerializedName("exifInfo") val exifInfo: ImmichExifDto? = null,
    val checksum: String? = null,
    @SerializedName("resized") val resized: Boolean = true
) {
    fun toCloudMediaEntity(serverConfigId: Long, baseUrl: String): CloudMediaEntity {
        val mimeType = originalMimeType ?: if (type == "VIDEO") "video/mp4" else "image/jpeg"
        val timestamp = parseIsoTimestamp(fileCreatedAt ?: localDateTime ?: "")
        return CloudMediaEntity(
            remoteId = id,
            providerType = ProviderType.IMMICH,
            serverConfigId = serverConfigId,
            label = originalFileName.ifBlank { originalPath.substringAfterLast('/') },
            path = originalPath,
            relativePath = originalPath.substringBeforeLast('/'),
            mimeType = mimeType,
            timestamp = timestamp,
            takenTimestamp = parseIsoTimestamp(localDateTime ?: "").takeIf { it > 0 },
            size = exifInfo?.fileSizeInByte ?: 0L,
            width = exifInfo?.exifImageWidth ?: 0,
            height = exifInfo?.exifImageHeight ?: 0,
            duration = duration,
            favorite = isFavorite,
            trashed = isTrashed || visibility.equals("HIDDEN", ignoreCase = true),
            archived = isArchived || visibility.equals("ARCHIVE", ignoreCase = true),
            syncState = SyncState.REMOTE_ONLY,
            contentHash = checksum,
            thumbnailUrl = "$baseUrl/api/assets/$id/thumbnail",
            originalUrl = "$baseUrl/api/assets/$id/original",
            lastSyncedAt = parseIsoTimestamp(fileModifiedAt ?: ""),
            fileId = deviceAssetId,
            latitude = exifInfo?.latitude,
            longitude = exifInfo?.longitude,
            city = exifInfo?.city,
            state = exifInfo?.state,
            country = exifInfo?.country,
            cameraMake = exifInfo?.make,
            cameraModel = exifInfo?.model,
            lensModel = exifInfo?.lensModel,
            imageDescription = exifInfo?.description,
            dateTimeOriginal = exifInfo?.dateTimeOriginal,
            exposureTime = exifInfo?.exposureTime,
            aperture = exifInfo?.fNumber?.let { "f/$it" },
            iso = exifInfo?.iso,
            focalLength = exifInfo?.focalLength
        )
    }

    companion object {
        fun parseIsoTimestamp(iso: String): Long {
            if (iso.isBlank()) return 0L
            return try {
                // Try parsing as Instant (e.g. "2024-01-15T10:30:00.000Z")
                java.time.Instant.parse(iso).toEpochMilli()
            } catch (_: Exception) {
                try {
                    // Try OffsetDateTime (e.g. "2024-01-15T10:30:00.000+00:00")
                    java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    try {
                        // Try LocalDateTime without timezone (e.g. "2024-01-15T10:30:00" or "2024-01-15 10:30:00")
                        java.time.LocalDateTime.parse(iso.replace(" ", "T"))
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        0L
                    }
                }
            }
        }
    }
}

data class ImmichExifDto(
    @SerializedName("exifImageWidth") val exifImageWidth: Int? = null,
    @SerializedName("exifImageHeight") val exifImageHeight: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val make: String? = null,
    val model: String? = null,
    @SerializedName("lensModel") val lensModel: String? = null,
    val description: String? = null,
    @SerializedName("dateTimeOriginal") val dateTimeOriginal: String? = null,
    @SerializedName("exposureTime") val exposureTime: String? = null,
    @SerializedName("fNumber") val fNumber: Double? = null,
    val iso: Int? = null,
    @SerializedName("focalLength") val focalLength: Double? = null,
    @SerializedName("fileSizeInByte") val fileSizeInByte: Long? = null
)

data class ImmichAlbumDto(
    val id: String = "",
    @SerializedName("albumName") val albumName: String = "",
    val description: String = "",
    @SerializedName("albumThumbnailAssetId") val albumThumbnailAssetId: String? = null,
    @SerializedName("assetCount") val assetCount: Int = 0,
    val shared: Boolean = false,
    @SerializedName("ownerId") val ownerId: String? = null,
    val owner: ImmichUserDto? = null,
    @SerializedName("hasSharedLink") val hasSharedLink: Boolean = false,
    @SerializedName("albumUsers") val albumUsers: List<ImmichAlbumUserDto>? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    val assets: List<ImmichAssetDto>? = null
)

/** `AlbumUserResponseDto` — a collaborator entry on `albumUsers`. */
data class ImmichAlbumUserDto(
    val user: ImmichUserDto? = null,
    val role: String = ""
)

/**
 * `BulkIdResponseDto` — per-item verdict returned by album asset add/remove.
 * The HTTP call answers 200 even when individual ids are discarded; the only
 * trace is this nested `success`/`error` pair.
 */
data class ImmichBulkIdResponseDto(
    val id: String = "",
    val success: Boolean = false,
    val error: String? = null
)

data class ImmichPersonDto(
    val id: String = "",
    val name: String = "",
    @SerializedName("birthDate") val birthDate: String? = null,
    @SerializedName("thumbnailPath") val thumbnailPath: String = "",
    @SerializedName("isHidden") val isHidden: Boolean = false
)

data class ImmichMapMarkerDto(
    val id: String = "",
    @SerializedName("lat") val latitude: Double = 0.0,
    @SerializedName("lon") val longitude: Double = 0.0,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null
)

data class ImmichServerAboutDto(
    val version: String = "",
    @SerializedName("versionUrl") val versionUrl: String = "",
    val licensed: Boolean = false
)

data class ImmichServerStorageDto(
    @SerializedName("diskUse") val diskUsed: String = "0",
    @SerializedName("diskAvailable") val diskAvailable: String = "0",
    @SerializedName("diskSize") val diskSize: String = "0",
    @SerializedName("diskUsagePercentage") val diskUsedPercentage: Double = 0.0,
    @SerializedName("diskUseRaw") val diskUsedRaw: Long = 0L,
    @SerializedName("diskAvailableRaw") val diskAvailableRaw: Long = 0L,
    @SerializedName("diskSizeRaw") val diskSizeRaw: Long = 0L
)

data class ImmichLoginDto(
    @SerializedName("email") val email: String = "",
    @SerializedName("password") val password: String = ""
)

data class ImmichLoginResponseDto(
    @SerializedName("accessToken") val accessToken: String = "",
    @SerializedName("userId") val userId: String = "",
    @SerializedName("userEmail") val userEmail: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("isAdmin") val isAdmin: Boolean = false
)

data class ImmichValidateTokenDto(
    @SerializedName("authStatus") val authStatus: Boolean = false
)

data class ImmichUserDto(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    @SerializedName("isAdmin") val isAdmin: Boolean = false,
    @SerializedName("avatarColor") val avatarColor: String = "",
    @SerializedName("quotaUsageInBytes") val quotaUsageInBytes: Long? = null,
    @SerializedName("quotaSizeInBytes") val quotaSizeInBytes: Long? = null
)

data class ImmichSearchDto(
    val query: String? = null,
    // Anchors the CLIP similarity search at an existing asset ("more like this").
    // The server looks up that asset's embedding; no raw vector ever leaves the server.
    @SerializedName("queryAssetId") val queryAssetId: String? = null,
    val page: Int = 1,
    @SerializedName("size") val pageSize: Int = 100
)

data class ImmichSearchResponseDto(
    val assets: ImmichSearchAssetsDto? = null
)

data class ImmichSearchAssetsDto(
    val total: Int = 0,
    val count: Int = 0,
    val items: List<ImmichAssetDto> = emptyList(),
    val facets: List<ImmichSearchFacetDto> = emptyList(),
    val nextPage: String? = null
)

data class ImmichTagDto(
    val id: String = "",
    val name: String = "",
    val value: String = "",
    val color: String? = null
)

/** `AssetDeltaSyncResponseDto` — `POST api/sync/delta-sync`. */
data class ImmichDeltaSyncResponseDto(
    val added: List<ImmichAssetDto> = emptyList(),
    val deleted: List<ImmichAssetIdsDto> = emptyList()
)

/** `AssetIdsResponseDto` — a bare asset id wrapper used by the sync endpoints. */
data class ImmichAssetIdsDto(
    val assetId: String = ""
)

data class ImmichSearchFacetDto(
    @SerializedName("fieldName") val fieldName: String = "",
    val counts: List<ImmichFacetCountDto> = emptyList()
)

data class ImmichFacetCountDto(
    val count: Int = 0,
    val value: String = ""
)

data class ImmichSharedLinkCreateDto(
    @SerializedName("assetIds") val assetIds: List<String> = emptyList(),
    val type: String = "INDIVIDUAL",
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("allowDownload") val allowDownload: Boolean = true,
    @SerializedName("showMetadata") val showMetadata: Boolean = true
)

data class ImmichSharedLinkDto(
    val id: String = "",
    val key: String = "",
    val type: String = "",
    val description: String? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("allowDownload") val allowDownload: Boolean = true,
    @SerializedName("allowUpload") val allowUpload: Boolean = false,
    @SerializedName("showMetadata") val showMetadata: Boolean = true,
    val password: String? = null,
    val assets: List<ImmichAssetDto> = emptyList(),
    val album: ImmichSharedLinkAlbumDto? = null,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class ImmichSharedLinkAlbumDto(
    val id: String = "",
    @SerializedName("albumName") val albumName: String = "",
    @SerializedName("albumThumbnailAssetId") val albumThumbnailAssetId: String? = null
)

data class ImmichAssetMediaResponseDto(
    val id: String = "",
    val status: String = ""
)

data class ImmichBulkUploadCheckDto(
    val assets: List<ImmichBulkCheckItemDto> = emptyList()
)

data class ImmichBulkCheckItemDto(
    val id: String = "",
    val checksum: String = ""
)

data class ImmichBulkUploadCheckResultDto(
    val results: List<ImmichBulkCheckResultItemDto> = emptyList()
)

data class ImmichBulkCheckResultItemDto(
    val id: String = "",
    val action: String = "",
    val assetId: String? = null,
    val reason: String? = null,
    val isTrashed: Boolean? = null
) {
    fun isSafeDuplicate(): Boolean = action == "reject" &&
        reason == "duplicate" &&
        !assetId.isNullOrBlank() &&
        isTrashed != true
}

data class ImmichMemoryDto(
    val id: String = "",
    val type: String = "on_this_day",
    val data: ImmichMemoryDataDto? = null,
    val assets: List<ImmichAssetDto> = emptyList(),
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("seenAt") val seenAt: String? = null
)

data class ImmichMemoryDataDto(
    val year: Int = 0
)

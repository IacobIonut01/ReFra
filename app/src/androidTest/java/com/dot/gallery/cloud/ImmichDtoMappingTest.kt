/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.immich.data.dto.ImmichAssetDto
import com.dot.gallery.cloud.immich.data.dto.ImmichExifDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Network-free tests for the Immich asset -> [com.dot.gallery.cloud.data.entity.CloudMediaEntity]
 * mapping and the Gson deserialization of the Immich v2.x JSON shape. Guards the field mapping
 * (mime inference, visibility -> archived/trashed, thumbnail/original URL construction, EXIF and
 * geo passthrough, ISO timestamp parsing) independently of any live server.
 */
@RunWith(AndroidJUnit4::class)
class ImmichDtoMappingTest {

    private val gson = Gson()

    @Test
    fun mapsCoreImageFields() {
        val dto = ImmichAssetDto(
            id = "asset-1",
            type = "IMAGE",
            originalPath = "upload/library/IMG_1.jpg",
            originalFileName = "IMG_1.jpg",
            originalMimeType = "image/jpeg",
            fileCreatedAt = "2024-01-15T10:30:00.000Z",
            isFavorite = true,
            checksum = "abc123"
        )
        val entity = dto.toCloudMediaEntity(serverConfigId = 7L, baseUrl = "https://immich.test")

        assertEquals("asset-1", entity.remoteId)
        assertEquals(ProviderType.IMMICH, entity.providerType)
        assertEquals(7L, entity.serverConfigId)
        assertEquals("IMG_1.jpg", entity.label)
        assertEquals("image/jpeg", entity.mimeType)
        assertTrue(entity.favorite)
        assertFalse(entity.trashed)
        assertFalse(entity.archived)
        assertEquals("abc123", entity.contentHash)
        assertEquals("https://immich.test/api/assets/asset-1/thumbnail", entity.thumbnailUrl)
        assertEquals("https://immich.test/api/assets/asset-1/original", entity.originalUrl)
        assertTrue("timestamp should parse from ISO", entity.timestamp > 0L)
    }

    @Test
    fun infersVideoMimeWhenMissing() {
        val dto = ImmichAssetDto(
            id = "v1",
            type = "VIDEO",
            originalFileName = "clip.mov",
            originalMimeType = null,
            duration = "0:00:12.34000"
        )
        val entity = dto.toCloudMediaEntity(1L, "https://x")
        assertEquals("video/mp4", entity.mimeType)
        assertEquals("0:00:12.34000", entity.duration)
    }

    @Test
    fun visibilityArchiveMapsToArchived() {
        val dto = ImmichAssetDto(id = "a", visibility = "ARCHIVE")
        val entity = dto.toCloudMediaEntity(1L, "https://x")
        assertTrue(entity.archived)
    }

    @Test
    fun visibilityHiddenMapsToTrashed() {
        val dto = ImmichAssetDto(id = "h", visibility = "HIDDEN")
        val entity = dto.toCloudMediaEntity(1L, "https://x")
        assertTrue(entity.trashed)
    }

    @Test
    fun explicitFlagsWin() {
        val dto = ImmichAssetDto(id = "t", isTrashed = true, isArchived = true)
        val entity = dto.toCloudMediaEntity(1L, "https://x")
        assertTrue(entity.trashed)
        assertTrue(entity.archived)
    }

    @Test
    fun exifAndGeoArePassedThrough() {
        val dto = ImmichAssetDto(
            id = "e",
            exifInfo = ImmichExifDto(
                latitude = 48.85,
                longitude = 2.35,
                city = "Paris",
                make = "Canon",
                model = "R6",
                iso = 400
            )
        )
        val entity = dto.toCloudMediaEntity(1L, "https://x")
        assertEquals(48.85, entity.latitude!!, 0.0001)
        assertEquals(2.35, entity.longitude!!, 0.0001)
        assertEquals("Paris", entity.city)
        assertEquals("Canon", entity.cameraMake)
        assertEquals("R6", entity.cameraModel)
        assertEquals(400, entity.iso)
    }

    @Test
    fun deserializesV2SearchJsonShape() {
        // The exact envelope returned by POST /api/search/metadata in Immich v2.x.
        val json = """
            {
              "id": "srv-asset-9",
              "type": "IMAGE",
              "originalFileName": "beach.jpg",
              "originalMimeType": "image/jpeg",
              "fileCreatedAt": "2023-07-01T08:00:00.000Z",
              "isFavorite": false,
              "isArchived": false,
              "exifInfo": { "make": "Sony", "iso": 100 }
            }
        """.trimIndent()

        val dto = gson.fromJson(json, ImmichAssetDto::class.java)
        assertEquals("srv-asset-9", dto.id)
        assertEquals("beach.jpg", dto.originalFileName)
        assertEquals("Sony", dto.exifInfo?.make)

        val entity = dto.toCloudMediaEntity(2L, "https://immich.test")
        assertEquals("beach.jpg", entity.label)
        assertEquals("Sony", entity.cameraMake)
    }

    @Test
    fun assetStorePathNeverBecomesRelativePath() {
        // Regression guard for #1270: `originalPath` is the SERVER's internal
        // asset-store layout (upload/<userId>/<shard>/<shard>), not a user album —
        // mirroring it into `relativePath` recreated that tree under Pictures/.
        val dto = ImmichAssetDto(
            id = "shard-1",
            type = "IMAGE",
            originalPath = "/photos/upload/e2f0c95f-f60b-41bd-af75-1c3d0f6b5a01/ed/4f/PXL_20260914_185724932.jpg",
            originalFileName = "PXL_20260914_185724932.jpg",
            originalMimeType = "image/jpeg"
        )
        val entity = dto.toCloudMediaEntity(serverConfigId = 3L, baseUrl = "https://immich.test")
        assertEquals("", entity.relativePath)
        assertEquals(dto.originalPath, entity.path)
    }
}

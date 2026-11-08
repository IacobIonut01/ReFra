/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class BackupV3ModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun sectionedManifestRoundTrips() {
        val manifest = BackupManifest(
            schemaVersion = 3,
            appVersionName = "5.3.0",
            exportedAt = 123L,
            sections = mapOf(
                BackupSection.ALBUMS.name to SectionDescriptor("sections/albums.jsonl", 4, "abc", 42)
            ),
            vaults = listOf(VaultEntry("uuid", "Private", emptyList())),
            credentialIncluded = true,
            encryption = BackupManifest.ENCRYPTION_PASSWORD
        )
        val decoded = json.decodeFromString(
            BackupManifest.serializer(),
            json.encodeToString(BackupManifest.serializer(), manifest)
        )
        assertEquals(3, decoded.schemaVersion)
        assertEquals(4, decoded.sections[BackupSection.ALBUMS.name]?.count)
        assertTrue(decoded.credentialIncluded)
        assertEquals(BackupManifest.ENCRYPTION_PASSWORD, decoded.encryption)
    }

    @Test
    fun selectionMergeDefaultSelectsOnlySectionsPresentInTheArchive() {
        val contents = BackupContents(
            schemaVersion = 3,
            appVersionName = "",
            exportedAt = 0L,
            encrypted = false,
            settingsCount = 10,
            localFavoritesCount = 0,
            cloudFavoritesCount = 0,
            cloudConfigsCount = 0,
            vaultCount = 0,
            vaultMediaCount = 0,
            sectionCounts = mapOf(
                BackupSection.SETTINGS to 10,
                BackupSection.CATEGORIES to 3
            )
        )
        val selection = BackupSelection().mergeDefault(contents)
        assertTrue(selection.settings)
        assertTrue(selection.categories)
        assertFalse(selection.metadata)
        assertFalse(selection.people)
        assertFalse(selection.editBackups)
    }

    @Test
    fun localAndCloudMediaRefsRoundTrip() {
        val refs = listOf<MediaRef>(
            MediaRef.Local("DCIM/Camera", "IMG_1.jpg", 42L, 99L, "Camera", "emulated/0"),
            MediaRef.Cloud("IMMICH", "IMMICH:7", "asset-1", "IMG_1.jpg")
        )
        val decoded = json.decodeFromString(
            ListSerializer(MediaRef.serializer()),
            json.encodeToString(ListSerializer(MediaRef.serializer()), refs)
        )
        assertEquals(refs, decoded)
    }

    @Test
    fun previewHeaderRoundTripsWithSectionCounts() {
        val header = BackupPreviewHeader(
            encrypted = true,
            sections = mapOf(BackupSection.VAULTS.name to 2),
            vaults = listOf(PreviewVault("uuid", "", 5)),
            credentialIncluded = true
        )
        val decoded = json.decodeFromString(
            BackupPreviewHeader.serializer(),
            json.encodeToString(BackupPreviewHeader.serializer(), header)
        )
        assertEquals(2, decoded.count(BackupSection.VAULTS))
        assertTrue(decoded.encrypted)
        assertEquals("", decoded.vaults.single().name)
    }

    @Test
    fun cryptoV2RoundTripsAndRejectsAWrongPassword() {
        val plaintext = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream().also { out ->
            BackupCrypto.encryptV2(plaintext.inputStream(), out, "correct horse")
        }.toByteArray()

        val decrypted = ByteArrayOutputStream().also { out ->
            BackupCrypto.decryptV2(encrypted.inputStream(), out, "correct horse")
        }.toByteArray()
        assertTrue(plaintext.contentEquals(decrypted))

        assertThrows(BackupPasswordException::class.java) {
            BackupCrypto.decryptV2(encrypted.inputStream(), ByteArrayOutputStream(), "wrong")
        }

        assertTrue(BackupCrypto.verifyPassword(encrypted.inputStream(), "correct horse"))
        assertFalse(BackupCrypto.verifyPassword(encrypted.inputStream(), "wrong"))
    }

    @Test
    fun legacyAndV2MagicsAreDistinguishable() {
        assertTrue(BackupCrypto.isEncrypted("RFBKP1rest".toByteArray()))
        assertTrue(BackupCrypto.isEncryptedV2("RFBKP2Erest".toByteArray()))
        assertFalse(BackupCrypto.isEncrypted("PK\u0003\u0004".toByteArray()))
        assertFalse(BackupCrypto.isEncryptedV2("PK\u0003\u0004".toByteArray()))
        assertFalse(BackupCrypto.isEncrypted("RFBKP2".toByteArray()))
    }
}

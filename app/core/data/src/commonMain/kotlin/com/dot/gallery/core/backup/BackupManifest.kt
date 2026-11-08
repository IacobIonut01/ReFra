/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/** Describes one streamed section file inside a schema-v3 archive. */
@Serializable
data class SectionDescriptor(
    val file: String = "",
    val count: Int = 0,
    /** Lower-case SHA-256 of the section file bytes. */
    val sha256: String = "",
    val bytes: Long = 0L
)

@Serializable
data class BackupManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersionName: String = "",
    val appVersionCode: Long = 0L,
    val exportedAt: Long = 0L,
    // Legacy inline sections (schema v1/v2). Schema v3 writes these as section files instead.
    val settings: Map<String, SettingValue> = emptyMap(),
    val localFavorites: List<LocalFavoriteEntry> = emptyList(),
    val cloudFavorites: List<CloudFavoriteEntry> = emptyList(),
    val cloudConfigs: List<CloudConfigEntry> = emptyList(),
    val vaults: List<VaultEntry> = emptyList(),
    // Schema v3
    val sections: Map<String, SectionDescriptor> = emptyMap(),
    val vaultCredentials: List<VaultCredentialEntry> = emptyList(),
    val credentialIncluded: Boolean = false,
    /** "none" or "password". Redundant with the outer preview header, kept for manifest-only reads. */
    val encryption: String = ENCRYPTION_NONE
) {
    companion object {
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        const val SCHEMA_VERSION = 3
        const val MANIFEST_NAME = "manifest.json"
        const val VAULTS_DIR = "vaults"
        const val SECTIONS_DIR = "sections"
        const val EDIT_BACKUPS_DIR = "edit_backups"

        const val ENCRYPTION_NONE = "none"
        const val ENCRYPTION_PASSWORD = "password"

        fun requireSupportedSchema(schemaVersion: Int) {
            if (schemaVersion !in MIN_SUPPORTED_SCHEMA_VERSION..SCHEMA_VERSION) {
                throw UnsupportedBackupSchemaException(schemaVersion)
            }
        }

        fun sectionFileName(section: BackupSection): String =
            "$SECTIONS_DIR/${section.name.lowercase()}.jsonl"

        fun supportsSectionFiles(schemaVersion: Int): Boolean = schemaVersion >= 3
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/**
 * Plaintext header written before the archive payload by schema-v3 exports.
 *
 * It carries only counts, versions, and the encryption flag, so the restore wizard can show a
 * preview (and decide whether a password is needed) without decrypting anything. Vault names
 * are only included for unencrypted archives.
 */
@Serializable
data class BackupPreviewHeader(
    val schemaVersion: Int = BackupManifest.SCHEMA_VERSION,
    val encrypted: Boolean = false,
    val appVersionName: String = "",
    val appVersionCode: Long = 0L,
    val exportedAt: Long = 0L,
    val credentialIncluded: Boolean = false,
    /** Section id (`BackupSection.name`) to record count. */
    val sections: Map<String, Int> = emptyMap(),
    val vaults: List<PreviewVault> = emptyList(),
    val payloadBytes: Long = 0L
) {
    fun count(section: BackupSection): Int = sections[section.name] ?: 0

    companion object {
        const val MAGIC = "RFBKP2"
        const val MAGIC_SIZE = 6
    }
}

@Serializable
data class PreviewVault(
    val uuid: String,
    val name: String = "",
    val mediaCount: Int = 0
)

/** What kind of archive a picked file is. */
enum class BackupFormat {
    /** Legacy schema v1/v2 plaintext ZIP. */
    LEGACY_ZIP,
    /** Legacy schema v1/v2 whole-archive encrypted stream. */
    LEGACY_ENCRYPTED,
    /** Schema v3 archive with a plaintext preview header. */
    SECTIONED
}

/**
 * Result of probing a picked archive. [encrypted] is the only signal the UI may use to decide
 * whether to ask for a password; it is derived from the archive bytes, never from the file name
 * or from which sections are present.
 */
data class BackupProbe(
    val format: BackupFormat,
    val encrypted: Boolean,
    val preview: BackupPreviewHeader? = null
) {
    val needsPassword: Boolean get() = encrypted
}

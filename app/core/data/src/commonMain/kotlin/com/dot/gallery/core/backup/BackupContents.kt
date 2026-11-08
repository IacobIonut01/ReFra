/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

/**
 * Summary of what a backup archive contains, produced by inspecting the manifest/preview
 * header only (without restoring anything). Used to drive the import "pick what to restore" step.
 */
data class BackupContents(
    val schemaVersion: Int,
    val appVersionName: String,
    val exportedAt: Long,
    val encrypted: Boolean,
    val settingsCount: Int,
    val localFavoritesCount: Int,
    val cloudFavoritesCount: Int,
    val cloudConfigsCount: Int,
    val vaultCount: Int,
    val vaultMediaCount: Int,
    /** Per-vault summary so the import UI can let the user pick individual vaults. */
    val vaults: List<VaultSummary> = emptyList(),
    /** Counts for every section, including the ones added in schema v3. */
    val sectionCounts: Map<BackupSection, Int> = emptyMap(),
    /** True when the archive carries vault credentials (only possible for encrypted archives). */
    val credentialIncluded: Boolean = false,
    /** 1 = legacy manifest, 2 = legacy manifest with integrity metadata, 3 = sectioned archive. */
    val formatVersion: Int = 2,
    /** Size of the archive payload in bytes, when known. */
    val payloadBytes: Long = 0L
) {
    /** Number of records in [section]. Legacy fields are used when no section map exists. */
    fun count(section: BackupSection): Int = sectionCounts[section] ?: when (section) {
        BackupSection.SETTINGS -> settingsCount
        BackupSection.LOCAL_FAVORITES -> localFavoritesCount
        BackupSection.CLOUD_FAVORITES -> cloudFavoritesCount
        BackupSection.CLOUD_CONFIGS -> cloudConfigsCount
        BackupSection.VAULTS -> vaultCount
        else -> 0
    }

    /** True if the given section has any data in this backup. */
    fun has(section: BackupSection): Boolean = count(section) > 0

    /** True when at least one section has data. */
    val hasAnyData: Boolean get() = BackupSection.entries.any(::has)
}

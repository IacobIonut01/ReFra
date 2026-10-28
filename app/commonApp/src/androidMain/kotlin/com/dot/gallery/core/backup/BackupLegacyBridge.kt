/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import android.net.Uri
import com.dot.gallery.core.backup.ConfigBackupManager.ImportIssue

/**
 * Reuses the v1/v2 restore logic (settings, cloud accounts/favorites, local-favorite matching)
 * from the schema-v3 restorer without duplicating its credential-identity rules.
 */
interface BackupLegacyBridge {
    suspend fun bridgeExportSettings(): Map<String, SettingValue>
    suspend fun bridgeExportLocalFavorites(): List<LocalFavoriteEntry>
    suspend fun bridgeExportCloudFavorites(): List<CloudFavoriteEntry>
    suspend fun bridgeExportCloudConfigs(): List<CloudConfigEntry>

    suspend fun bridgeRestoreSettings(
        settings: Map<String, SettingValue>,
        issues: MutableList<ImportIssue>
    ): BridgeCount

    suspend fun bridgeRestoreCloudConfigs(
        entries: List<CloudConfigEntry>,
        existing: List<com.dot.gallery.data.entity.CloudServerConfigEntity>,
        issues: MutableList<ImportIssue>
    ): BridgeCloudConfigResult

    suspend fun bridgeRestoreCloudFavorites(
        favorites: List<CloudFavoriteEntry>,
        mappings: BackupAccountMappings,
        issues: MutableList<ImportIssue>
    ): BridgeCount

    suspend fun bridgeMatchLocalFavorites(favorites: List<LocalFavoriteEntry>): List<Uri>

    suspend fun bridgeMatchExistingAccounts(
        entries: List<CloudConfigEntry>,
        existing: List<com.dot.gallery.data.entity.CloudServerConfigEntity>
    ): BackupAccountMappings
}

data class BridgeCount(val restored: Int, val skipped: Int)

data class BridgeCloudConfigResult(
    val restored: Int,
    val skipped: Int,
    val reauthenticationRequired: Int,
    val accountMappings: BackupAccountMappings
)

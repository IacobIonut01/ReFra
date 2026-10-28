/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/**
 * Selectable categories of data to include in an export / apply on import.
 *
 * [selectedVaultIds] further narrows the VAULTS section to specific vault UUIDs.
 * `null` means "all vaults" (the default); a non-null set restricts to those ids only.
 *
 * Sections that existed before schema v3 default to included for backward compatibility.
 * The wizard narrows them down; [BackupSelection.mergeDefault] keeps only what the archive contains.
 */
@Serializable
data class BackupSelection(
    val settings: Boolean = true,
    val localFavorites: Boolean = true,
    val cloudFavorites: Boolean = true,
    val cloudConfigs: Boolean = true,
    val vaults: Boolean = true,
    val albums: Boolean = true,
    val collections: Boolean = true,
    val categories: Boolean = true,
    val metadata: Boolean = true,
    val searchHistory: Boolean = true,
    val people: Boolean = true,
    val cloudPrefs: Boolean = true,
    /** Original files kept before an edit. Off by default: they can be very large. */
    val editBackups: Boolean = false,
    val deviceUi: Boolean = true,
    val selectedVaultIds: Set<String>? = null
) {
    /** True if the vault with [uuid] should be included (all vaults when no explicit set). */
    fun isVaultSelected(uuid: String): Boolean = selectedVaultIds?.contains(uuid) ?: true

    fun isOn(section: BackupSection): Boolean = when (section) {
        BackupSection.SETTINGS -> settings
        BackupSection.LOCAL_FAVORITES -> localFavorites
        BackupSection.CLOUD_FAVORITES -> cloudFavorites
        BackupSection.CLOUD_CONFIGS -> cloudConfigs
        BackupSection.VAULTS -> vaults
        BackupSection.ALBUMS -> albums
        BackupSection.COLLECTIONS -> collections
        BackupSection.CATEGORIES -> categories
        BackupSection.METADATA -> metadata
        BackupSection.SEARCH_HISTORY -> searchHistory
        BackupSection.PEOPLE -> people
        BackupSection.CLOUD_PREFS -> cloudPrefs
        BackupSection.EDIT_BACKUPS -> editBackups
        BackupSection.DEVICE_UI -> deviceUi
    }

    fun with(section: BackupSection, value: Boolean): BackupSelection = when (section) {
        BackupSection.SETTINGS -> copy(settings = value)
        BackupSection.LOCAL_FAVORITES -> copy(localFavorites = value)
        BackupSection.CLOUD_FAVORITES -> copy(cloudFavorites = value)
        BackupSection.CLOUD_CONFIGS -> copy(cloudConfigs = value)
        BackupSection.VAULTS -> copy(vaults = value)
        BackupSection.ALBUMS -> copy(albums = value)
        BackupSection.COLLECTIONS -> copy(collections = value)
        BackupSection.CATEGORIES -> copy(categories = value)
        BackupSection.METADATA -> copy(metadata = value)
        BackupSection.SEARCH_HISTORY -> copy(searchHistory = value)
        BackupSection.PEOPLE -> copy(people = value)
        BackupSection.CLOUD_PREFS -> copy(cloudPrefs = value)
        BackupSection.EDIT_BACKUPS -> copy(editBackups = value)
        BackupSection.DEVICE_UI -> copy(deviceUi = value)
    }

    /** Keeps only the sections present in [contents], preserving the current vault narrowing. */
    fun mergeDefault(contents: BackupContents): BackupSelection {
        var result = this
        BackupSection.entries.forEach { section ->
            result = result.with(section, contents.has(section))
        }
        return result
    }
}

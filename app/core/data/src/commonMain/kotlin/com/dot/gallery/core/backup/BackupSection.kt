/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/** The individual data categories handled during a backup / restore, used for per-section progress. */
@Serializable
enum class BackupSection {
    SETTINGS,
    LOCAL_FAVORITES,
    CLOUD_FAVORITES,
    CLOUD_CONFIGS,
    VAULTS,
    ALBUMS,
    COLLECTIONS,
    CATEGORIES,
    METADATA,
    SEARCH_HISTORY,
    PEOPLE,
    CLOUD_PREFS,
    EDIT_BACKUPS,
    DEVICE_UI;

    /** Sections that predate the section-file format and travel inline in the manifest. */
    val isLegacyInline: Boolean
        get() = this == SETTINGS ||
            this == LOCAL_FAVORITES ||
            this == CLOUD_FAVORITES ||
            this == CLOUD_CONFIGS ||
            this == VAULTS
}

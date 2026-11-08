/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui


/**
 * Stable identifiers for every shortcut tile that can appear in the Library
 * shortcuts area. Persistence keys off of [id], so values must never change
 * once shipped (the enum order may change freely — order is persisted
 * separately by the user's layout).
 */
enum class LibraryShortcut(val id: String) {
    TRASH("trash"),
    FAVORITES("favorites"),
    VAULT("vault"),
    IGNORED("ignored"),
    PRIVATE_FOLDER("private_folder"),
    MEMORIES("memories"),
    CLOUD_ARCHIVE("cloud_archive"),
    CLOUD_SHARED_LINKS("cloud_shared_links"),
    CLOUD_BACKUP("cloud_backup"),
    CLOUD_ACCOUNTS("cloud_accounts");

    companion object {
        fun fromId(id: String): LibraryShortcut? = entries.firstOrNull { it.id == id }
    }
}

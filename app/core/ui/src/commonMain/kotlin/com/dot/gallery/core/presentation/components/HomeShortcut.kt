/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components


/** What the configurable Timeline shortcut opens. [storedValue] is what the setting keeps. */
enum class HomeShortcut(val storedValue: String) {
    FAVORITES("favorites"),
    TRASH("trash"),
    VAULT("vault"),
    CAMERA("camera"),
    NONE("none");

    companion object {
        /**
         * The chosen shortcut. Before the slot existed there was only an on/off Favorites
         * button, so an unset slot follows that switch: off means [NONE].
         */
        fun resolve(stored: String, legacyShowFavorites: Boolean): HomeShortcut =
            entries.firstOrNull { it.storedValue == stored }
                ?: if (legacyShowFavorites) FAVORITES else NONE
    }
}

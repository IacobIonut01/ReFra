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

/** Which [HomeShortcut]s this platform can open. [NONE] is always available. */
data class HomeShortcutSupport(
    val favorites: Boolean = true,
    val trash: Boolean = true,
    val vault: Boolean = true,
) {
    fun supports(shortcut: HomeShortcut) = when (shortcut) {
        HomeShortcut.FAVORITES -> favorites
        HomeShortcut.TRASH -> trash
        HomeShortcut.VAULT -> vault
        HomeShortcut.NONE -> true
    }

    /** The slot's choices offered in settings, in order. */
    val choices: List<HomeShortcut> get() = HomeShortcut.entries.filter(::supports)
}

/**
 * Which home shortcuts are shown. [shortcut] is the configurable slot, or null when it is
 * hidden. While a selection is active Select stays in place and turns into its Close button
 * ([selectCloses]), the slot steps aside, and Search takes its place because the dock that
 * holds the search pill is hidden then.
 */
data class HomeShortcutVisibility(
    val select: Boolean,
    val shortcut: HomeShortcut?,
    val search: Boolean,
    val selectCloses: Boolean = false,
)

fun homeShortcutVisibility(
    timelineShortcuts: Boolean,
    selectionActive: Boolean,
    support: HomeShortcutSupport,
    shortcut: HomeShortcut,
    showSelect: Boolean,
) = HomeShortcutVisibility(
    select = timelineShortcuts && showSelect,
    shortcut = shortcut.takeIf {
        timelineShortcuts && !selectionActive && it != HomeShortcut.NONE && support.supports(it)
    },
    search = selectionActive,
    selectCloses = timelineShortcuts && showSelect && selectionActive,
)

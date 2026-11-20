/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui


/**
 * Shortcuts whose tile only exists once live data says so. While one of these is
 * still [pendingShortcuts] the Library remembers whether it was shown last time
 * and reserves its slot with a placeholder, so tiles do not pop in one by one.
 */
val DataBackedShortcuts: Set<LibraryShortcut> = setOf(
    LibraryShortcut.LOCATIONS,
    LibraryShortcut.PEOPLE,
    LibraryShortcut.CATEGORIES,
    LibraryShortcut.CLOUD_ARCHIVE,
    LibraryShortcut.CLOUD_SHARED_LINKS,
    LibraryShortcut.CLOUD_BACKUP,
    LibraryShortcut.CLOUD_ACCOUNTS,
)

/** Data-backed shortcuts whose live data has not settled yet. */
fun pendingShortcuts(state: LibraryState, noClassification: Boolean): Set<LibraryShortcut> = buildSet {
    val s = state.snapshot
    if (s.locations == null) add(LibraryShortcut.LOCATIONS)
    if (!s.peopleLoaded) add(LibraryShortcut.PEOPLE)
    if (s.categories == null && state.aiAvailable && !noClassification) {
        add(LibraryShortcut.CATEGORIES)
    }
    if (!s.cloudLoaded) {
        addAll(
            listOf(
                LibraryShortcut.CLOUD_ARCHIVE,
                LibraryShortcut.CLOUD_SHARED_LINKS,
                LibraryShortcut.CLOUD_BACKUP,
                LibraryShortcut.CLOUD_ACCOUNTS,
            )
        )
    }
}

/** The pending shortcuts the user saw last time, offered again as placeholders. */
fun reservedShortcuts(
    remembered: Set<String>,
    pending: Set<LibraryShortcut>,
): Set<LibraryShortcut> = pending.filterTo(HashSet()) { it.id in remembered }

/**
 * The shortcut ids to remember after this frame: a still-pending tile keeps its
 * old answer, a settled tile is remembered only while it is actually shown, and
 * ids that are not data-backed are never stored.
 */
fun nextRememberedShortcuts(
    remembered: Set<String>,
    shown: Set<LibraryShortcut>,
    pending: Set<LibraryShortcut>,
): Set<String> = DataBackedShortcuts.mapNotNullTo(HashSet()) { shortcut ->
    shortcut.id.takeIf {
        if (shortcut in pending) shortcut.id in remembered else shortcut in shown
    }
}

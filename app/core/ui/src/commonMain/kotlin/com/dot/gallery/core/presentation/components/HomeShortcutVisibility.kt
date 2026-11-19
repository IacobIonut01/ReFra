/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

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

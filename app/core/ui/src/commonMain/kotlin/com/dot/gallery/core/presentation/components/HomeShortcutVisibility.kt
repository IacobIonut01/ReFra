/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

/**
 * Which home shortcuts are shown. While a selection is active Select stays in place and turns
 * into its Close button ([selectCloses]), Favorites steps aside, and Search takes its place
 * because the dock that holds the search pill is hidden then.
 */
data class HomeShortcutVisibility(
    val select: Boolean,
    val favorites: Boolean,
    val search: Boolean,
    val selectCloses: Boolean = false,
)

fun homeShortcutVisibility(
    timelineShortcuts: Boolean,
    selectionActive: Boolean,
    supportsFavorites: Boolean,
    showFavorites: Boolean,
    showSelect: Boolean,
) = HomeShortcutVisibility(
    select = timelineShortcuts && showSelect,
    favorites = timelineShortcuts && supportsFavorites && showFavorites && !selectionActive,
    search = selectionActive,
    selectCloses = timelineShortcuts && showSelect && selectionActive,
)

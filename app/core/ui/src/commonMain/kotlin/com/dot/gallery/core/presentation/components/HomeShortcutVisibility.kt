/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

/**
 * Which home shortcuts are shown. Timeline shortcuts step aside while a selection is active,
 * and Search takes their place because the dock that holds the search pill is hidden then.
 */
data class HomeShortcutVisibility(
    val select: Boolean,
    val favorites: Boolean,
    val search: Boolean,
)

fun homeShortcutVisibility(
    timelineShortcuts: Boolean,
    selectionActive: Boolean,
    supportsFavorites: Boolean,
    showFavorites: Boolean,
    showSelect: Boolean,
) = HomeShortcutVisibility(
    select = timelineShortcuts && showSelect && !selectionActive,
    favorites = timelineShortcuts && supportsFavorites && showFavorites && !selectionActive,
    search = selectionActive,
)

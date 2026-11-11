/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector


/**
 * Fully resolved shortcut ready to render: persisted layout ([pref]) merged
 * with the live data (title/icon/colour/count/route) computed at runtime.
 *
 * [available] reflects whether the underlying feature is currently usable
 * (e.g. cloud connected, private folder configured). Unavailable shortcuts are
 * never rendered, but their layout preference is preserved so they reappear in
 * their previous slot once the feature comes back.
 */
@Immutable
data class LibraryShortcutItem(
    val shortcut: LibraryShortcut,
    val title: String,
    val icon: ImageVector?,
    val contentColor: Color,
    val useIndicator: Boolean,
    val indicatorCounter: Int,
    val route: String,
    val available: Boolean,
    val pref: LibraryShortcutPref
) {
    val span: Int get() = pref.span
    val size: LibraryShortcutSize get() = pref.size
    val visible: Boolean get() = pref.visible
}

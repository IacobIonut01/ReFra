/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components


/**
 * Which [HomeShortcut]s this platform can open. [NONE] is always available; [camera] needs an
 * installed app that opens the still camera.
 */
data class HomeShortcutSupport(
    val favorites: Boolean = true,
    val trash: Boolean = true,
    val vault: Boolean = true,
    val camera: Boolean = true,
) {
    fun supports(shortcut: HomeShortcut) = when (shortcut) {
        HomeShortcut.FAVORITES -> favorites
        HomeShortcut.TRASH -> trash
        HomeShortcut.VAULT -> vault
        HomeShortcut.CAMERA -> camera
        HomeShortcut.NONE -> true
    }

    /** The slot's choices offered in settings, in order. */
    val choices: List<HomeShortcut> get() = HomeShortcut.entries.filter(::supports)
}

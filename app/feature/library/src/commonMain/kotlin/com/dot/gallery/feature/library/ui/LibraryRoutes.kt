/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.runtime.Immutable


/**
 * Plain navigation routes the Library feature links to — supplied by the app
 * wrapper from its Screen.* table so the shared module never imports it.
 */
@Immutable
data class LibraryRoutes(
    /** The Library screen's own route — used as the scroll-to-top key. */
    val library: String,
    val settings: String,
    val locations: String,
    val people: String,
    val categories: String,
    val trash: String,
    val favorites: String,
    val vault: String,
    val ignored: String,
    val privateFolder: String,
    val memories: String,
    val cloudArchive: String,
    val cloudSharedLinks: String,
    val cloudBackup: String,
    val cloudAccounts: String,
)

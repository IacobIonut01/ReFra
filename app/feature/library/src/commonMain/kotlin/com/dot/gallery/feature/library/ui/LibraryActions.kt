/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.runtime.Immutable


/**
 * Callbacks [LibraryHeader] reports to the app shell. Route strings stay
 * app-side and come through [LibraryRoutes].
 */
@Immutable
data class LibraryActions(
    /** Push a plain navigation route (shortcut tiles, section cards). */
    val onNavigate: (route: String) -> Unit,
)

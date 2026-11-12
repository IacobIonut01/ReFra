/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.runtime.Immutable


/** Which of Locations, People and Categories have something to show and are shown as tiles. */
@Immutable
data class LibrarySections(
    val locations: Boolean,
    val people: Boolean,
    val categories: Boolean,
) {
    val count: Int get() = listOf(locations, people, categories).count { it }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.util

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a screen around the album grid, such as Customize library, open the album arrange
 * screen. The grid reports whether the albums can be arranged in [available] and takes a
 * [request] the next time it can.
 */
@Stable
class AlbumArrangeRequest {
    var available by mutableStateOf(false)
        internal set

    var pending by mutableStateOf(false)
        private set

    fun request() {
        pending = true
    }

    internal fun consume() {
        pending = false
    }
}

val LocalAlbumArrangeRequest = staticCompositionLocalOf<AlbumArrangeRequest?> { null }

/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets an active selection move its action bar into the wide-window rail instead of covering
 * the content with a bottom sheet. The selection keeps owning its state and dialogs; it only
 * publishes the bar's content, which the rail composes in place of its destinations.
 */
@Stable
class GalleryRailSelectionHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    private var owner: Any? = null

    fun publish(owner: Any, content: @Composable () -> Unit) {
        this.owner = owner
        this.content = content
    }

    fun withdraw(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            content = null
        }
    }
}

val LocalGalleryRailSelectionHost = staticCompositionLocalOf<GalleryRailSelectionHost?> { null }

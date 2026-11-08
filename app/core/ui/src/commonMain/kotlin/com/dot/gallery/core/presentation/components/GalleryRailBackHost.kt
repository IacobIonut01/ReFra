/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * While the wide-window rail is visible, screens' [NavigationBackButton]s draw nothing and register
 * their action here instead; the rail shows one back button that runs the most recently composed
 * screen's action, so custom back behaviour (`forcedAction`) is preserved.
 *
 * A host created with [acceptsRegistrations] = false only hides back buttons, for content that
 * is shown beside the current destination rather than being it (the settings hub pane).
 */
@Stable
class GalleryRailBackHost(val acceptsRegistrations: Boolean = true) {
    private val actions = mutableStateListOf<State<() -> Unit>>()

    /** The action the rail's back button should run, or null when no screen offers one. */
    val action: (() -> Unit)?
        get() = actions.lastOrNull()?.let { latest -> { latest.value() } }

    fun register(action: State<() -> Unit>) {
        if (acceptsRegistrations) actions += action
    }

    fun unregister(action: State<() -> Unit>) {
        actions.remove(action)
    }
}

val LocalGalleryRailBackHost = staticCompositionLocalOf<GalleryRailBackHost?> { null }

/** Registers [action] with [host] for as long as the calling screen stays composed. */
@Composable
fun RailBackRegistration(host: GalleryRailBackHost, action: () -> Unit) {
    val latest = rememberUpdatedState(action)
    DisposableEffect(host, latest) {
        host.register(latest)
        onDispose { host.unregister(latest) }
    }
}

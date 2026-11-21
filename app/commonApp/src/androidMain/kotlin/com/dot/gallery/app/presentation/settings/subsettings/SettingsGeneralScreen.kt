/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature.settings.ui.SettingsGeneralActions
import com.dot.gallery.feature.settings.ui.SettingsGeneralContent
import com.dot.gallery.feature.settings.ui.SettingsGeneralState
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.core.presentation.util.Screen

/**
 * Android shell for [SettingsGeneralContent]: owns trash navigation, the real launch-screen
 * routes and the settings-search sink.
 */
@Composable
fun SettingsGeneralScreen() {
    val eventHandler = LocalEventHandler.current

    val actions = SettingsGeneralActions(
        onOpenTrash = { eventHandler.navigate(Screen.TrashedScreen()) },
        searchSink = SettingsSearchRegistry::register,
        searchRoute = Screen.SettingsGeneralScreen(),
    )

    SettingsGeneralContent(
        state = SettingsGeneralState(
            supportsTrash = SdkCompat.supportsTrash,
            timelineRoute = Screen.TimelineScreen(),
            libraryRoute = Screen.LibraryScreen(),
        ),
        actions = actions,
    )
}

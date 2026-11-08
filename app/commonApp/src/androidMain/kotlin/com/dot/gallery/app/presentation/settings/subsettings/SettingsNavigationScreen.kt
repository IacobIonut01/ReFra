/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.feature.settings.ui.SettingsNavigationActions
import com.dot.gallery.feature.settings.ui.SettingsNavigationContent
import com.dot.gallery.feature.settings.ui.SettingsNavigationState
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.core.presentation.util.Screen

/**
 * Android shell for [SettingsNavigationContent]: feeds the real `Screen` routes
 * and wires navigation plus the selection-sheet live preview.
 */
@Composable
fun SettingsNavigationScreen() {
    val eventHandler = LocalEventHandler.current

    SettingsNavigationContent(
        state = SettingsNavigationState(
            timelineRoute = Screen.TimelineScreen(),
            libraryRoute = Screen.LibraryScreen(),
        ),
        actions = SettingsNavigationActions(
            onOpenSelectionActions = {
                eventHandler.navigate(Screen.SettingsSelectionActionsScreen())
            },
            searchSink = SettingsSearchRegistry::register,
            searchRoute = Screen.SettingsNavigationScreen(),
        ),
    )
}

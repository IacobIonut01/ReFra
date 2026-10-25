/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.pane

import com.dot.gallery.domain.model.UIEvent
import com.dot.gallery.domain.util.EventHandler

/**
 * Event handler for the settings hub shown beside a settings page: opening another page
 * replaces the visible one instead of stacking it, everything else goes to [base].
 */
internal class SettingsHubEventHandler(
    private val base: EventHandler,
    private val openPage: (route: String) -> Unit,
) : EventHandler by base {
    override fun pushEvent(event: UIEvent) {
        if (event is UIEvent.NavigationRouteEvent) openPage(event.route) else base.pushEvent(event)
    }
}

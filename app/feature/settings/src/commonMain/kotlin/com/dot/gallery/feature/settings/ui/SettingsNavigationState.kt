/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.domain.settings.LAUNCH_ROUTE_LIBRARY
import com.dot.gallery.domain.settings.LAUNCH_ROUTE_TIMELINE

/**
 * Route strings fed by the app shell (`Screen.*()`), so the stored pref values
 * always match real navigation routes.
 */
data class SettingsNavigationState(
    val timelineRoute: String = LAUNCH_ROUTE_TIMELINE,
    val libraryRoute: String = LAUNCH_ROUTE_LIBRARY,
)

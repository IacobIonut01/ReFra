/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

data class SettingsMediaViewerActions(
    val onOpenSlideshow: () -> Unit = {},
    /** Opens the editor for the viewer's bottom quick-action bar. */
    val onOpenViewerActions: () -> Unit = {},
    /** Side effect after tap-navigation is toggled (prompt-shown marker write). */
    val onTapSidesChanged: () -> Unit = {},
    val searchSink: ((String, List<String>) -> Unit)? = null,
    val searchRoute: String? = null,
)

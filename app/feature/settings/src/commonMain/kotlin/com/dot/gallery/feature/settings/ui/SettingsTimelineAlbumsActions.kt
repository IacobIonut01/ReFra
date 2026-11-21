/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

data class SettingsTimelineAlbumsActions(
    val onDateFormatClick: () -> Unit = {},
    val onStoryCardsClick: () -> Unit = {},
    /** Opens the selection-actions editor (`Screen.SettingsSelectionActionsScreen`). */
    val onOpenSelectionActions: () -> Unit = {},
    /** Rebuilds the capture-time index — WorkManager on Android. */
    val onRecheckCaptureDates: () -> Unit = {},
    // Album-section management (AlbumsViewModel ops on Android)
    val onCreateSection: (String) -> Unit = {},
    val onRenameSection: (Long, String) -> Unit = { _, _ -> },
    val onDeleteSection: (Long) -> Unit = {},
    val onReorderSections: (List<Long>) -> Unit = {},
    val onToggleSectionVisibility: (Long, Boolean) -> Unit = { _, _ -> },
    val onEnsureDefaultSections: () -> Unit = {},
    val searchSink: ((String, List<String>) -> Unit)? = null,
    val searchRoute: String? = null,
)

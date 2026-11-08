/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Platform actions for [SettingsSecurityContent] — the SAF folder picker and
 * `PrivateFolderManager` calls stay app-side.
 */
@Stable
class SettingsSecurityActions(
    val onPickPrivateFolder: () -> Unit = {},
    val onClearPrivateFolder: () -> Unit = {},
    val onClearPrivateFolderAndReveal: () -> Unit = {},
    /** Opens the private folder album; null hides the Open button. */
    val onOpenPrivateFolder: (() -> Unit)? = null,
    /**
     * Scans the folder at the given tree URI for its count and size. Only composed while the
     * private folder page is open, so the SAF walk doesn't run for the rest of Security.
     */
    val privateFolderStats: @Composable (treeUri: String) -> PrivateFolderStats? = { null },
)

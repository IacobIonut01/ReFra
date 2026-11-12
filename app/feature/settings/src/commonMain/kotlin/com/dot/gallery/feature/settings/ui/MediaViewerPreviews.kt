/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Live preview slots for the media-viewer settings detail panes. The Android shell
 * supplies the real mocks (haze bars, sample photos, app icons); other platforms or
 * docs scenes get empty slots — the surrounding detail screen still renders.
 */
data class MediaViewerPreviews(
    val fullBrightness: @Composable (Boolean) -> Unit = { FullBrightnessPreview(it) },
    val dateHeader: @Composable (Boolean) -> Unit = {},
    val tapNavigation: @Composable (Boolean) -> Unit = {},
    /** `(currentEditor, editApps)` — icons are resolved by the platform side. */
    val editor: @Composable (String, List<EditAppEntry>) -> Unit = { _, _ -> },
    val smoothing: @Composable (Boolean) -> Unit = {},
    /** `(enabled, icon, position)`. */
    val visualSearch: @Composable (Boolean, ImageVector, String) -> Unit = { _, _, _ -> },
    /** Icon slot for a visual-search provider row/summary — real launcher icon when set. */
    val providerIcon: (@Composable (packageName: String) -> Unit)? = null,
)

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.core.ui.mediaview.VisualSearchTarget

data class SettingsMediaViewerState(
    /** `HdrCapabilities.isHdrDisplay` — disables the HDR toggle on SDR panels. */
    val hdrDisplayCapable: Boolean = true,
    /** `SdkCompat.supportsFavorites` — hides the favorite-button row when false. */
    val supportsFavorites: Boolean = true,
    /** Default `video_surface_rebind` (platform-conditioned: Samsung). */
    val surfaceRebindDefault: Boolean = false,
    val editApps: List<EditAppEntry> = emptyList(),
    val visualSearchTargets: List<VisualSearchTarget> = emptyList(),
    /** Whether the subject cutout model is installed — gates the "Cut out subject" long-press option. */
    val cutoutModelAvailable: Boolean = true,
)

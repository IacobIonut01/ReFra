/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

/**
 * The Editors settings page: installed image and video editor apps the platform
 * discovered, plus the built-in/ReFra choices. Portable stand-ins for
 * `ResolveInfo` — the Android shell resolves package names to launcher icons.
 */
data class SettingsEditorsState(
    val editApps: List<EditAppEntry> = emptyList(),
    val videoEditApps: List<EditAppEntry> = emptyList(),
)

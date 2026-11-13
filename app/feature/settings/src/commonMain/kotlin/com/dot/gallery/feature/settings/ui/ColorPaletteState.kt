/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.domain.settings.ALIAS_REFRA

data class ColorPaletteState(
    /** `Build.VERSION.SDK_INT >= S` — blur only exists on Android 12+. */
    val supportsBlur: Boolean = false,
    /** `BuildConfig.MAPS_ENABLED` — gates the map-appearance section. */
    val mapsEnabled: Boolean = false,
    /** Maps enabled AND no embedded Carto key — shows the manual-key row. */
    val showManualCartoKey: Boolean = false,
    /** Wallpaper-derived palette options (dynamic color); empty when unsupported. */
    val wallpaperPalettes: List<ColorPaletteOption> = emptyList(),
    /** Launcher alias values — owned by the wrapper (applying them is platform work). */
    val appNameAlias: String = ALIAS_REFRA,
    val appLogoAlias: String = ALIAS_REFRA,
)

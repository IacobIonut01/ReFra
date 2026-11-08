/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The colour scheme [GalleryTheme] would build for [themeColorSeed] in the given [darkTheme] and
 * [amoledMode], without changing the app's theme. Settings previews use it to show the light,
 * dark and pure black versions of the user's own palette side by side.
 */
@Composable
fun rememberGalleryColorScheme(
    darkTheme: Boolean,
    amoledMode: Boolean = false,
    themeColorSeed: String = LocalThemePreferences.current.themeColorSeed,
): ColorScheme {
    val dynamicScheme = if (themeColorSeed == THEME_SEED_SYSTEM) {
        platformDynamicColorScheme(darkTheme)
    } else {
        null
    }
    return remember(dynamicScheme, darkTheme, amoledMode, themeColorSeed) {
        val amoled = amoledMode && darkTheme
        when {
            themeColorSeed == THEME_SEED_NEUTRAL -> neutralColorScheme(darkTheme, amoled)
            themeColorSeed != THEME_SEED_SYSTEM -> {
                val seedArgb = themeColorSeed.toLongOrNull(16)?.toInt() ?: seed.value.toInt()
                colorSchemeFromSeed(seedArgb, darkTheme, amoled)
            }
            dynamicScheme != null -> dynamicScheme.maybeAmoled(amoled)
            darkTheme -> DarkColors.maybeAmoled(amoled)
            else -> LightColors
        }
    }
}

/**
 * Draws [content] in the app's palette for [darkTheme] and [amoledMode], keeping the current
 * typography and shapes.
 */
@Composable
fun GalleryPreviewTheme(
    darkTheme: Boolean,
    amoledMode: Boolean = false,
    themeColorSeed: String = LocalThemePreferences.current.themeColorSeed,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = rememberGalleryColorScheme(darkTheme, amoledMode, themeColorSeed),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

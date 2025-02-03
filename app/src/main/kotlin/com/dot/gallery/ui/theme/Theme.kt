/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dot.gallery.core.Settings.Misc.rememberForceTheme
import com.dot.gallery.core.Settings.Misc.rememberIsAmoledMode
import com.dot.gallery.core.Settings.Misc.rememberIsDarkMode

private val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
)


private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    },
    ignoreUserPreference: Boolean = false,
    content: @Composable () -> Unit
) {
    val forceThemeValue by rememberForceTheme()
    val isDarkMode by rememberIsDarkMode()
    val forcedDarkTheme by remember(ignoreUserPreference, forceThemeValue, darkTheme, isDarkMode) {
        mutableStateOf(if (!ignoreUserPreference && forceThemeValue) isDarkMode else darkTheme)
    }
    val isAmoledMode by rememberIsAmoledMode()
    val context = LocalContext.current
    val colorScheme = remember(dynamicColor, forcedDarkTheme, isAmoledMode) {
        if (dynamicColor) {
            maybeDynamicColorScheme(context, forcedDarkTheme, isAmoledMode)
        } else {
            if (forcedDarkTheme) {
                DarkColors.maybeAmoled(isAmoledMode)
            } else {
                LightColors
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(
            value = LocalOverscrollFactory provides null,
            content = content
        )
    }
}

private fun maybeDynamicColorScheme(
    context: Context,
    darkTheme: Boolean,
    isAmoledMode: Boolean
): ColorScheme {
    return if (darkTheme) {
        if (atLeastS) {
            dynamicDarkColorScheme(context).maybeAmoled(isAmoledMode)
        } else {
            DarkColors.maybeAmoled(isAmoledMode)
        }
    } else {
        if (atLeastS) {
            dynamicLightColorScheme(context)
        } else {
            LightColors
        }
    }
}

private val atLeastS: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun ColorScheme.maybeAmoled(boolean: Boolean) = if (boolean) {
    copy(
        surface = Color.Black,
        inverseSurface = Color.White,
        background = Color.Black
    )
} else {
    this
}
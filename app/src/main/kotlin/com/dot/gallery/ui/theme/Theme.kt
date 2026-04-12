/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.ui.theme

import android.annotation.SuppressLint
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberForceTheme
import com.dot.gallery.core.Settings.Misc.rememberIsAmoledMode
import com.dot.gallery.core.Settings.Misc.rememberIsDarkMode
import com.dot.gallery.core.Settings.Misc.rememberThemeColorSeed
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot

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

@Composable
fun isDarkTheme(): Boolean {
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val forceThemeValue by rememberForceTheme()
    val isDarkMode by rememberIsDarkMode()
    return rememberSaveable(isSystemInDarkTheme, forceThemeValue, isDarkMode) {
        if (forceThemeValue) isDarkMode else isSystemInDarkTheme
    }
}

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
    val themeColorSeed by rememberThemeColorSeed()
    val context = LocalContext.current
    val colorScheme = remember(dynamicColor, forcedDarkTheme, isAmoledMode, themeColorSeed) {
        if (themeColorSeed != Settings.Misc.THEME_SEED_SYSTEM) {
            val seedArgb = themeColorSeed.toLongOrNull(16)?.toInt() ?: seed.value.toInt()
            colorSchemeFromSeed(seedArgb, forcedDarkTheme, isAmoledMode)
        } else if (dynamicColor) {
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

@SuppressLint("RestrictedApi")
fun colorSchemeFromSeed(
    seedArgb: Int,
    isDark: Boolean,
    isAmoledMode: Boolean = false
): ColorScheme {
    val hct = Hct.fromInt(seedArgb)
    val scheme = SchemeTonalSpot(hct, isDark, 0.0)
    val p = scheme.primaryPalette
    val s = scheme.secondaryPalette
    val t = scheme.tertiaryPalette
    val n = scheme.neutralPalette
    val nv = scheme.neutralVariantPalette
    val e = scheme.errorPalette
    return if (isDark) {
        darkColorScheme(
            primary = Color(p.tone(80)),
            onPrimary = Color(p.tone(20)),
            primaryContainer = Color(p.tone(30)),
            onPrimaryContainer = Color(p.tone(90)),
            inversePrimary = Color(p.tone(40)),
            secondary = Color(s.tone(80)),
            onSecondary = Color(s.tone(20)),
            secondaryContainer = Color(s.tone(30)),
            onSecondaryContainer = Color(s.tone(90)),
            tertiary = Color(t.tone(80)),
            onTertiary = Color(t.tone(20)),
            tertiaryContainer = Color(t.tone(30)),
            onTertiaryContainer = Color(t.tone(90)),
            background = Color(n.tone(6)),
            onBackground = Color(n.tone(90)),
            surface = Color(n.tone(6)),
            onSurface = Color(n.tone(90)),
            surfaceVariant = Color(nv.tone(30)),
            onSurfaceVariant = Color(nv.tone(80)),
            surfaceTint = Color(p.tone(80)),
            inverseSurface = Color(n.tone(90)),
            inverseOnSurface = Color(n.tone(20)),
            error = Color(e.tone(80)),
            onError = Color(e.tone(20)),
            errorContainer = Color(e.tone(30)),
            onErrorContainer = Color(e.tone(90)),
            outline = Color(nv.tone(60)),
            outlineVariant = Color(nv.tone(30)),
            scrim = Color(n.tone(0)),
            surfaceBright = Color(n.tone(24)),
            surfaceDim = Color(n.tone(6)),
            surfaceContainer = Color(n.tone(12)),
            surfaceContainerHigh = Color(n.tone(17)),
            surfaceContainerHighest = Color(n.tone(22)),
            surfaceContainerLow = Color(n.tone(10)),
            surfaceContainerLowest = Color(n.tone(4)),
            primaryFixed = Color(p.tone(90)),
            primaryFixedDim = Color(p.tone(80)),
            onPrimaryFixed = Color(p.tone(10)),
            onPrimaryFixedVariant = Color(p.tone(30)),
            secondaryFixed = Color(s.tone(90)),
            secondaryFixedDim = Color(s.tone(80)),
            onSecondaryFixed = Color(s.tone(10)),
            onSecondaryFixedVariant = Color(s.tone(30)),
            tertiaryFixed = Color(t.tone(90)),
            tertiaryFixedDim = Color(t.tone(80)),
            onTertiaryFixed = Color(t.tone(10)),
            onTertiaryFixedVariant = Color(t.tone(30)),
        )
    } else {
        lightColorScheme(
            primary = Color(p.tone(40)),
            onPrimary = Color(p.tone(100)),
            primaryContainer = Color(p.tone(90)),
            onPrimaryContainer = Color(p.tone(10)),
            inversePrimary = Color(p.tone(80)),
            secondary = Color(s.tone(40)),
            onSecondary = Color(s.tone(100)),
            secondaryContainer = Color(s.tone(90)),
            onSecondaryContainer = Color(s.tone(10)),
            tertiary = Color(t.tone(40)),
            onTertiary = Color(t.tone(100)),
            tertiaryContainer = Color(t.tone(90)),
            onTertiaryContainer = Color(t.tone(10)),
            background = Color(n.tone(99)),
            onBackground = Color(n.tone(10)),
            surface = Color(n.tone(99)),
            onSurface = Color(n.tone(10)),
            surfaceVariant = Color(nv.tone(90)),
            onSurfaceVariant = Color(nv.tone(30)),
            surfaceTint = Color(p.tone(40)),
            inverseSurface = Color(n.tone(20)),
            inverseOnSurface = Color(n.tone(95)),
            error = Color(e.tone(40)),
            onError = Color(e.tone(100)),
            errorContainer = Color(e.tone(90)),
            onErrorContainer = Color(e.tone(10)),
            outline = Color(nv.tone(50)),
            outlineVariant = Color(nv.tone(80)),
            scrim = Color(n.tone(0)),
            surfaceBright = Color(n.tone(98)),
            surfaceDim = Color(n.tone(87)),
            surfaceContainer = Color(n.tone(94)),
            surfaceContainerHigh = Color(n.tone(92)),
            surfaceContainerHighest = Color(n.tone(90)),
            surfaceContainerLow = Color(n.tone(96)),
            surfaceContainerLowest = Color(n.tone(100)),
            primaryFixed = Color(p.tone(90)),
            primaryFixedDim = Color(p.tone(80)),
            onPrimaryFixed = Color(p.tone(10)),
            onPrimaryFixedVariant = Color(p.tone(30)),
            secondaryFixed = Color(s.tone(90)),
            secondaryFixedDim = Color(s.tone(80)),
            onSecondaryFixed = Color(s.tone(10)),
            onSecondaryFixedVariant = Color(s.tone(30)),
            tertiaryFixed = Color(t.tone(90)),
            tertiaryFixedDim = Color(t.tone(80)),
            onTertiaryFixed = Color(t.tone(10)),
            onTertiaryFixedVariant = Color(t.tone(30)),
        )
    }.maybeAmoled(isAmoledMode && isDark)
}
/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAppLogoAlias
import com.dot.gallery.core.Settings.Misc.rememberAppNameAlias
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature.settings.ui.ColorPaletteContent
import com.dot.gallery.feature.settings.ui.ColorPaletteOption
import com.dot.gallery.feature.settings.ui.ColorPaletteState
import com.dot.gallery.core.presentation.util.shouldShowManualCartoKeySetting
import com.dot.gallery.app.presentation.util.changeAppAlias
import com.dot.gallery.app.presentation.util.restartApplication
import com.dot.gallery.ui.theme.isDarkTheme
import com.dot.gallery.BuildConfig
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dot.gallery.R

/**
 * Android shell for [ColorPaletteContent]: supplies flavor flags, SDK gating and
 * the dynamic (wallpaper) palette options, which only exist on Android 12+, plus
 * the launcher-alias prefs behind the App icon & name page.
 */
@Composable
fun ColorPaletteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isDarkTheme()

    var appNameAlias by rememberAppNameAlias()
    var appLogoAlias by rememberAppLogoAlias()

    // Extract wallpaper color variations from the dynamic color scheme
    val wallpaperPalettes = remember(isDark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicScheme = if (isDark) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            listOf(
                dynamicScheme.primary,
                dynamicScheme.secondary,
                dynamicScheme.tertiary
            ).mapIndexed { index, color ->
                val argb = color.toArgb()
                val hex = String.format("%08X", argb)
                ColorPaletteOption(
                    name = when (index) {
                        0 -> "Primary"
                        1 -> "Secondary"
                        else -> "Tertiary"
                    },
                    seedArgb = argb,
                    hexKey = hex
                )
            }.distinctBy { it.hexKey }
        } else {
            emptyList()
        }
    }

    ColorPaletteContent(
        state = ColorPaletteState(
            supportsBlur = SdkCompat.supportsBlur,
            mapsEnabled = BuildConfig.MAPS_ENABLED,
            showManualCartoKey = shouldShowManualCartoKeySetting(
                mapsEnabled = BuildConfig.MAPS_ENABLED,
                embeddedKey = BuildConfig.CARTO_BASEMAP_KEY,
            ),
            wallpaperPalettes = wallpaperPalettes,
            appNameAlias = appNameAlias,
            appLogoAlias = appLogoAlias,
        ),
        appIcon = { alias -> LauncherIcon(alias) },
        onAppAliasChange = { name, logo ->
            appNameAlias = name
            appLogoAlias = logo
            context.changeAppAlias(name, logo)
            scope.launch {
                delay(300)
                context.restartApplication()
            }
        },
    )
}

/** The round launcher icon of one launcher alias, as the home screen draws it. */
@Composable
private fun LauncherIcon(alias: String) {
    val context = LocalContext.current
    val iconRes = when (alias) {
        Settings.Misc.ALIAS_GALLERY -> R.mipmap.ic_launcher_gallery_round
        Settings.Misc.ALIAS_MONO -> R.mipmap.ic_launcher_mono_round
        Settings.Misc.ALIAS_GALLERY_MONO -> R.mipmap.ic_launcher_gallery_mono_round
        else -> R.mipmap.ic_launcher_round
    }
    Image(
        painter = rememberDrawablePainter(
            drawable = AppCompatResources.getDrawable(context, iconRes)
        ),
        contentDescription = null,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
    )
}

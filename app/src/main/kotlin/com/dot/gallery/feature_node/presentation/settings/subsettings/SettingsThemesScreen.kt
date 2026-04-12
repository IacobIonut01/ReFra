package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.ui.theme.colorSchemeFromSeed
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class ColorPaletteOption(
    val name: String,
    val seedArgb: Int,
    val hexKey: String,
)

internal val presetPalettes = listOf(
    ColorPaletteOption("Blue", 0xFF1673E1.toInt(), "FF1673E1"),
    ColorPaletteOption("Red", 0xFFC62828.toInt(), "FFC62828"),
    ColorPaletteOption("Green", 0xFF2E7D32.toInt(), "FF2E7D32"),
    ColorPaletteOption("Purple", 0xFF7B1FA2.toInt(), "FF7B1FA2"),
    ColorPaletteOption("Orange", 0xFFE65100.toInt(), "FFE65100"),
    ColorPaletteOption("Teal", 0xFF00897B.toInt(), "FF00897B"),
    ColorPaletteOption("Pink", 0xFFD81B60.toInt(), "FFD81B60"),
    ColorPaletteOption("Indigo", 0xFF283593.toInt(), "FF283593"),
    ColorPaletteOption("Amber", 0xFFFFA000.toInt(), "FFFFA000"),
    ColorPaletteOption("Cyan", 0xFF00ACC1.toInt(), "FF00ACC1"),
    ColorPaletteOption("Brown", 0xFF5D4037.toInt(), "FF5D4037"),
    ColorPaletteOption("Lime", 0xFF9E9D24.toInt(), "FF9E9D24"),
)

@Composable
fun SettingsThemesScreen() {
    val eventHandler = LocalEventHandler.current
    var themeColorSeed by Settings.Misc.rememberThemeColorSeed()

    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        var forceTheme by Settings.Misc.rememberForceTheme()
        val forceThemeValuePref = rememberSwitchPreference(
            forceTheme,
            title = stringResource(R.string.settings_follow_system_theme_title),
            isChecked = !forceTheme,
            onCheck = { forceTheme = !it },
            screenPosition = Position.Top
        )
        var darkModeValue by Settings.Misc.rememberIsDarkMode()
        val darkThemePref = rememberSwitchPreference(
            darkModeValue, forceTheme,
            title = stringResource(R.string.settings_dark_mode_title),
            enabled = forceTheme,
            isChecked = darkModeValue,
            onCheck = { darkModeValue = it },
            screenPosition = Position.Middle
        )
        var amoledModeValue by Settings.Misc.rememberIsAmoledMode()
        val amoledModePref = rememberSwitchPreference(
            amoledModeValue,
            title = stringResource(R.string.amoled_mode_title),
            summary = stringResource(R.string.amoled_mode_summary),
            isChecked = amoledModeValue,
            onCheck = { amoledModeValue = it },
            screenPosition = Position.Bottom
        )

        val paletteSummary = if (themeColorSeed == Settings.Misc.THEME_SEED_SYSTEM) {
            stringResource(R.string.color_palette_system)
        } else {
            presetPalettes.find { it.hexKey == themeColorSeed }?.name
                ?: stringResource(R.string.color_palette_custom)
        }
        val paletteTitle = stringResource(R.string.color_palette_title)
        val palettePref = remember(themeColorSeed, paletteTitle, paletteSummary) {
            SettingsEntity.Preference(
                title = paletteTitle,
                summary = paletteSummary,
                icon = Icons.Outlined.Palette,
                onClick = { eventHandler.navigate(Screen.ColorPaletteScreen()) },
                screenPosition = Position.Alone
            )
        }

        return remember(
            palettePref, forceThemeValuePref, darkThemePref, amoledModePref
        ) {
            mutableStateListOf(
                palettePref,
                forceThemeValuePref,
                darkThemePref,
                amoledModePref,
            )
        }
    }

    val isDark = isSystemInDarkTheme()
    val swatchColors = remember(themeColorSeed, isDark) {
        if (themeColorSeed == Settings.Misc.THEME_SEED_SYSTEM) {
            null
        } else {
            val seedArgb = themeColorSeed.toLongOrNull(16)?.toInt() ?: return@remember null
            val scheme = colorSchemeFromSeed(seedArgb, isDark)
            listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer)
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_theme),
        settingsList = settings(),
        settingsBuilder = { item, index ->
            if (index == 0) {
                SettingsItem(
                    item = item,
                    customTrailingContent = {
                        ColorSwatch(swatchColors)
                    }
                )
            } else {
                SettingsItem(item)
            }
        }
    )

}

@Composable
private fun ColorSwatch(colors: List<Color>?) {
    if (colors == null) return
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
    ) {
        Column(modifier = Modifier.matchParentSize()) {
            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(colors[0])
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(colors[1])
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(colors[2])
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(colors[3])
                )
            }
        }
    }
}


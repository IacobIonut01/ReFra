package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference

@Composable
fun SettingsThemesScreen() {
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
        return remember(forceThemeValuePref, darkThemePref, amoledModePref) {
            mutableStateListOf(
                forceThemeValuePref,
                darkThemePref,
                amoledModePref
            )
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_theme),
        settingsList = settings(),
    )
}

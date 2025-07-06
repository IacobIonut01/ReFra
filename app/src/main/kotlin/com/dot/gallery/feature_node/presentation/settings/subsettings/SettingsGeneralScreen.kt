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
fun SettingsGeneralScreen() {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        var trashCanEnabled by Settings.Misc.rememberTrashEnabled()
        val trashCanEnabledPref = rememberSwitchPreference(
            trashCanEnabled,
            title = stringResource(R.string.settings_trash_title),
            summary = stringResource(R.string.settings_trash_summary),
            isChecked = trashCanEnabled,
            onCheck = { trashCanEnabled = it },
            screenPosition = Position.Top
        )
        var secureMode by Settings.Misc.rememberSecureMode()
        val secureModePref = rememberSwitchPreference(
            secureMode,
            title = stringResource(R.string.secure_mode_title),
            summary = stringResource(R.string.secure_mode_summary),
            isChecked = secureMode,
            onCheck = { secureMode = it },
            screenPosition = Position.Middle
        )

        var allowVibrations by Settings.Misc.rememberAllowVibrations()
        val allowVibrationsPref = rememberSwitchPreference(
            allowVibrations,
            title = stringResource(R.string.allow_vibrations),
            summary = stringResource(R.string.allow_vibrations_summary),
            isChecked = allowVibrations,
            onCheck = { allowVibrations = it },
            screenPosition = Position.Bottom
        )

        return remember(trashCanEnabledPref, secureModePref, allowVibrationsPref) {
            mutableStateListOf(
                trashCanEnabledPref,
                secureModePref,
                allowVibrationsPref
            )
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_general),
        settingsList = settings(),
    )
}
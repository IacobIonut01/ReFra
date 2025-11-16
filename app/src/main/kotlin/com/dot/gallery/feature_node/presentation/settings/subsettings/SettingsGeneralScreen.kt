package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberTrashConfirmationEnabled
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference

@Composable
fun SettingsGeneralScreen() {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val res = LocalResources.current

        val trashSectionPref = remember(res) {
            SettingsEntity.Header(
                title = res.getString(R.string.trash)
            )
        }

        var trashCanEnabled by Settings.Misc.rememberTrashEnabled()
        val trashCanEnabledPref = rememberSwitchPreference(
            trashCanEnabled,
            title = stringResource(R.string.settings_trash_title),
            summary = stringResource(R.string.settings_trash_summary),
            isChecked = trashCanEnabled,
            onCheck = { trashCanEnabled = it },
            screenPosition = Position.Top
        )

        var trashConfirmationEnabled by rememberTrashConfirmationEnabled()
        val trashConfirmationEnabledPref = rememberSwitchPreference(
            trashConfirmationEnabled,
            title = stringResource(R.string.settings_trash_confirmation_title),
            summary = stringResource(R.string.settings_trash_confirmation_summary),
            isChecked = trashConfirmationEnabled,
            onCheck = { trashConfirmationEnabled = it },
            screenPosition = Position.Bottom
        )

        val otherSectionPref = remember(res) {
            SettingsEntity.Header(
                title = res.getString(R.string.other)
            )
        }

        var secureMode by Settings.Misc.rememberSecureMode()
        val secureModePref = rememberSwitchPreference(
            secureMode,
            title = stringResource(R.string.secure_mode_title),
            summary = stringResource(R.string.secure_mode_summary),
            isChecked = secureMode,
            onCheck = { secureMode = it },
            screenPosition = Position.Top
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

        return remember(trashCanEnabledPref, trashConfirmationEnabledPref, secureModePref, allowVibrationsPref) {
            mutableStateListOf(
                trashSectionPref,
                trashCanEnabledPref,
                trashConfirmationEnabledPref,
                otherSectionPref,
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
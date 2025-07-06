/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.SettingsAppHeader
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    @Composable
    fun rememberDashboardSettings(): SnapshotStateList<SettingsEntity> {
        val eventHandler = LocalEventHandler.current
        val themePref = rememberPreference(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_theme),
            summary = stringResource(R.string.settings_theme_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsThemeScreen())
            },
            screenPosition = Position.Top
        )
        val generalPref = rememberPreference(
            icon = Icons.Outlined.Dashboard,
            title = stringResource(R.string.settings_general),
            summary = stringResource(R.string.settings_general_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsGeneralScreen())
            },
            screenPosition = Position.Middle
        )
        val customisationPref = rememberPreference(
            icon = Icons.Outlined.DashboardCustomize,
            title = stringResource(R.string.customization),
            summary = stringResource(R.string.customization_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsCustomizationScreen())
            },
            screenPosition = Position.Middle
        )
        val smartPref = rememberPreference(
            icon = Icons.Outlined.SettingsSuggest,
            title = stringResource(R.string.ai_category),
            summary = stringResource(R.string.ai_category_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsSmartFeaturesScreen())
            },
            screenPosition = Position.Bottom
        )
        return remember(themePref, generalPref, customisationPref, smartPref) {
            mutableStateListOf(themePref, generalPref, customisationPref, smartPref)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val backgroundColors = remember(primaryColor, secondaryColor, tertiaryColor, errorColor) {
        listOf(primaryColor, secondaryColor, tertiaryColor, errorColor)
    }
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSecondaryColor = MaterialTheme.colorScheme.onSecondary
    val onTertiaryColor = MaterialTheme.colorScheme.onTertiary
    val onErrorColor = MaterialTheme.colorScheme.onError
    val onBackgroundColors =
        remember(onPrimaryColor, onSecondaryColor, onTertiaryColor, onErrorColor) {
            listOf(onPrimaryColor, onSecondaryColor, onTertiaryColor, onErrorColor)
        }
    BaseSettingsScreen(
        title = stringResource(R.string.settings_title),
        topContent = {
            SettingsAppHeader()
        },
        settingsList = rememberDashboardSettings(),
        settingsBuilder = { setting, index ->
            SettingsItem(
                setting,
                customizeIcon = { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = setting.title,
                        tint = onBackgroundColors[index],
                        modifier = Modifier
                            .background(
                                color = backgroundColors[index],
                                shape = CircleShape
                            )
                            .padding(8.dp)
                    )
                }
            )
        }
    )
}

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.CustomCircleIcon
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
        val appearancePref = rememberPreference(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_appearance),
            summary = stringResource(R.string.settings_appearance_summary),
            onClick = {
                eventHandler.navigate(Screen.ColorPaletteScreen())
            },
            screenPosition = Position.Top
        )
        val timelineAlbumsPref = rememberPreference(
            icon = Icons.Outlined.GridView,
            title = stringResource(R.string.settings_timeline_albums),
            summary = stringResource(R.string.settings_timeline_albums_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsTimelineAlbumsScreen())
            },
            screenPosition = Position.Middle
        )
        val mediaViewerPref = rememberPreference(
            icon = Icons.Outlined.Fullscreen,
            title = stringResource(R.string.settings_media_viewer),
            summary = stringResource(R.string.settings_media_viewer_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsMediaViewerScreen())
            },
            screenPosition = Position.Middle
        )
        val navigationPref = rememberPreference(
            icon = Icons.Outlined.Explore,
            title = stringResource(R.string.settings_navigation),
            summary = stringResource(R.string.settings_navigation_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsNavigationScreen())
            },
            screenPosition = Position.Middle
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
        val smartPref = rememberPreference(
            icon = Icons.Outlined.SettingsSuggest,
            title = stringResource(R.string.ai_category),
            summary = stringResource(R.string.ai_category_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsSmartFeaturesScreen())
            },
            screenPosition = Position.Middle
        )
        val helpPref = rememberPreference(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            title = stringResource(R.string.help_title),
            summary = stringResource(R.string.help_summary),
            onClick = {
                eventHandler.navigate(Screen.HelpScreen())
            },
            screenPosition = Position.Bottom
        )
        return remember(
            appearancePref, timelineAlbumsPref, mediaViewerPref,
            navigationPref, generalPref, smartPref, helpPref
        ) {
            mutableStateListOf(
                appearancePref, timelineAlbumsPref, mediaViewerPref,
                navigationPref, generalPref, smartPref, helpPref
            )
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val backgroundColors = remember(
        primaryColor, secondaryColor, tertiaryColor,
        errorColor, primaryContainerColor, tertiaryContainerColor, surfaceVariantColor
    ) {
        listOf(
            primaryColor, secondaryColor, tertiaryColor,
            errorColor, primaryContainerColor, tertiaryContainerColor, surfaceVariantColor
        )
    }
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSecondaryColor = MaterialTheme.colorScheme.onSecondary
    val onTertiaryColor = MaterialTheme.colorScheme.onTertiary
    val onErrorColor = MaterialTheme.colorScheme.onError
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val onTertiaryContainerColor = MaterialTheme.colorScheme.onTertiaryContainer
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onBackgroundColors = remember(
        onPrimaryColor, onSecondaryColor, onTertiaryColor,
        onErrorColor, onPrimaryContainerColor, onTertiaryContainerColor, onSurfaceVariantColor
    ) {
        listOf(
            onPrimaryColor, onSecondaryColor, onTertiaryColor,
            onErrorColor, onPrimaryContainerColor, onTertiaryContainerColor, onSurfaceVariantColor
        )
    }
    BaseSettingsScreen(
        title = stringResource(R.string.settings_title),
        topContent = {
            SettingsAppHeader()
        },
        settingsList = rememberDashboardSettings(),
        settingsBuilder = { setting, index ->
            SettingsItem(
                item = setting,
                customIcon = { icon, iconUri, iconRes ->
                    CustomCircleIcon(
                        iconVector = icon,
                        iconUri = iconUri,
                        iconRes = iconRes,
                        containerColor = backgroundColors[index],
                        contentColor = onBackgroundColors[index]
                    )
                }
            )
        }
    )
}

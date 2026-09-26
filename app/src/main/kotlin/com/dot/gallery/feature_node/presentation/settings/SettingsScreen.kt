/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.CustomCircleIcon
import com.dot.gallery.feature_node.presentation.settings.components.SettingsAppHeader
import com.dot.gallery.feature_node.presentation.settings.components.SettingsAppHeaderCompact
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    @Composable
    fun rememberDashboardSettings(developerMode: Boolean): SnapshotStateList<SettingsEntity> {
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
        val securityPref = rememberPreference(
            icon = Icons.Outlined.Shield,
            title = stringResource(R.string.settings_security),
            summary = stringResource(R.string.settings_security_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsSecurityScreen())
            },
            screenPosition = Position.Middle
        )
        val backupPref = rememberPreference(
            icon = Icons.Outlined.Backup,
            title = stringResource(R.string.settings_backup),
            summary = stringResource(R.string.settings_backup_summary),
            onClick = {
                eventHandler.navigate(Screen.SettingsBackupScreen())
            },
            screenPosition = Position.Middle
        )
        val hasCloudProviders = remember { ProviderType.hasAnyRemoteProvider() }
        val cloudPref = if (hasCloudProviders) rememberPreference(
            icon = Icons.Outlined.Cloud,
            title = stringResource(R.string.settings_cloud_accounts),
            summary = stringResource(R.string.settings_cloud_accounts_summary),
            onClick = {
                eventHandler.navigate(Screen.CloudAccountsScreen())
            },
            screenPosition = Position.Middle
        ) else null
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
            screenPosition = if (developerMode) Position.Middle else Position.Bottom
        )
        val developerPref = if (developerMode) rememberPreference(
            icon = Icons.Outlined.BugReport,
            title = stringResource(R.string.dev_menu_title),
            summary = stringResource(R.string.dev_menu_summary),
            onClick = {
                eventHandler.navigate(Screen.DeveloperScreen())
            },
            screenPosition = Position.Bottom
        ) else null
        return remember(
            appearancePref, timelineAlbumsPref, mediaViewerPref,
            navigationPref, generalPref, securityPref, backupPref, cloudPref, smartPref, helpPref,
            developerPref
        ) {
            mutableStateListOf<SettingsEntity>(
                appearancePref, timelineAlbumsPref, mediaViewerPref,
                navigationPref, generalPref, securityPref, backupPref,
            ).apply {
                if (cloudPref != null) add(cloudPref)
                addAll(listOf(smartPref, helpPref))
                if (developerPref != null) add(developerPref)
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val inversePrimaryColor = MaterialTheme.colorScheme.inversePrimary
    val backgroundColors = remember(
        primaryColor, secondaryColor, tertiaryColor,
        errorColor, primaryContainerColor, secondaryContainerColor, tertiaryContainerColor, surfaceVariantColor,
        inversePrimaryColor
    ) {
        listOf(
            primaryColor, secondaryColor, inversePrimaryColor, tertiaryColor,
            errorColor, primaryContainerColor, secondaryContainerColor, tertiaryContainerColor, surfaceVariantColor
        )
    }
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSecondaryColor = MaterialTheme.colorScheme.onSecondary
    val onTertiaryColor = MaterialTheme.colorScheme.onTertiary
    val onErrorColor = MaterialTheme.colorScheme.onError
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val onSecondaryContainerColor = MaterialTheme.colorScheme.onSecondaryContainer
    val onTertiaryContainerColor = MaterialTheme.colorScheme.onTertiaryContainer
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onBackgroundColors = remember(
        onPrimaryColor, onSecondaryColor, onTertiaryColor,
        onErrorColor, onPrimaryContainerColor, onSecondaryContainerColor, onTertiaryContainerColor, onSurfaceVariantColor,
        onSurfaceColor
    ) {
        listOf(
            onPrimaryColor, onSecondaryColor, onSurfaceColor, onTertiaryColor,
            onErrorColor, onPrimaryContainerColor, onSecondaryContainerColor, onTertiaryContainerColor, onSurfaceVariantColor
        )
    }
    var bannerDismissed by Settings.Misc.rememberHeaderBannerDismissed()
    var developerMode by Settings.Misc.rememberDeveloperMode()
    val context = LocalContext.current
    val unlockedMessage = stringResource(R.string.dev_unlocked)
    val onDeveloperUnlocked = {
        developerMode = true
        Toast.makeText(context, unlockedMessage, Toast.LENGTH_LONG).show()
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_title),
        topContent = if (!bannerDismissed) {
            {
                SettingsAppHeader(
                    onDismiss = { bannerDismissed = true },
                    developerModeEnabled = developerMode,
                    onDeveloperUnlocked = onDeveloperUnlocked,
                )
            }
        } else null,
        bottomContent = if (bannerDismissed) {
            {
                SettingsAppHeaderCompact(
                    onRestore = { bannerDismissed = false },
                    developerModeEnabled = developerMode,
                    onDeveloperUnlocked = onDeveloperUnlocked,
                )
            }
        } else null,
        settingsList = rememberDashboardSettings(developerMode),
        settingsBuilder = { setting, index, modifier ->
            SettingsItem(
                item = setting,
                modifier = modifier,
                customIcon = { icon, iconUri, iconRes ->
                    CustomCircleIcon(
                        iconVector = icon,
                        iconUri = iconUri,
                        iconRes = iconRes,
                        containerColor = backgroundColors[index % backgroundColors.size],
                        contentColor = onBackgroundColors[index % onBackgroundColors.size]
                    )
                }
            )
        }
    )
}

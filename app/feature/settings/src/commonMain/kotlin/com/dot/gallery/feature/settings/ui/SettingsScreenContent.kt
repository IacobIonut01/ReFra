/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.CustomCircleIcon
import com.dot.gallery.core.ui.components.Footnote
import com.dot.gallery.core.ui.components.SettingsAppHeader
import com.dot.gallery.core.ui.components.SettingsAppHeaderCompact
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.rememberPreference
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ai_category
import com.dot.gallery.feature.settings.generated.resources.ai_category_summary
import com.dot.gallery.feature.settings.generated.resources.dev_menu_summary
import com.dot.gallery.feature.settings.generated.resources.dev_menu_title
import com.dot.gallery.feature.settings.generated.resources.help_summary
import com.dot.gallery.feature.settings.generated.resources.help_title
import com.dot.gallery.feature.settings.generated.resources.settings_appearance
import com.dot.gallery.feature.settings.generated.resources.settings_appearance_summary
import com.dot.gallery.feature.settings.generated.resources.settings_backup
import com.dot.gallery.feature.settings.generated.resources.settings_backup_summary
import com.dot.gallery.feature.settings.generated.resources.settings_cloud_accounts
import com.dot.gallery.feature.settings.generated.resources.settings_cloud_accounts_summary
import com.dot.gallery.feature.settings.generated.resources.settings_general
import com.dot.gallery.feature.settings.generated.resources.settings_general_summary
import com.dot.gallery.feature.settings.generated.resources.settings_media_viewer
import com.dot.gallery.feature.settings.generated.resources.settings_media_viewer_summary
import com.dot.gallery.feature.settings.generated.resources.settings_navigation
import com.dot.gallery.feature.settings.generated.resources.settings_navigation_summary
import com.dot.gallery.feature.settings.generated.resources.settings_root_footnote
import com.dot.gallery.feature.settings.generated.resources.settings_section_about
import com.dot.gallery.feature.settings.generated.resources.settings_section_intelligence
import com.dot.gallery.feature.settings.generated.resources.settings_section_personalize
import com.dot.gallery.feature.settings.generated.resources.settings_section_privacy_data
import com.dot.gallery.feature.settings.generated.resources.settings_security
import com.dot.gallery.feature.settings.generated.resources.settings_security_summary
import com.dot.gallery.feature.settings.generated.resources.settings_timeline_albums
import com.dot.gallery.feature.settings.generated.resources.settings_timeline_albums_summary
import com.dot.gallery.feature.settings.generated.resources.settings_title

/**
 * Settings hub — whole screen, state + callbacks only: the app card, the
 * Personalize, Privacy and data, Intelligence and About groups with coloured
 * circle row icons, then a footnote. The platform wrapper owns preferences ([state]),
 * navigation ([actions]), the support sheet, and the developer-unlock toast.
 */
@Composable
fun SettingsScreenContent(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
    listState: LazyListState = rememberLazyListState(),
    /** The launcher icon the user picked; the app card shows it as its badge. */
    appIcon: Painter? = null,
) {
    @Composable
    fun rememberDashboardSettings(): SnapshotStateList<SettingsEntity> {
        val appearancePref = rememberPreference(
            icon = Icons.Outlined.Palette,
            title = stringResource(Res.string.settings_appearance),
            summary = stringResource(Res.string.settings_appearance_summary),
            onClick = { actions.onNavigate(SettingsRoute.APPEARANCE) },
            screenPosition = Position.Top
        )
        val timelineAlbumsPref = rememberPreference(
            icon = Icons.Outlined.GridView,
            title = stringResource(Res.string.settings_timeline_albums),
            summary = stringResource(Res.string.settings_timeline_albums_summary),
            onClick = { actions.onNavigate(SettingsRoute.TIMELINE_ALBUMS) },
            screenPosition = Position.Middle
        )
        val mediaViewerPref = rememberPreference(
            icon = Icons.Outlined.Fullscreen,
            title = stringResource(Res.string.settings_media_viewer),
            summary = stringResource(Res.string.settings_media_viewer_summary),
            onClick = { actions.onNavigate(SettingsRoute.MEDIA_VIEWER) },
            screenPosition = Position.Middle
        )
        val navigationPref = rememberPreference(
            icon = Icons.Outlined.Explore,
            title = stringResource(Res.string.settings_navigation),
            summary = stringResource(Res.string.settings_navigation_summary),
            onClick = { actions.onNavigate(SettingsRoute.NAVIGATION) },
            screenPosition = Position.Bottom
        )
        val generalPref = rememberPreference(
            icon = Icons.Outlined.Dashboard,
            title = stringResource(Res.string.settings_general),
            summary = stringResource(Res.string.settings_general_summary),
            onClick = { actions.onNavigate(SettingsRoute.GENERAL) },
            screenPosition = Position.Top
        )
        val securityPref = rememberPreference(
            icon = Icons.Outlined.Shield,
            title = stringResource(Res.string.settings_security),
            summary = stringResource(Res.string.settings_security_summary),
            onClick = { actions.onNavigate(SettingsRoute.SECURITY) },
            screenPosition = Position.Middle
        )
        val backupPref = rememberPreference(
            icon = Icons.Outlined.Backup,
            title = stringResource(Res.string.settings_backup),
            summary = stringResource(Res.string.settings_backup_summary),
            onClick = { actions.onNavigate(SettingsRoute.BACKUP) },
            screenPosition = if (state.hasCloudProviders) Position.Middle else Position.Bottom
        )
        val cloudPref = if (state.hasCloudProviders) rememberPreference(
            icon = Icons.Outlined.Cloud,
            title = stringResource(Res.string.settings_cloud_accounts),
            summary = stringResource(Res.string.settings_cloud_accounts_summary),
            onClick = { actions.onNavigate(SettingsRoute.CLOUD_ACCOUNTS) },
            screenPosition = Position.Bottom
        ) else null
        val smartPref = rememberPreference(
            icon = Icons.Outlined.AutoAwesome,
            title = stringResource(Res.string.ai_category),
            summary = stringResource(Res.string.ai_category_summary),
            onClick = { actions.onNavigate(SettingsRoute.SMART_FEATURES) },
            screenPosition = Position.Alone
        )
        val helpPref = rememberPreference(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            title = stringResource(Res.string.help_title),
            summary = stringResource(Res.string.help_summary),
            onClick = { actions.onNavigate(SettingsRoute.HELP) },
            screenPosition = Position.Top
        )
        val developerPref = rememberPreference(
            icon = Icons.Outlined.BugReport,
            title = stringResource(Res.string.dev_menu_title),
            summary = stringResource(Res.string.dev_menu_summary),
            onClick = { actions.onNavigate(SettingsRoute.DEVELOPER) },
            screenPosition = Position.Bottom
        )
        val personalizeHeader = SettingsEntity.Header(stringResource(Res.string.settings_section_personalize))
        val privacyHeader = SettingsEntity.Header(stringResource(Res.string.settings_section_privacy_data))
        val intelligenceHeader = SettingsEntity.Header(stringResource(Res.string.settings_section_intelligence))
        val aboutHeader = SettingsEntity.Header(stringResource(Res.string.settings_section_about))
        return remember(
            appearancePref, timelineAlbumsPref, mediaViewerPref,
            navigationPref, generalPref, securityPref, backupPref, cloudPref, smartPref, helpPref,
            developerPref, personalizeHeader, privacyHeader, intelligenceHeader, aboutHeader,
        ) {
            mutableStateListOf<SettingsEntity>(
                personalizeHeader,
                appearancePref, timelineAlbumsPref, mediaViewerPref, navigationPref,
                privacyHeader,
                generalPref, securityPref, backupPref,
            ).apply {
                if (cloudPref != null) add(cloudPref)
                addAll(listOf(intelligenceHeader, smartPref, aboutHeader, helpPref, developerPref))
            }
        }
    }

    // Coloured circle icons, cycling through the primary, secondary and tertiary containers.
    val colors = MaterialTheme.colorScheme
    val backgroundColors = remember(colors) {
        listOf(colors.primaryContainer, colors.secondaryContainer, colors.tertiaryContainer)
    }
    val onBackgroundColors = remember(colors) {
        listOf(colors.onPrimaryContainer, colors.onSecondaryContainer, colors.onTertiaryContainer)
    }
    val settingsList = rememberDashboardSettings()

    BaseSettingsScreen(
        title = stringResource(Res.string.settings_title),
        topContent = if (!state.bannerDismissed) {
            {
                SettingsAppHeader(
                    appIcon = appIcon,
                    onDismiss = actions.onDismissBanner,
                    onSupportClick = actions.onShowSupport,
                    developerModeEnabled = state.developerMode,
                    onDeveloperUnlocked = actions.onDeveloperUnlocked,
                )
            }
        } else null,
        bottomContent = {
            Column {
                Footnote(
                    icon = Icons.Outlined.Info,
                    text = stringResource(Res.string.settings_root_footnote),
                )
                if (state.bannerDismissed) {
                    SettingsAppHeaderCompact(
                        onRestore = actions.onRestoreBanner,
                        onSupportClick = actions.onShowSupport,
                        developerModeEnabled = state.developerMode,
                        onDeveloperUnlocked = actions.onDeveloperUnlocked,
                    )
                }
            }
        },
        settingsList = settingsList,
        listState = listState,
        settingsBuilder = { setting, index, modifier ->
            // Section headers don't take a colour, so each row keeps the same one with or
            // without the group labels.
            val rowIndex = settingsList.subList(0, index).count { !it.isHeader }
            SettingsItem(
                item = setting,
                modifier = modifier,
                customIcon = { icon, iconUri, iconRes ->
                    CustomCircleIcon(
                        iconVector = icon,
                        iconUri = iconUri,
                        iconRes = iconRes,
                        containerColor = backgroundColors[rowIndex % backgroundColors.size],
                        contentColor = onBackgroundColors[rowIndex % onBackgroundColors.size],
                    )
                },
            )
        },
    )
}

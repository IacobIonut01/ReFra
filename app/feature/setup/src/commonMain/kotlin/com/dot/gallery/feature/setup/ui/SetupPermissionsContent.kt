/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.granted
import com.dot.gallery.feature.setup.generated.resources.setup_allow_access
import com.dot.gallery.feature.setup.generated.resources.setup_back
import com.dot.gallery.feature.setup.generated.resources.setup_continue
import com.dot.gallery.feature.setup.generated.resources.setup_media_access_full
import com.dot.gallery.feature.setup.generated.resources.setup_media_access_limited
import com.dot.gallery.feature.setup.generated.resources.setup_open_app_settings
import com.dot.gallery.feature.setup.generated.resources.setup_perm_files
import com.dot.gallery.feature.setup.generated.resources.setup_perm_files_summary
import com.dot.gallery.feature.setup.generated.resources.setup_perm_internet
import com.dot.gallery.feature.setup.generated.resources.setup_perm_internet_summary
import com.dot.gallery.feature.setup.generated.resources.setup_perm_local_network
import com.dot.gallery.feature.setup.generated.resources.setup_perm_local_network_summary
import com.dot.gallery.feature.setup.generated.resources.setup_perm_location
import com.dot.gallery.feature.setup.generated.resources.setup_perm_location_summary
import com.dot.gallery.feature.setup.generated.resources.setup_perm_manage_media
import com.dot.gallery.feature.setup.generated.resources.setup_perm_manage_media_summary
import com.dot.gallery.feature.setup.generated.resources.setup_perm_media
import com.dot.gallery.feature.setup.generated.resources.setup_perm_media_summary
import com.dot.gallery.feature.setup.generated.resources.setup_perm_notifications
import com.dot.gallery.feature.setup.generated.resources.setup_perm_notifications_summary
import com.dot.gallery.feature.setup.generated.resources.setup_permission_not_granted
import com.dot.gallery.feature.setup.generated.resources.setup_permissions_footnote
import com.dot.gallery.feature.setup.generated.resources.setup_permissions_subtitle
import com.dot.gallery.feature.setup.generated.resources.setup_permissions_title
import com.dot.gallery.feature.setup.generated.resources.setup_step_permissions
import com.dot.gallery.feature.setup.generated.resources.setup_tap_to_allow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Step 1 · Permissions. Photos and videos are required (one row for the media grant); the other
 * permissions are optional rows that ask again, open app settings or launch a system screen.
 */
@Composable
fun SetupPermissionsContent(
    state: SetupPermissionsState,
    actions: SetupPermissionsActions,
    stepNumber: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Images and videos share one media grant, so they show as a single row.
    val rows = state.permissions.filterNot {
        it.permission == SetupPermission.VIDEO &&
            state.permissions.any { other -> other.permission == SetupPermission.IMAGES }
    }
    SetupStepScaffold(
        modifier = modifier,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        stepName = stringResource(Res.string.setup_step_permissions),
        hero = SetupHero(
            icon = Icons.Outlined.Image,
            shape = SetupHeroShape.Permissions.toShape(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        title = stringResource(Res.string.setup_permissions_title),
        subtitle = stringResource(Res.string.setup_permissions_subtitle),
        onBack = onBack,
        bottomBar = {
            SetupBottomActions(
                primaryLabel = stringResource(
                    if (state.coreGranted) Res.string.setup_continue else Res.string.setup_allow_access
                ),
                onPrimary = {
                    if (state.coreGranted) {
                        if (actions.recheckMediaAccess()) onNext()
                    } else {
                        actions.onRequestCorePermissions()
                    }
                },
                secondaryLabel = stringResource(Res.string.setup_back),
                onSecondary = onBack,
            )
        },
    ) {
        SetupGlassPanel {
            rows.forEachIndexed { index, item ->
                PermissionRow(
                    item = item,
                    mediaAccess = state.mediaAccess,
                    position = setupRowPosition(index, rows.size),
                    onRequestCore = actions.onRequestCorePermissions,
                    onGrant = actions.onGrant,
                )
            }
        }
        SetupNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.setup_permissions_footnote))
    }
}

private data class PermissionCopy(val icon: ImageVector, val title: StringResource, val summary: StringResource)

private fun SetupPermission.copy(): PermissionCopy = when (this) {
    SetupPermission.IMAGES, SetupPermission.VIDEO ->
        PermissionCopy(Icons.Outlined.Image, Res.string.setup_perm_media, Res.string.setup_perm_media_summary)
    SetupPermission.MEDIA_LOCATION ->
        PermissionCopy(Icons.Outlined.LocationOn, Res.string.setup_perm_location, Res.string.setup_perm_location_summary)
    SetupPermission.INTERNET ->
        PermissionCopy(Icons.Outlined.Language, Res.string.setup_perm_internet, Res.string.setup_perm_internet_summary)
    SetupPermission.LOCAL_NETWORK ->
        PermissionCopy(Icons.Outlined.Router, Res.string.setup_perm_local_network, Res.string.setup_perm_local_network_summary)
    SetupPermission.NOTIFICATIONS ->
        PermissionCopy(Icons.Outlined.Notifications, Res.string.setup_perm_notifications, Res.string.setup_perm_notifications_summary)
    SetupPermission.MANAGE_MEDIA ->
        PermissionCopy(Icons.Outlined.PermMedia, Res.string.setup_perm_manage_media, Res.string.setup_perm_manage_media_summary)
    SetupPermission.MANAGE_FILES ->
        PermissionCopy(Icons.Outlined.Folder, Res.string.setup_perm_files, Res.string.setup_perm_files_summary)
}

@Composable
private fun PermissionRow(
    item: SetupPermissionUi,
    mediaAccess: SetupMediaAccess,
    position: SetupRowPosition,
    onRequestCore: () -> Unit,
    onGrant: (SetupPermission) -> Unit,
) {
    val copy = item.permission.copy()
    val core = item.permission == SetupPermission.IMAGES || item.permission == SetupPermission.VIDEO
    // Internet is install-time: shown for transparency, nothing to grant.
    val informational = item.permission == SetupPermission.INTERNET
    val granted = if (core) mediaAccess != SetupMediaAccess.NONE else item.granted
    val status = when {
        informational -> null
        core -> stringResource(
            when (mediaAccess) {
                SetupMediaAccess.FULL -> Res.string.setup_media_access_full
                SetupMediaAccess.LIMITED -> Res.string.setup_media_access_limited
                SetupMediaAccess.NONE -> Res.string.setup_permission_not_granted
            }
        )
        granted -> stringResource(Res.string.granted)
        item.requestAttempted -> stringResource(Res.string.setup_open_app_settings)
        else -> stringResource(Res.string.setup_tap_to_allow)
    }
    val onClick: (() -> Unit)? = when {
        informational || (granted && !core) -> null
        core -> if (granted) null else onRequestCore
        else -> { { onGrant(item.permission) } }
    }
    SetupGlassRow(
        title = stringResource(copy.title),
        summary = stringResource(copy.summary),
        icon = copy.icon,
        status = status,
        statusColor = if (core && !granted) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.primary,
        position = position,
        onClick = onClick,
        trailing = when {
            granted -> {
                { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            onClick != null -> {
                {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> null
        },
    )
}

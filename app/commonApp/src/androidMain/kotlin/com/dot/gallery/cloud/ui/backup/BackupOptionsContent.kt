/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.UploadTargetResolver
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.cloud.ui.components.CloudPage
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Videocam
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.action_save
import com.dot.gallery.generated.resources.cloud_backup_album_sync
import com.dot.gallery.generated.resources.cloud_backup_background
import com.dot.gallery.generated.resources.cloud_backup_cellular_photos
import com.dot.gallery.generated.resources.cloud_backup_cellular_photos_summary
import com.dot.gallery.generated.resources.cloud_backup_cellular_videos
import com.dot.gallery.generated.resources.cloud_backup_cellular_videos_summary
import com.dot.gallery.generated.resources.cloud_backup_destination
import com.dot.gallery.generated.resources.cloud_backup_folder_dialog_hint
import com.dot.gallery.generated.resources.cloud_backup_network
import com.dot.gallery.generated.resources.cloud_backup_options
import com.dot.gallery.generated.resources.cloud_backup_require_charging
import com.dot.gallery.generated.resources.cloud_backup_require_charging_summary
import com.dot.gallery.generated.resources.cloud_backup_sync_albums
import com.dot.gallery.generated.resources.cloud_backup_sync_albums_summary
import com.dot.gallery.generated.resources.cloud_backup_upload_folder
import com.dot.gallery.generated.resources.cloud_backup_upload_folder_default
import com.dot.gallery.generated.resources.cloud_backup_upload_folder_summary
import com.dot.gallery.generated.resources.cloud_backup_videos_folder
import com.dot.gallery.generated.resources.cloud_backup_videos_folder_same
import com.dot.gallery.generated.resources.cloud_backup_videos_folder_summary
import com.dot.gallery.generated.resources.cloud_global


/**
 * Stateless backup options for one account (or the global defaults when
 * [global]). [BackupOptionsScreen] loads the config and delegates here; the
 * docs capture tier supplies a sample. Path-based providers get the upload
 * destination rows; content-addressed providers (Immich) do not.
 */
@Composable
fun BackupOptionsContent(
    config: CloudServerConfigEntity,
    global: Boolean,
    onUpdateConfig: (CloudServerConfigEntity.() -> CloudServerConfigEntity) -> Unit,
) {
    val cellularPhotos = config.cellularPhotos
    val cellularVideos = config.cellularVideos
    val requireCharging = config.requireCharging
    val syncAlbums = config.syncAlbums
    // Content-addressed providers (Immich) have no folder semantics; the destination
    // section is only meaningful for path-based stores (WebDAV/ownCloud/Nextcloud/SMB/NFS).
    val supportsUploadPaths = config.providerType != ProviderType.IMMICH
    val uploadBasePath = config.uploadBasePath
    val uploadVideosPath = config.uploadVideosPath
    var editingPath by remember { mutableStateOf<PathField?>(null) }

    val networkHeader = stringResource(Res.string.cloud_backup_network)
    val cellularPhotosTitle = stringResource(Res.string.cloud_backup_cellular_photos)
    val cellularPhotosSummary = stringResource(Res.string.cloud_backup_cellular_photos_summary)
    val cellularVideosTitle = stringResource(Res.string.cloud_backup_cellular_videos)
    val cellularVideosSummary = stringResource(Res.string.cloud_backup_cellular_videos_summary)
    val backgroundHeader = stringResource(Res.string.cloud_backup_background)
    val requireChargingTitle = stringResource(Res.string.cloud_backup_require_charging)
    val requireChargingSummary = stringResource(Res.string.cloud_backup_require_charging_summary)
    val albumSyncHeader = stringResource(Res.string.cloud_backup_album_sync)
    val syncAlbumsTitle = stringResource(Res.string.cloud_backup_sync_albums)
    val syncAlbumsSummary = stringResource(Res.string.cloud_backup_sync_albums_summary)
    val destinationHeader = stringResource(Res.string.cloud_backup_destination)
    val uploadFolderTitle = stringResource(Res.string.cloud_backup_upload_folder)
    val uploadFolderSummary = stringResource(Res.string.cloud_backup_upload_folder_summary)
    val uploadFolderDefault = stringResource(Res.string.cloud_backup_upload_folder_default)
    val videosFolderTitle = stringResource(Res.string.cloud_backup_videos_folder)
    val videosFolderSummary = stringResource(Res.string.cloud_backup_videos_folder_summary)
    val videosFolderSame = stringResource(Res.string.cloud_backup_videos_folder_same)
    val resourceStrings = listOf(
        networkHeader,
        cellularPhotosTitle,
        cellularPhotosSummary,
        cellularVideosTitle,
        cellularVideosSummary,
        backgroundHeader,
        requireChargingTitle,
        requireChargingSummary,
        albumSyncHeader,
        syncAlbumsTitle,
        syncAlbumsSummary,
        destinationHeader,
        uploadFolderTitle,
        uploadFolderSummary,
        uploadFolderDefault,
        videosFolderTitle,
        videosFolderSummary,
        videosFolderSame,
    )

    val settingsList = remember(
        cellularPhotos,
        cellularVideos,
        requireCharging,
        syncAlbums,
        supportsUploadPaths,
        uploadBasePath,
        uploadVideosPath,
        resourceStrings,
    ) {
        buildList {
            // Network section
            add(SettingsEntity.Header(title = networkHeader))
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.SignalCellularAlt,
                    title = cellularPhotosTitle,
                    summary = cellularPhotosSummary,
                    isChecked = cellularPhotos,
                    onCheck = { onUpdateConfig { copy(cellularPhotos = it) } },
                    screenPosition = Position.Top
                )
            )
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.Videocam,
                    title = cellularVideosTitle,
                    summary = cellularVideosSummary,
                    isChecked = cellularVideos,
                    onCheck = { onUpdateConfig { copy(cellularVideos = it) } },
                    screenPosition = Position.Bottom
                )
            )

            // Background section
            add(SettingsEntity.Header(title = backgroundHeader))
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.BatteryChargingFull,
                    title = requireChargingTitle,
                    summary = requireChargingSummary,
                    isChecked = requireCharging,
                    onCheck = { onUpdateConfig { copy(requireCharging = it) } },
                    screenPosition = Position.Alone
                )
            )

            // Albums section
            add(SettingsEntity.Header(title = albumSyncHeader))
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = syncAlbumsTitle,
                    summary = syncAlbumsSummary,
                    isChecked = syncAlbums,
                    onCheck = { onUpdateConfig { copy(syncAlbums = it) } },
                    screenPosition = Position.Alone
                )
            )

            if (supportsUploadPaths) {
                add(SettingsEntity.Header(title = destinationHeader))
                add(
                    SettingsEntity.Preference(
                        icon = Icons.Outlined.Folder,
                        title = uploadFolderTitle,
                        summary = uploadFolderSummary,
                        rightText = uploadBasePath.ifBlank { uploadFolderDefault },
                        onClick = { editingPath = PathField.BASE },
                        screenPosition = Position.Top
                    )
                )
                add(
                    SettingsEntity.Preference(
                        icon = Icons.Outlined.Movie,
                        title = videosFolderTitle,
                        summary = videosFolderSummary,
                        rightText = uploadVideosPath.ifBlank { videosFolderSame },
                        onClick = { editingPath = PathField.VIDEOS },
                        screenPosition = Position.Bottom
                    )
                )
            }
        }.toMutableStateList()
    }

    CloudPage(
        title = stringResource(Res.string.cloud_backup_options),
        count = if (global) stringResource(Res.string.cloud_global)
        else config.displayName.ifBlank { config.providerType.displayName },
    ) {
        items(settingsList, key = { it.title }) { SettingsItem(item = it) }
    }

    editingPath?.let { field ->
        val isVideos = field == PathField.VIDEOS
        var text by remember(field) {
            mutableStateOf(if (isVideos) uploadVideosPath else uploadBasePath)
        }
        AlertDialog(
            onDismissRequest = { editingPath = null },
            title = { Text(if (isVideos) videosFolderTitle else uploadFolderTitle) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(Res.string.cloud_backup_folder_dialog_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val sanitized = UploadTargetResolver.sanitizePath(text)
                    onUpdateConfig {
                        if (isVideos) copy(uploadVideosPath = sanitized) else copy(uploadBasePath = sanitized)
                    }
                    editingPath = null
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingPath = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

private enum class PathField { BASE, VIDEOS }

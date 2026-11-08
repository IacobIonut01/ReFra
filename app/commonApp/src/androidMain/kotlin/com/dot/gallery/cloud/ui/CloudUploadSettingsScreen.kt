/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.dot.gallery.cloud.ui.components.CloudPage
import com.dot.gallery.cloud.ui.components.CloudSectionLabel
import com.dot.gallery.cloud.ui.components.cloudContentWidth
import com.dot.gallery.cloud.ui.components.positionOf
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.ui.components.SettingsItem
import com.github.panpf.sketch.AsyncImage
import org.jetbrains.compose.resources.pluralStringResource
import com.dot.gallery.generated.resources.cloud_upload_albums_summary
import com.dot.gallery.generated.resources.cloud_upload_album_items
import com.dot.gallery.generated.resources.cloud_upload_filter_all
import com.dot.gallery.generated.resources.cloud_upload_filter_off
import com.dot.gallery.generated.resources.cloud_upload_filter_on
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.domain.model.Album
import com.dot.gallery.core.ui.components.RequestInitialSettingsFocus
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.action_save
import com.dot.gallery.generated.resources.cloud_backup_album_syncing
import com.dot.gallery.generated.resources.cloud_backup_folder_dialog_hint
import com.dot.gallery.generated.resources.cloud_dedup_header
import com.dot.gallery.generated.resources.cloud_delete_local_after_upload
import com.dot.gallery.generated.resources.cloud_delete_local_after_upload_summary
import com.dot.gallery.generated.resources.cloud_delete_local_all_confirm
import com.dot.gallery.generated.resources.cloud_destinations_no_albums
import com.dot.gallery.generated.resources.cloud_find_duplicates
import com.dot.gallery.generated.resources.cloud_local_deletion_unavailable
import com.dot.gallery.generated.resources.cloud_upload_album_custom_path_default
import com.dot.gallery.generated.resources.cloud_upload_album_custom_path_summary
import com.dot.gallery.generated.resources.cloud_upload_albums
import com.dot.gallery.generated.resources.cloud_upload_settings_header
import com.dot.gallery.generated.resources.cloud_upload_sync_now
import com.dot.gallery.generated.resources.cloud_upload_syncing


internal fun deleteLocalEnabledForSelection(
    enabledAlbums: Set<Long>,
    deleteLocalPreferences: Map<Long, Boolean>
): Boolean = enabledAlbums.isNotEmpty() && enabledAlbums.all { deleteLocalPreferences[it] == true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudUploadSettingsScreen(
    viewModel: CloudUploadSettingsViewModel = injectViewModel { cloudUploadSettingsViewModel(it) },
    navigateUp: () -> Unit = {}
) {
    val localAlbums by viewModel.localAlbums.collectAsStateWithLifecycle()
    val uploadPrefs by viewModel.uploadPreferences.collectAsStateWithLifecycle()
    val deleteLocalPrefs by viewModel.deleteLocalPreferences.collectAsStateWithLifecycle()
    val accountLabel by viewModel.accountLabel.collectAsStateWithLifecycle()
    val supportsUploadPaths by viewModel.supportsUploadPaths.collectAsStateWithLifecycle()
    val customPaths by viewModel.customPaths.collectAsStateWithLifecycle()

    val uploadRunning by viewModel.uploadWorkRunning.collectAsStateWithLifecycle()

    var showDedupDialog by remember { mutableStateOf(false) }
    var showDeleteLocalConfirm by remember { mutableStateOf(false) }
    var pathEditAlbum by remember { mutableStateOf<Album?>(null) }
    val dedupState by viewModel.dedupState.collectAsStateWithLifecycle()

    val enabledAlbums by remember(uploadPrefs) {
        derivedStateOf { uploadPrefs.filter { it.value }.keys }
    }

    val context = LocalContext.current
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    val visibleAlbums = remember(localAlbums, uploadPrefs, filter) {
        when (filter) {
            FILTER_ON -> localAlbums.filter { uploadPrefs[it.id] == true }
            FILTER_OFF -> localAlbums.filter { uploadPrefs[it.id] != true }
            else -> localAlbums
        }
    }
    val selectedItems = remember(localAlbums, enabledAlbums) {
        localAlbums.filter { it.id in enabledAlbums }.sumOf { it.count }
    }
    val initialFocusRequester = remember { FocusRequester() }
    RequestInitialSettingsFocus(initialFocusRequester, localAlbums.isNotEmpty())

    CloudPage(
        title = stringResource(Res.string.cloud_upload_albums),
        count = accountLabel.ifBlank { null },
        onBack = navigateUp,
        bottomBar = {
            // Pinned action, only once at least one album is selected.
            AnimatedVisibility(
                visible = enabledAlbums.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp)
                ) {
                    Button(
                        onClick = { viewModel.triggerUploadNow() },
                        enabled = !uploadRunning,
                        modifier = Modifier.cloudContentWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (uploadRunning) stringResource(Res.string.cloud_upload_syncing)
                            else stringResource(Res.string.cloud_upload_sync_now)
                        )
                    }
                }
            }
        }
    ) {
        item(key = "filters") {
            Row(
                modifier = Modifier.cloudContentWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    FILTER_ALL to Res.string.cloud_upload_filter_all,
                    FILTER_ON to Res.string.cloud_upload_filter_on,
                    FILTER_OFF to Res.string.cloud_upload_filter_off,
                ).forEach { (value, label) ->
                    GalleryFilterChip(
                        label = stringResource(label),
                        selected = filter == value,
                        onClick = { filter = value },
                    )
                }
            }
        }
        item(key = "selection") {
            Text(
                text = stringResource(
                    Res.string.cloud_upload_albums_summary,
                    enabledAlbums.size,
                    localAlbums.size,
                    selectedItems.toInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.cloudContentWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
            )
        }
        if (visibleAlbums.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(Res.string.cloud_destinations_no_albums),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cloudContentWidth().padding(16.dp)
                )
            }
        }
        itemsIndexed(visibleAlbums, key = { _, album -> "album_${album.id}" }) { index, album ->
            val isSelected = uploadPrefs[album.id] ?: false
            val details = pluralStringResource(
                Res.plurals.cloud_upload_album_items, album.count.toInt(), album.count.toInt()
            ) + " · " + Formatter.formatFileSize(context, album.size)
            val summary = buildAnnotatedString {
                append(details)
                if (isSelected && uploadRunning) {
                    append(" · ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(stringResource(Res.string.cloud_backup_album_syncing))
                    }
                }
            }
            SettingsItem(
                item = SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.PhotoAlbum,
                    title = album.label,
                    summary = summary.text,
                    summaryAnnotated = summary,
                    isChecked = isSelected,
                    onCheck = { viewModel.setAlbumUploadEnabled(album.id, album.label, it) },
                    screenPosition = positionOf(index, visibleAlbums.size),
                ),
                modifier = if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier,
                customIcon = { _, _, _ ->
                    AsyncImage(
                        uri = album.uri.toString(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                },
            )
        }

        // Options for the selected albums
        if (enabledAlbums.isNotEmpty()) {
            val pathAlbums = if (supportsUploadPaths) localAlbums.filter { it.id in enabledAlbums } else emptyList()
            val rows = 1 + pathAlbums.size
            item(key = "options_header") {
                CloudSectionLabel(title = stringResource(Res.string.cloud_upload_settings_header))
            }
            item(key = "delete_local") {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        icon = Icons.Outlined.DeleteSweep,
                        title = stringResource(Res.string.cloud_delete_local_after_upload),
                        summary = stringResource(Res.string.cloud_delete_local_after_upload_summary),
                        isChecked = deleteLocalEnabledForSelection(enabledAlbums, deleteLocalPrefs),
                        onCheck = { enabled ->
                            if (enabled) {
                                // Arms deletion of the user's ORIGINAL files for every selected
                                // album — requires the explicit confirm below, not a stray tap.
                                showDeleteLocalConfirm = true
                            } else {
                                enabledAlbums.forEach { albumId ->
                                    val album = localAlbums.find { it.id == albumId }
                                    if (album != null) {
                                        viewModel.setDeleteLocalEnabled(album.id, album.label, false)
                                    }
                                }
                            }
                        },
                        screenPosition = positionOf(0, rows),
                    )
                )
            }
            // Per-album remote folder overrides (path-based providers only).
            pathAlbums.forEachIndexed { index, album ->
                item(key = "path_${album.id}") {
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            icon = Icons.Outlined.Folder,
                            title = album.label,
                            summary = customPaths[album.id].orEmpty().ifBlank {
                                stringResource(Res.string.cloud_upload_album_custom_path_default)
                            },
                            screenPosition = positionOf(index + 1, rows),
                            onClick = { pathEditAlbum = album },
                        )
                    )
                }
            }
        }

        item(key = "dedup_header") { CloudSectionLabel(title = stringResource(Res.string.cloud_dedup_header)) }
        item(key = "dedup") {
            SettingsItem(
                item = SettingsEntity.Preference(
                    icon = Icons.Outlined.ContentCopy,
                    title = stringResource(Res.string.cloud_find_duplicates),
                    screenPosition = Position.Alone,
                    onClick = { showDedupDialog = true; viewModel.findDuplicates() },
                )
            )
        }
    }

    if (showDeleteLocalConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteLocalConfirm = false },
            title = { Text(stringResource(Res.string.cloud_delete_local_after_upload)) },
            text = { Text(stringResource(Res.string.cloud_delete_local_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteLocalConfirm = false
                        enabledAlbums.forEach { albumId ->
                            val album = localAlbums.find { it.id == albumId }
                            if (album != null) {
                                viewModel.setDeleteLocalEnabled(album.id, album.label, true)
                            }
                        }
                    }
                ) {
                    Text(stringResource(Res.string.cloud_delete_local_after_upload))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLocalConfirm = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    if (showDedupDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!dedupState.isScanning && !dedupState.isDeleting) {
                    showDedupDialog = false
                    viewModel.clearDedupState()
                }
            },
            title = { Text(stringResource(Res.string.cloud_find_duplicates)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(dedupState.message)
                    if (dedupState.isScanning && dedupState.totalCount > 0) {
                        LinearProgressIndicator(
                            progress = { dedupState.scannedCount.toFloat() / dedupState.totalCount },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (dedupState.isScanning || dedupState.isDeleting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (dedupState.duplicates.isNotEmpty() && !dedupState.isDeleting) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.cloud_local_deletion_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (!dedupState.isScanning && !dedupState.isDeleting) {
                    TextButton(onClick = {
                        showDedupDialog = false
                        viewModel.clearDedupState()
                    }) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                }
            }
        )
    }

    pathEditAlbum?.let { album ->
        var text by remember(album.id) {
            mutableStateOf(customPaths[album.id].orEmpty())
        }
        AlertDialog(
            onDismissRequest = { pathEditAlbum = null },
            title = { Text(album.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.cloud_upload_album_custom_path_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(Res.string.cloud_backup_folder_dialog_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAlbumCustomPath(album.id, text)
                    pathEditAlbum = null
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { pathEditAlbum = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

private const val FILTER_ALL = 0
private const val FILTER_ON = 1
private const val FILTER_OFF = 2

/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.presentation.ignored.setup.components.SelectableAlbumItem
import com.dot.gallery.feature_node.presentation.settings.components.RequestInitialSettingsFocus
import com.dot.gallery.feature_node.presentation.settings.components.settingsFocusGroup

internal fun deleteLocalEnabledForSelection(
    enabledAlbums: Set<Long>,
    deleteLocalPreferences: Map<Long, Boolean>
): Boolean = enabledAlbums.isNotEmpty() && enabledAlbums.all { deleteLocalPreferences[it] == true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudUploadSettingsScreen(
    viewModel: CloudUploadSettingsViewModel = hiltViewModel(),
    navigateUp: () -> Unit = {}
) {
    val localAlbums by viewModel.localAlbums.collectAsStateWithLifecycle()
    val uploadPrefs by viewModel.uploadPreferences.collectAsStateWithLifecycle()
    val deleteLocalPrefs by viewModel.deleteLocalPreferences.collectAsStateWithLifecycle()
    val accountLabel by viewModel.accountLabel.collectAsStateWithLifecycle()

    val uploadRunning by viewModel.uploadWorkRunning.collectAsStateWithLifecycle()

    var showDedupDialog by remember { mutableStateOf(false) }
    val dedupState by viewModel.dedupState.collectAsStateWithLifecycle()

    val enabledAlbums by remember(uploadPrefs) {
        derivedStateOf { uploadPrefs.filter { it.value }.keys }
    }

    val albumGridSize by rememberAlbumGridSize()
    val gridCells = remember(albumGridSize) {
        Constants.albumCellsList[albumGridSize]
    }
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val initialFocusRequester = remember { FocusRequester() }
    RequestInitialSettingsFocus(initialFocusRequester, localAlbums.isNotEmpty())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.cloud_upload_albums),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val subtitle = when {
                            accountLabel.isNotBlank() && enabledAlbums.isNotEmpty() ->
                                "$accountLabel · ${enabledAlbums.size} selected"
                            accountLabel.isNotBlank() -> accountLabel
                            enabledAlbums.isNotEmpty() -> "${enabledAlbums.size} albums selected"
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            // Persistent floating action bar — replaces the gigantic mid-screen
            // button; only present once at least one album is selected.
            AnimatedVisibility(
                visible = enabledAlbums.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        SetupButton(
                            text = if (uploadRunning) stringResource(R.string.cloud_upload_syncing)
                            else stringResource(R.string.cloud_upload_sync_now),
                            enabled = !uploadRunning,
                            applyHorizontalPadding = false,
                            applyBottomPadding = false,
                            applyInsets = false,
                            onClick = { viewModel.triggerUploadNow() }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .settingsFocusGroup(),
            columns = gridCells,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Album grid
            if (localAlbums.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.cloud_destinations_no_albums),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                items(
                    items = localAlbums,
                    key = { it.toString() }
                ) { album ->
                    val isSelected = uploadPrefs[album.id] ?: false
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SelectableAlbumItem(
                            album = album,
                            isSelected = isSelected,
                            modifier = if (album == localAlbums.firstOrNull()) {
                                Modifier.focusRequester(initialFocusRequester)
                            } else {
                                Modifier
                            },
                            isDisabled = false,
                            showCheckmark = true,
                            onClick = {
                                viewModel.setAlbumUploadEnabled(album.id, album.label, !isSelected)
                            }
                        )
                        // Non-intrusive status overlay for selected albums: a "New/Queued"
                        // pill at rest, or a live syncing spinner while a backup is running.
                        if (isSelected) {
                            AlbumStatusBadge(
                                syncing = uploadRunning,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Settings + Sync section for enabled albums
            if (enabledAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_upload_settings_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.cloud_delete_local_after_upload),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.cloud_delete_local_after_upload_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = deleteLocalEnabledForSelection(enabledAlbums, deleteLocalPrefs),
                                onCheckedChange = { enabled ->
                                    enabledAlbums.forEach { albumId ->
                                        val album = localAlbums.find { it.id == albumId }
                                        if (album != null) {
                                            viewModel.setDeleteLocalEnabled(album.id, album.label, enabled)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Dedup section
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cloud_dedup_header),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    SetupButton(
                        text = stringResource(R.string.cloud_find_duplicates),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        onClick = { showDedupDialog = true; viewModel.findDuplicates() }
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showDedupDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!dedupState.isScanning && !dedupState.isDeleting) {
                    showDedupDialog = false
                    viewModel.clearDedupState()
                }
            },
            title = { Text(stringResource(R.string.cloud_find_duplicates)) },
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
                            text = stringResource(R.string.cloud_local_deletion_unavailable),
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
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        )
    }
}

/**
 * Compact overlay pill shown on selected album thumbnails. While a backup is
 * running it shows a live spinner ("Syncing"); otherwise it marks the album as
 * queued/new ("Queued").
 */
@Composable
private fun AlbumStatusBadge(
    syncing: Boolean,
    modifier: Modifier = Modifier
) {
    val container = if (syncing) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.tertiary
    val onContainer = if (syncing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onTertiary

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container.copy(alpha = 0.92f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = onContainer
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = if (syncing) stringResource(R.string.cloud_backup_album_syncing)
            else stringResource(R.string.cloud_backup_album_queued),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = onContainer
        )
    }
}

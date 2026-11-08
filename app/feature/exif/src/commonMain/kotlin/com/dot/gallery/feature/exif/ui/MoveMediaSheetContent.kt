/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.SheetActions
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.domain.model.Media
import com.dot.gallery.feature.albums.ui.components.ProviderIconSlot
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.exif.generated.resources.Res
import com.dot.gallery.feature.exif.generated.resources.close
import com.dot.gallery.feature.exif.generated.resources.cloud_move_incomplete
import com.dot.gallery.feature.exif.generated.resources.cloud_move_mixed_unsupported
import com.dot.gallery.feature.exif.generated.resources.cloud_move_removing_originals
import com.dot.gallery.feature.exif.generated.resources.move_to_another_album
import com.dot.gallery.feature.exif.generated.resources.open_album
import com.dot.gallery.feature.exif.generated.resources.open_media
import com.dot.gallery.feature.exif.generated.resources.retry_failed_items
import com.dot.gallery.feature.exif.generated.resources.search
import com.dot.gallery.feature.exif.generated.resources.search_albums

/**
 * Move-to-album sheet: destination picker, transfer progress, completion and
 * failure panels. All platform work (write/delete requests, biometrics,
 * navigation) is delegated to the caller through the callback parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveMediaSheetContent(
    sheetState: AppBottomSheetState,
    media: List<Media>,
    albums: List<Album>,
    albumGroups: List<AlbumGroupWithAlbums>,
    cloudAccounts: Map<Long, CloudDestinationAccount>,
    gridSizeIndex: Int,
    moveSourceStatus: CloudCopyDestinationStatus,
    hasMixedSources: Boolean,
    operationActive: Boolean,
    operationProgress: Float,
    awaitingSourceCleanup: Boolean,
    succeeded: Boolean,
    sourceRetained: Boolean,
    failed: Boolean,
    failureMessage: String,
    canRetry: Boolean,
    destination: Album?,
    destinationLabel: String,
    openTarget: TransferOpenTarget?,
    hasFullStorageAccess: Boolean,
    isDestinationEnabled: (Album) -> Boolean,
    destinationStatus: (Album) -> CloudCopyDestinationStatus?,
    onDestinationSelected: (Album) -> Unit,
    onNewAlbum: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onCloseResult: () -> Unit,
    onDone: () -> Unit,
    onOpen: () -> Unit,
    providerIcon: ProviderIconSlot? = null,
) {
    val scope = rememberCoroutineScope()
    val newAlbumSheetState = rememberAppBottomSheetState()
    var searchQuery by remember { mutableStateOf("") }
    val selectionVisible = !operationActive && !failed && !succeeded

    if (sheetState.isVisible) {
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = onDismissRequest,
            dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shape = ExpressiveSheetShape
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .imePadding()
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SheetHeader(
                    title = stringResource(Res.string.move_to_another_album),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )

                AnimatedVisibility(
                    visible = selectionVisible,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        placeholder = { Text(stringResource(Res.string.search_albums)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(Res.string.search)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                AnimatedVisibility(
                    visible = selectionVisible && moveSourceStatus != CloudCopyDestinationStatus.READY,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    Text(
                        text = if (hasMixedSources) {
                            stringResource(Res.string.cloud_move_mixed_unsupported)
                        } else {
                            stringResource(moveSourceStatus.messageRes())
                        },
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                AnimatedVisibility(
                    visible = operationActive,
                    modifier = Modifier
                        .padding(32.dp)
                        .padding(bottom = 64.dp)
                        .navigationBarsPadding()
                        .align(Alignment.CenterHorizontally),
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    if (destination != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TransferJourneyPreview(
                                media = media,
                                destination = destination,
                                destinationLabel = destinationLabel,
                                mode = TransferSheetMode.MOVE,
                                progress = operationProgress,
                                providerIcon = providerIcon
                            )
                            if (awaitingSourceCleanup) {
                                Text(
                                    text = stringResource(Res.string.cloud_move_removing_originals),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        CircularProgressIndicator(
                            progress = { operationProgress },
                            modifier = Modifier.size(128.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = succeeded,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    TransferCompletionPanel(
                        media = media,
                        destination = destination,
                        destinationLabel = destinationLabel,
                        mode = TransferSheetMode.MOVE,
                        sourceRetained = sourceRetained,
                        openLabel = stringResource(
                            if (openTarget?.type == TransferOpenTargetType.MEDIA) {
                                Res.string.open_media
                            } else {
                                Res.string.open_album
                            }
                        ),
                        onOpen = openTarget?.let { { onOpen() } },
                        onDone = onDone,
                        providerIcon = providerIcon
                    )
                }

                AnimatedVisibility(
                    visible = failed,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SheetHeader(
                            title = stringResource(Res.string.cloud_move_incomplete),
                            body = failureMessage
                        )
                        SheetActions {
                            SheetButton(
                                text = stringResource(Res.string.close),
                                onClick = onCloseResult,
                                style = SheetButtonStyle.Tonal,
                                modifier = Modifier.weight(1f)
                            )
                            if (canRetry) {
                                SheetButton(
                                    text = stringResource(Res.string.retry_failed_items),
                                    onClick = onRetry,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = selectionVisible,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    TransferDestinationPicker(
                        albums = albums,
                        albumGroups = albumGroups,
                        cloudAccounts = cloudAccounts,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        gridSizeIndex = gridSizeIndex,
                        newAlbumEnabled = moveSourceStatus == CloudCopyDestinationStatus.READY,
                        fallbackStatus = moveSourceStatus,
                        isDestinationEnabled = isDestinationEnabled,
                        destinationStatus = destinationStatus,
                        onAlbumClick = onDestinationSelected,
                        onNewAlbumClick = { scope.launch { newAlbumSheetState.show() } },
                        providerIcon = providerIcon
                    )
                }
            }
        }
    }

    AddAlbumSheet(
        sheetState = newAlbumSheetState,
        hasFullStorageAccess = hasFullStorageAccess,
        onFinish = onNewAlbum,
        onCancel = {
            if (newAlbumSheetState.isVisible) {
                scope.launch { newAlbumSheetState.hide() }
            }
        }
    )
}

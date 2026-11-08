/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.ModalBottomSheetProperties
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
import androidx.compose.ui.graphics.StrokeCap
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
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.domain.model.Media
import com.dot.gallery.feature.albums.ui.components.ProviderIconSlot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.exif.generated.resources.Res
import com.dot.gallery.feature.exif.generated.resources.close
import com.dot.gallery.feature.exif.generated.resources.cloud_copy_incomplete
import com.dot.gallery.feature.exif.generated.resources.copy
import com.dot.gallery.feature.exif.generated.resources.open_album
import com.dot.gallery.feature.exif.generated.resources.open_media
import com.dot.gallery.feature.exif.generated.resources.retry_failed_items
import com.dot.gallery.feature.exif.generated.resources.search
import com.dot.gallery.feature.exif.generated.resources.search_albums

/**
 * Copy-to-album sheet: destination picker, transfer progress, completion and
 * failure panels. All platform work (enqueueing copies, biometrics,
 * navigation) is delegated to the caller through the callback parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyMediaSheetContent(
    sheetState: AppBottomSheetState,
    media: List<Media>,
    albums: List<Album>,
    albumGroups: List<AlbumGroupWithAlbums>,
    cloudAccounts: Map<Long, CloudDestinationAccount>,
    gridSizeIndex: Int,
    operationActive: Boolean,
    operationProgress: Float,
    operationFinished: Boolean,
    journeyVisible: Boolean,
    succeeded: Boolean,
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
    onRetry: () -> Unit,
    onCloseResult: () -> Unit,
    onDone: () -> Unit,
    onOpen: () -> Unit,
    providerIcon: ProviderIconSlot? = null,
    destinationGridState: LazyGridState = rememberLazyGridState(),
) {
    val scope = rememberCoroutineScope()
    val newAlbumSheetState = rememberAppBottomSheetState()
    var searchQuery by remember { mutableStateOf("") }

    AnimatedVisibility(
        visible = sheetState.isVisible,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        val shouldDismiss by rememberedDerivedState(operationActive) {
            !operationActive
        }
        val properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = shouldDismiss
        )
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                when {
                    operationActive -> scope.launch { sheetState.show() }
                    operationFinished -> onDone()
                    else -> scope.launch { sheetState.hide() }
                }
            },
            properties = properties,
            dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shape = ExpressiveSheetShape,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .imePadding()
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = !succeeded,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    SheetHeader(
                        title = stringResource(Res.string.copy),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    )
                }

                AnimatedVisibility(
                    visible = !operationActive && !operationFinished,
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
                    visible = operationActive,
                    modifier = Modifier
                        .padding(32.dp)
                        .align(Alignment.CenterHorizontally),
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    if (journeyVisible) {
                        TransferJourneyPreview(
                            media = media,
                            destination = destination,
                            destinationLabel = destinationLabel,
                            mode = TransferSheetMode.COPY,
                            progress = operationProgress,
                            providerIcon = providerIcon
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { operationProgress },
                                strokeWidth = 4.dp,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.size(128.dp),
                            )
                            Text(text = "${(operationProgress * 100).roundToInt()}%")
                        }
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
                        mode = TransferSheetMode.COPY,
                        sourceRetained = false,
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
                            title = stringResource(Res.string.cloud_copy_incomplete),
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
                    visible = !operationActive && !operationFinished,
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
                        newAlbumEnabled = true,
                        fallbackStatus = null,
                        isDestinationEnabled = isDestinationEnabled,
                        destinationStatus = destinationStatus,
                        onAlbumClick = onDestinationSelected,
                        onNewAlbumClick = { scope.launch { newAlbumSheetState.show() } },
                        providerIcon = providerIcon,
                        gridState = destinationGridState
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

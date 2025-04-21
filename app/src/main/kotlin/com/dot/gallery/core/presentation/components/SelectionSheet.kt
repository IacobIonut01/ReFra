/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Settings.Misc.rememberTrashEnabled
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.use_case.MediaHandleUseCase
import com.dot.gallery.feature_node.presentation.exif.CopyMediaSheet
import com.dot.gallery.feature_node.presentation.exif.MoveMediaSheet
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialog
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.shareMedia
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun <T: Media> SelectionSheet(
    modifier: Modifier = Modifier,
    selectedMedia: SnapshotStateList<T>,
    selectionState: MutableState<Boolean>,
    albumsState: State<AlbumState>,
    handler: MediaHandleUseCase
) {
    fun clearSelection() {
        selectedMedia.clear()
        selectionState.value = false
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shouldMoveToTrash by rememberSaveable { mutableStateOf(true) }
    val trashSheetState = rememberAppBottomSheetState()
    val moveSheetState = rememberAppBottomSheetState()
    val copySheetState = rememberAppBottomSheetState()
    val result = rememberActivityResult(
        onResultOk = {
            clearSelection()
            if (trashSheetState.isVisible) {
                scope.launch {
                    trashSheetState.hide()
                    shouldMoveToTrash = true
                }
            }
        }
    )
    val windowSizeClass = calculateWindowSizeClass(LocalActivity.current as Activity)
    val tabletMode = remember(windowSizeClass) {
        windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact
    }
    val sizeModifier = remember(tabletMode) {
        if (!tabletMode) Modifier.fillMaxWidth()
        else Modifier.wrapContentWidth()
    }
    AnimatedVisibility(
        modifier = modifier,
        visible = selectionState.value,
        enter = slideInVertically { it * 2 },
        exit = slideOutVertically { it * 2 }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .then(sizeModifier)
                .wrapContentHeight()
                .clip(Shapes.extraLarge)
                .shadow(
                    elevation = 4.dp,
                    shape = Shapes.extraLarge
                )
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .then(sizeModifier)
                    .height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = ::clearSelection,
                    modifier = Modifier.size(24.dp),
                ) {
                    Image(
                        imageVector = Icons.Outlined.Close,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        contentDescription = stringResource(R.string.selection_dialog_close_cd)
                    )
                }
                Text(
                    text = stringResource(R.string.selected_s, selectedMedia.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Row(
                modifier = Modifier
                    .then(sizeModifier)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = Shapes.large
                    )
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Share Component
                SelectionBarColumn(
                    imageVector = Icons.Outlined.Share,
                    tabletMode = tabletMode,
                    title = stringResource(R.string.share)
                ) {
                    context.shareMedia(selectedMedia)
                }
                // Favorite Component
                SelectionBarColumn(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    tabletMode = tabletMode,
                    title = stringResource(R.string.favorite)
                ) {
                    scope.launch {
                        handler.toggleFavorite(result = result, selectedMedia)
                    }
                }
                // Copy Component
                SelectionBarColumn(
                    imageVector = Icons.Outlined.CopyAll,
                    tabletMode = tabletMode,
                    title = stringResource(R.string.copy)
                ) {
                    scope.launch {
                        copySheetState.show()
                    }
                }
                // Move Component
                SelectionBarColumn(
                    imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                    tabletMode = tabletMode,
                    title = stringResource(R.string.move)
                ) {
                    scope.launch {
                        moveSheetState.show()
                    }
                }
                // Trash Component
                val trashEnabled = rememberTrashEnabled()
                val trashEnabledRes = remember(trashEnabled) {
                    if (trashEnabled.value) R.string.trash else R.string.trash_delete
                }
                SelectionBarColumn(
                    imageVector = Icons.Outlined.DeleteOutline,
                    tabletMode = tabletMode,
                    title = stringResource(id = trashEnabledRes),
                    onItemLongClick = {
                        scope.launch {
                            shouldMoveToTrash = false
                            trashSheetState.show()
                        }
                    },
                    onItemClick = {
                        scope.launch {
                            shouldMoveToTrash = true
                            trashSheetState.show()
                        }
                    }
                )
            }
        }
    }

    if (albumsState.value.albums.isNotEmpty()) {
        MoveMediaSheet(
            sheetState = moveSheetState,
            mediaList = selectedMedia,
            albumState = albumsState,
            handler = handler,
            onFinish = ::clearSelection
        )

        CopyMediaSheet(
            sheetState = copySheetState,
            mediaList = selectedMedia,
            albumsState = albumsState,
            handler = handler,
            onFinish = ::clearSelection
        )
    }

    TrashDialog(
        appBottomSheetState = trashSheetState,
        data = selectedMedia,
        action = remember(shouldMoveToTrash) {
            if (shouldMoveToTrash) TrashDialogAction.TRASH else TrashDialogAction.DELETE
        },
    ) {
        if (shouldMoveToTrash) {
            handler.trashMedia(result, it, true)
        } else {
            handler.deleteMedia(result, it)
        }
    }
}

@Composable
private fun RowScope.SelectionBarColumn(
    imageVector: ImageVector,
    title: String,
    tabletMode: Boolean,
    onItemLongClick: (() -> Unit)? = null,
    onItemClick: () -> Unit,
) {
    val tintColor = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .defaultMinSize(minHeight = 80.dp)
            .then(
                if (tabletMode) Modifier.defaultMinSize(minWidth = 80.dp)
                else Modifier.weight(1f)
            )
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(tintColor),
            contentDescription = title,
            modifier = Modifier
                .height(32.dp)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = title,
            modifier = Modifier,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            color = tintColor,
            textAlign = TextAlign.Center
        )
    }
}
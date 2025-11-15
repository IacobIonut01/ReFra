/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberShowSelectionTitles
import com.dot.gallery.core.Settings.Misc.rememberTrashEnabled
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.exif.CopyMediaSheet
import com.dot.gallery.feature_node.presentation.exif.MoveMediaSheet
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialog
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.shareMedia
import com.dot.gallery.feature_node.presentation.util.shareMediaWithVaultSupport
import com.dot.gallery.ui.theme.Shapes
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun <T : Media> BoxScope.SelectionSheet(
    modifier: Modifier = Modifier,
    allMedia: MediaState<T>,
    selectedMedia: SnapshotStateList<T>
) {
    val albumsState = LocalMediaDistributor.current.albumsFlow.collectAsStateWithLifecycle()
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()

    val handler = LocalMediaHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shouldMoveToTrash by rememberSaveable { mutableStateOf(true) }
    val trashSheetState = rememberAppBottomSheetState()
    val moveSheetState = rememberAppBottomSheetState()
    val copySheetState = rememberAppBottomSheetState()
    val result = rememberActivityResult(
        onResultOk = {
            selector.clearSelection()
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
        visible = isSelectionActive,
        enter = slideInVertically { it * 2 },
        exit = slideOutVertically { it * 2 }
    ) {
        val allowBlur by rememberAllowBlur()
        val surfaceColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        val backgroundModifier = remember(allowBlur) {
            if (!allowBlur) {
                Modifier.background(
                    color = surfaceColor,
                    shape = Shapes.extraLarge
                )
            } else {
                Modifier
            }
        }
        val shape = Shapes.extraLarge
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .navigationBarsPadding()
                .then(sizeModifier)
                .wrapContentHeight()
                .clip(shape)
                .padding(vertical = 16.dp)
                .align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SelectionAddon(
                    onClick = {
                        scope.launch {
                            selector.clearSelection()
                        }
                    },
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.selection_dialog_close_cd),
                    text =  selectedMedia.size.toString()
                )

                SelectAllAddon(
                    allMedia = allMedia
                )

                /*SelectionAddon(
                    onClick = {

                    },
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add actions"
                )*/
            }
            Row(
                modifier = Modifier
                    .then(sizeModifier)
                    .then(backgroundModifier)
                    .clip(shape)
                    .shadow(
                        elevation = 4.dp,
                        shape = shape
                    )
                    .hazeEffect(
                        state = LocalHazeState.current,
                        style = HazeMaterials.regular(
                            containerColor = surfaceColor
                        )
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
                    scope.launch {
                        // Use enhanced sharing that handles encrypted media if vault context available
                        context.shareMediaWithVaultSupport(selectedMedia, currentVault = null)
                    }
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
            onFinish = selector::clearSelection
        )

        CopyMediaSheet(
            sheetState = copySheetState,
            mediaList = selectedMedia,
            albumsState = albumsState,
            onFinish = selector::clearSelection
        )
    }

    TrashDialog(
        appBottomSheetState = trashSheetState,
        data = selectedMedia,
        action = remember(shouldMoveToTrash) {
            if (shouldMoveToTrash) TrashDialogAction.TRASH else TrashDialogAction.DELETE
        },
    ) {
        selector.clearSelection()
        if (shouldMoveToTrash) {
            handler.trashMedia(result, it, true)
        } else {
            handler.deleteMedia(result, it)
        }
    }
}

@Composable
fun <T: Media> SelectAllAddon(
    allMedia: MediaState<T>,
) {
    val scope = rememberCoroutineScope()
    val selector = LocalMediaSelector.current
    val selectedMedia by selector.selectedMedia.collectAsStateWithLifecycle()
    val selectedAll by rememberedDerivedState(selectedMedia, allMedia) {
        selectedMedia.size == allMedia.media.size && allMedia.media.isNotEmpty()
    }
    val selectAllText = if (selectedAll) {
        stringResource(R.string.clear_selection)
    } else {
        stringResource(R.string.select_all)
    }
    val selectAllContentDesc = if (selectedAll) {
        stringResource(R.string.clear_selection)
    } else {
        stringResource(R.string.select_all)
    }

    val selectAllIcon = if (selectedAll) {
        Icons.Outlined.Deselect
    } else {
        Icons.Outlined.SelectAll
    }

    val selectAllContainerColor by animateColorAsState(
        targetValue = if (selectedAll) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    )
    val selectAllContentColor by animateColorAsState(
        targetValue = if (selectedAll) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    )

    SelectionAddon(
        onClick = {
            scope.launch {
                if (selectedAll) selector.clearSelection()
                else selector.addToSelection(allMedia.media.map { it.id })
            }
        },
        text = selectAllText,
        imageVector = selectAllIcon,
        contentDescription = selectAllContentDesc,
        contentColor = selectAllContentColor,
        containerColor = selectAllContainerColor
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun SelectionAddon(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
    text: String? = null,
) {
    val allowBlur by rememberAllowBlur()
    val backgroundModifier = remember(allowBlur) {
        if (!allowBlur) {
            Modifier.background(
                color = containerColor,
                shape = Shapes.extraLarge
            )
        } else {
            Modifier
        }
    }
    val shape = Shapes.extraLarge
    Row(
        modifier = modifier
            .then(backgroundModifier)
            .clip(shape)
            .shadow(
                elevation = 4.dp,
                shape = shape
            )
            .hazeEffect(
                state = LocalHazeState.current,
                style = HazeMaterials.thin(
                    containerColor = containerColor
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(contentColor),
            contentDescription = contentDescription
        )
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                modifier = Modifier.weight(1f, fill = false)
            )
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
    val showTitles by rememberShowSelectionTitles()
    val tintColor = MaterialTheme.colorScheme.onSurface
    val minHeightSizeModifier = remember(showTitles) {
        if (showTitles) Modifier.defaultMinSize(minHeight = 80.dp)
        else Modifier.defaultMinSize(minHeight = 64.dp)
    }
    val minWidthSizeModifier = remember(tabletMode) {
        if (showTitles) {
            if (tabletMode) Modifier.defaultMinSize(minWidth = 80.dp)
            else Modifier.weight(1f)
        } else {
            if (tabletMode) Modifier.defaultMinSize(minWidth = 64.dp)
            else Modifier.weight(1f)
        }
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(minHeightSizeModifier)
            .then(minWidthSizeModifier)
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
        if (showTitles) {
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
}
/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.trashed.components

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.presentation.components.SelectAllAddon
import com.dot.gallery.core.presentation.components.SelectionAddon
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberIsMediaManager
import com.dot.gallery.ui.theme.Shapes
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun <T : Media> BoxScope.TrashSelectionSheet(
    modifier: Modifier = Modifier,
    allMedia: MediaState<T>,
    selectedMedia: SnapshotStateList<T>,
) {
    val selector = LocalMediaSelector.current
    val handler = LocalMediaHandler.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isMediaManager = rememberIsMediaManager()
    val deleteSheetState = rememberAppBottomSheetState()
    val restoreSheetState = rememberAppBottomSheetState()
    val result = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = {
            if (it.resultCode == Activity.RESULT_OK) {
                selector.clearSelection()
                if (isMediaManager) {
                    scope.launch {
                        deleteSheetState.hide()
                        restoreSheetState.hide()
                    }
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
            // Top row: Close + Select All
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    text = selectedMedia.size.toString()
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
                ) {
                    SelectAllAddon(allMedia = allMedia)
                }
            }
            // Bottom row: Restore + Delete
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
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SelectionBarItem(
                    imageVector = Icons.Outlined.RestoreFromTrash,
                    title = stringResource(R.string.trash_restore),
                    tabletMode = tabletMode,
                    onItemClick = {
                        scope.launch {
                            if (isMediaManager) {
                                restoreSheetState.show()
                            } else {
                                handler.trashMedia(result, selectedMedia, false)
                            }
                        }
                    }
                )
                SelectionBarItem(
                    imageVector = Icons.Outlined.DeleteOutline,
                    title = stringResource(R.string.trash_delete),
                    tabletMode = tabletMode,
                    onItemClick = {
                        scope.launch {
                            if (isMediaManager) {
                                deleteSheetState.show()
                            } else {
                                handler.deleteMedia(result, selectedMedia)
                            }
                        }
                    }
                )
            }
        }
    }

    TrashDialog(
        appBottomSheetState = deleteSheetState,
        data = selectedMedia,
        action = TrashDialogAction.DELETE
    ) {
        handler.deleteMedia(result, it)
    }
    TrashDialog(
        appBottomSheetState = restoreSheetState,
        data = selectedMedia,
        action = TrashDialogAction.RESTORE
    ) {
        handler.trashMedia(result, it, false)
    }
}

@Composable
private fun RowScope.SelectionBarItem(
    imageVector: ImageVector,
    title: String,
    tabletMode: Boolean,
    onItemClick: () -> Unit,
) {
    val showTitles by Settings.Misc.rememberShowSelectionTitles()
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
            .clickable(onClick = onItemClick)
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(tintColor),
            contentDescription = title,
            modifier = Modifier.height(32.dp)
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

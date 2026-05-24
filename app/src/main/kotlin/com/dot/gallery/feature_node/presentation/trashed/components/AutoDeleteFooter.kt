/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.trashed.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberIsMediaManager
import kotlinx.coroutines.launch

@Composable
fun <T: Media> AutoDeleteFooter(
    mediaState: State<MediaState<T>>,
) {
    val handler = LocalMediaHandler.current
    val selector = LocalMediaSelector.current
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            visible = mediaState.value.media.isNotEmpty(),
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SetupButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            if (isMediaManager) {
                                restoreSheetState.show()
                            } else {
                                handler.trashMedia(
                                    result,
                                    mediaState.value.media,
                                    false
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    text = stringResource(R.string.trash_restore_all)
                )
                SetupButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            if (isMediaManager) {
                                deleteSheetState.show()
                            } else {
                                handler.deleteMedia(result, mediaState.value.media)
                            }
                        }
                    },
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    text = stringResource(R.string.trash_delete_all)
                )
            }
        }

        SettingsItem(
            item = SettingsEntity.Preference(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.trash),
                summary = stringResource(R.string.trash_deletion_warning),
                screenPosition = Position.Alone,
            ),
            applyPaddings = false,
        )
    }

    TrashDialog(
        appBottomSheetState = deleteSheetState,
        data = mediaState.value.media,
        action = TrashDialogAction.DELETE
    ) {
        handler.deleteMedia(result, it)
    }
    TrashDialog(
        appBottomSheetState = restoreSheetState,
        data = mediaState.value.media,
        action = TrashDialogAction.RESTORE
    ) {
        handler.trashMedia(result, it, false)
    }
}
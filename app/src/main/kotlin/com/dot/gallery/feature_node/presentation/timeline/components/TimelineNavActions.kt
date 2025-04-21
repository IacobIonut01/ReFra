/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("UNCHECKED_CAST")

package com.dot.gallery.feature_node.presentation.timeline.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.use_case.MediaHandleUseCase
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionSheet
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.clear
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import kotlinx.coroutines.launch

@Composable
inline fun <reified T: Media> TimelineNavActions(
    albumId: Long,
    handler: MediaHandleUseCase,
    expandedDropDown: MutableState<Boolean>,
    mediaState: State<MediaState<T>>,
    selectedMedia: MutableState<Set<Long>>,
    selectionState: MutableState<Boolean>,
    crossinline navigate: @DisallowComposableCalls (route: String) -> Unit,
    crossinline navigateUp: @DisallowComposableCalls () -> Unit
) {
    val scope = rememberCoroutineScope()
    val result = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = {
            if (it.resultCode == Activity.RESULT_OK) {
                selectedMedia.clear()
                selectionState.value = false
            }
        }
    )
    val context = LocalContext.current
    val appBottomSheetState = rememberAppBottomSheetState()
    LaunchedEffect(appBottomSheetState.isVisible, expandedDropDown.value) {
        scope.launch {
            if (expandedDropDown.value) appBottomSheetState.show()
            else appBottomSheetState.hide()
        }
    }
    val optionList = remember(selectionState.value) {
        mutableListOf(
            OptionItem(
                text = if (selectionState.value)
                    context.getString(R.string.unselect_all)
                else
                    context.getString(R.string.select_all),
                onClick = {
                    selectionState.value = !selectionState.value
                    if (selectionState.value)
                        selectedMedia.value.plus(mediaState.value.media.map { it.id })
                    else
                        selectedMedia.clear()
                    expandedDropDown.value = false
                }
            )
        ).apply {
            if (albumId != -1L && T::class == Media.UriMedia::class) {
                add(
                    OptionItem(
                        text = context.getString(R.string.move_album_to_trash),
                        enabled = !selectionState.value,
                        onClick = {
                            scope.launch {
                                handler.trashMedia(
                                    result = result,
                                    mediaList = mediaState.value.media as List<Media.UriMedia>,
                                    trash = true
                                )
                                navigateUp()
                            }
                            expandedDropDown.value = false
                        }
                    )
                )
            }
            add(
                OptionItem(
                    text = context.getString(R.string.favorites),
                    enabled = !selectionState.value,
                    onClick = {
                        navigate(Screen.FavoriteScreen.route)
                        expandedDropDown.value = false
                    }
                )
            )
            add(
                OptionItem(
                    text = context.getString(R.string.trash),
                    enabled = !selectionState.value,
                    onClick = {
                        navigate(Screen.TrashedScreen.route)
                        expandedDropDown.value = false
                    }
                )
            )
        }
    }
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val settingsOption = remember(selectionState.value) {
        mutableStateListOf(
            OptionItem(
                text = context.getString(R.string.settings_title),
                enabled = !selectionState.value,
                containerColor = tertiaryContainer,
                contentColor = onTertiaryContainer,
                onClick = {
                    navigate(Screen.SettingsScreen.route)
                    expandedDropDown.value = false
                }
            )
        )
    }
    IconButton(onClick = { expandedDropDown.value = !expandedDropDown.value }) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = stringResource(R.string.drop_down_cd)
        )
    }

    OptionSheet(
        state = appBottomSheetState,
        onDismiss = {
            expandedDropDown.value = false
        },
        optionList = arrayOf(optionList, settingsOption)
    )
}


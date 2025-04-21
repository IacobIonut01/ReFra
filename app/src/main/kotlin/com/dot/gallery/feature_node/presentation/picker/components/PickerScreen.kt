/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.picker.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia
import com.dot.gallery.feature_node.presentation.picker.PickerViewModel
import com.dot.gallery.feature_node.presentation.util.clear
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import com.dot.gallery.feature_node.presentation.util.size
import kotlinx.coroutines.launch

@Composable
fun PickerScreen(
    allowedMedia: AllowedMedia,
    allowSelection: Boolean,
    sendMediaAsResult: (List<Uri>) -> Unit,
    sendMediaAsMediaResult: (List<Media>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedAlbumIndex by remember { mutableLongStateOf(-1) }
    val selectedMedia = remember { mutableStateOf(setOf<Long>()) }
    val mediaVM = hiltViewModel<PickerViewModel>().apply {
        this.allowedMedia = allowedMedia
    }
    val albumsState by mediaVM.albumsState.collectAsStateWithLifecycle()
    val chipColors = InputChipDefaults.inputChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally
            ),
            contentPadding = PaddingValues(start = 32.dp)
        ) {
            items(
                items = albumsState.albums,
                key = { it.toString() }
            ) {
                val selected = selectedAlbumIndex == it.id
                InputChip(
                    onClick = {
                        selectedAlbumIndex = it.id
                        mediaVM.albumId = selectedAlbumIndex
                    },
                    colors = chipColors,
                    shape = RoundedCornerShape(16.dp),
                    label = {
                        val title = if (it.id == -1L) stringResource(R.string.all) else it.label
                        Text(text = title)
                    },
                    selected = selected,
                    border = null
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            PickerMediaScreen(
                mediaState = mediaVM.mediaState.value,
                selectedMedia = selectedMedia,
                allowSelection = allowSelection,
            )
            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp),
                visible = !allowSelection || selectedMedia.value.isNotEmpty(),
                enter = slideInVertically { it * 2 },
                exit = slideOutVertically { it * 2 }
            ) {
                val enabled = selectedMedia.value.isNotEmpty()
                val containerColor by animateColorAsState(
                    targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    label = "containerColor"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "contentColor"
                )
                val mediaState by mediaVM.mediaState.value.collectAsStateWithLifecycle()
                val selectedMediaList = mediaState.media.selectedMedia(selectedMedia)
                ExtendedFloatingActionButton(
                    text = {
                        if (allowSelection)
                            Text(text = "Add (${selectedMedia.size})")
                        else
                            Text(text = "Add")
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null
                        )
                    },
                    containerColor = containerColor,
                    contentColor = contentColor,
                    expanded = allowSelection,
                    onClick = {
                        if (enabled) {
                            scope.launch {
                                sendMediaAsResult(selectedMediaList.map { it.getUri() })
                                sendMediaAsMediaResult(selectedMediaList)
                            }
                        }
                    },
                    modifier = Modifier
                        .semantics {
                            contentDescription = "Add media"
                        }
                )
                BackHandler(selectedMedia.value.isNotEmpty()) {
                    selectedMedia.clear()
                }
            }
        }
    }
    BackHandler(selectedAlbumIndex != -1L) {
        selectedAlbumIndex = -1L
        mediaVM.albumId = -1L
    }
}
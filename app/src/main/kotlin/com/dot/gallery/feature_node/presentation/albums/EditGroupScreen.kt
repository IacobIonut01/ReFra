/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albums

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.formatSize
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupScreen(
    groupId: Long,
    onAddAlbum: (Long, Long) -> Unit,
    onRemoveAlbum: (Long, Long) -> Unit,
) {
    val distributor = LocalMediaDistributor.current
    val albumsState by distributor.albumsFlow.collectAsStateWithLifecycle()

    val group = albumsState.albumGroups.find { it.group.id == groupId }
    val memberIds = remember(group) {
        (group?.albums?.map { it.id } ?: emptyList()).toMutableStateList()
    }
    val allAlbums = albumsState.albums
    val memberIdsList = memberIds.toList()
    val inGroup = remember(allAlbums, memberIdsList) {
        val albumMap = allAlbums.associateBy { it.id }
        memberIdsList.mapNotNull { albumMap[it] }
    }
    val notInGroup = remember(allAlbums, memberIds.toList()) {
        allAlbums.filter { it.id !in memberIds }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_group) + " — " + (group?.group?.label ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    NavigationBackButton()
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPaddingValues ->
        LazyVerticalGrid(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                top = innerPaddingValues.calculateTopPadding(),
                bottom = innerPaddingValues.calculateBottomPadding() + 16.dp + 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (inGroup.isNotEmpty()) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "header_in_group"
                ) {
                    Text(
                        text = stringResource(R.string.n_albums, inGroup.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
            items(
                items = inGroup,
                key = { "in_${it.id}" }
            ) { album ->
                EditGroupAlbumItem(
                    modifier = Modifier.animateItem(),
                    album = album,
                    isSelected = true,
                    onClick = {
                        memberIds.remove(album.id)
                        onRemoveAlbum(groupId, album.id)
                    }
                )
            }
            if (notInGroup.isNotEmpty()) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "header_not_in_group"
                ) {
                    Text(
                        text = stringResource(R.string.other_albums),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
            items(
                items = notInGroup,
                key = { "out_${it.id}" }
            ) { album ->
                EditGroupAlbumItem(
                    modifier = Modifier.animateItem(),
                    album = album,
                    isSelected = false,
                    onClick = {
                        memberIds.add(album.id)
                        onAddAlbum(groupId, album.id)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
private fun EditGroupAlbumItem(
    modifier: Modifier = Modifier,
    album: Album,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val feedbackManager = rememberFeedbackManager()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed.value) 32.dp else 16.dp,
        label = "cornerRadius"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        label = "borderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 1.dp,
        label = "borderWidth"
    )
    val itemAlpha = if (isSelected) 1f else 0.5f

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .alpha(itemAlpha),
    ) {
        Box(
            modifier = Modifier.aspectRatio(1f)
        ) {
            if (album.isLocked) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = {
                                feedbackManager.vibrate()
                                onClick()
                            }
                        )
                        .padding(48.dp)
                )
            } else {
                GlideImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = {
                                feedbackManager.vibrate()
                                onClick()
                            }
                        ),
                    model = album.uri,
                    contentDescription = album.label,
                    contentScale = ContentScale.Crop,
                    requestBuilderTransform = {
                        val newRequest = it.centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        newRequest.thumbnail(newRequest.clone().sizeMultiplier(0.4f))
                            .signature(GlideInvalidation.signature(album))
                    }
                )
            }
        }
        Text(
            modifier = Modifier
                .padding(top = 12.dp)
                .padding(horizontal = 16.dp),
            text = album.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
        if (album.count > 0) {
            Text(
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                text = pluralStringResource(
                    id = R.plurals.item_count,
                    count = album.count.toInt(),
                    album.count
                ) + " (${formatSize(album.size)})",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

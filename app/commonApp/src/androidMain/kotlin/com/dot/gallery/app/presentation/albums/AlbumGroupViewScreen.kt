/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.albums

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.feature.albums.ui.AlbumGroupViewContent
import com.dot.gallery.core.ui.util.rememberActivityResult

/**
 * App-side shell for the album-group grid: delegates to
 * `:feature:albums` [AlbumGroupViewContent] and bridges the MediaStore
 * trash launcher and custom-cover picker.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumGroupViewScreen(
    groupId: Long,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onMoveAlbumToTrash: (ActivityResultLauncher<IntentSenderRequest>, Album) -> Unit,
    onIgnoreAlbum: (Album) -> Unit,
    onRemoveFromGroup: (Album) -> Unit,
    onRenameGroup: (AlbumGroupWithAlbums) -> Unit,
    onAddAlbums: ((groupId: Long) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val trashResult = rememberActivityResult()
    AlbumGroupViewContent(
        groupId = groupId,
        onAlbumClick = onAlbumClick,
        onAlbumLongClick = onAlbumLongClick,
        onMoveAlbumToTrash = { album -> onMoveAlbumToTrash(trashResult, album) },
        onIgnoreAlbum = onIgnoreAlbum,
        onRemoveFromGroup = onRemoveFromGroup,
        onRenameGroup = onRenameGroup,
        supportsTrash = SdkCompat.supportsTrash,
        onAddAlbums = onAddAlbums,
        thumbnailActions = rememberAlbumThumbnailActions(),
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )
}

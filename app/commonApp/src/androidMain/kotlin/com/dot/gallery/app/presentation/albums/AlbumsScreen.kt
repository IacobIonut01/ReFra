/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.albums

import android.content.ContentUris
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.net.toUri
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.presentation.components.FilterOption
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.startup.LocalStartupWorkGate
import com.dot.gallery.core.startup.StartupContentEffect
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.domain.model.AlbumSectionWithAlbums
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.feature.albums.ui.AlbumThumbnailActions
import com.dot.gallery.feature.albums.ui.AlbumsContent
import com.dot.gallery.feature.albums.ui.collections.CollectionThumbnailModel
import com.dot.gallery.app.presentation.picker.PickerActivityContract
import com.dot.gallery.app.presentation.main.MainScreenTopBar
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.ui.util.rememberActivityResult
import kotlinx.coroutines.launch
import com.dot.gallery.core.logging.printError
import com.dot.gallery.feature.albums.ui.AlbumsStartupGate

/**
 * App-side shell for the Albums tab: keeps the legacy signature while delegating
 * all content to `:feature:albums` ([AlbumsContent]). Platform seams bridged
 * here: the search-bar slot, the MediaStore trash launcher, the startup gate,
 * the custom-album-cover picker flow, provider brand icons and the
 * MediaStore-based collection thumbnail model.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumsScreen(
    paddingValues: PaddingValues,
    filterOptions: SnapshotStateList<FilterOption>,
    isScrolling: MutableState<Boolean>,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onMoveAlbumToTrash: (ActivityResultLauncher<IntentSenderRequest>, Album) -> Unit,
    onIgnoreAlbum: (Album) -> Unit,
    onLockAlbum: (Album) -> Unit,
    onGroupClick: (AlbumGroupWithAlbums) -> Unit = {},
    onRenameGroup: (AlbumGroupWithAlbums) -> Unit = {},
    onDeleteGroup: (AlbumGroupWithAlbums) -> Unit = {},
    onEditGroup: (AlbumGroupWithAlbums) -> Unit = {},
    onAddToGroup: ((Album) -> Unit)? = null,
    onToggleMergeSubfolders: ((Album) -> Unit)? = null,
    onMergeParentSubfolders: ((Album) -> Unit)? = null,
    onToggleMergedSubfolderDisplayMode: ((Album) -> Unit)? = null,
    onCollectionClick: (CollectionWithCount) -> Unit = {},
    onCollectionRename: (CollectionWithCount) -> Unit = {},
    onCollectionDelete: (CollectionWithCount) -> Unit = {},
    onCollectionTogglePin: (CollectionWithCount) -> Unit = {},
    onCollectionEditAlbums: (CollectionWithCount) -> Unit = {},
    onCreateCollection: () -> Unit = {},
    onMoveToSection: ((Album) -> Unit)? = null,
    onToggleSectionExpanded: (AlbumSectionWithAlbums, Boolean) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val trashResult = rememberActivityResult()
    val startupGate = LocalStartupWorkGate.current
    AlbumsContent(
        paddingValues = paddingValues,
        filterOptions = filterOptions,
        isScrolling = isScrolling,
        gridSizeIndex = rememberAlbumGridSize(),
        scrollToTopRoute = Screen.AlbumsScreen.route,
        topBar = {
            MainScreenTopBar(
                isScrolling = isScrolling,
            )
        },
        onAlbumClick = onAlbumClick,
        onAlbumLongClick = onAlbumLongClick,
        onMoveAlbumToTrash = { album -> onMoveAlbumToTrash(trashResult, album) },
        onIgnoreAlbum = onIgnoreAlbum,
        onLockAlbum = onLockAlbum,
        onGroupClick = onGroupClick,
        onRenameGroup = onRenameGroup,
        onDeleteGroup = onDeleteGroup,
        onEditGroup = onEditGroup,
        onAddToGroup = onAddToGroup,
        onToggleMergeSubfolders = onToggleMergeSubfolders,
        onMergeParentSubfolders = onMergeParentSubfolders,
        onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
        onCollectionClick = onCollectionClick,
        onCollectionRename = onCollectionRename,
        onCollectionDelete = onCollectionDelete,
        onCollectionTogglePin = onCollectionTogglePin,
        onCollectionEditAlbums = onCollectionEditAlbums,
        onCreateCollection = onCreateCollection,
        onMoveToSection = onMoveToSection,
        onToggleSectionExpanded = onToggleSectionExpanded,
        startupGate = startupGate?.let { gate ->
            AlbumsStartupGate { gate.awaitFirstContent() }
        },
        startupReadyEffect = { ready ->
            StartupContentEffect(route = Screen.AlbumsScreen(), ready = ready)
        },
        supportsTrash = SdkCompat.supportsTrash,
        thumbnailActions = rememberAlbumThumbnailActions(),
        providerIcon = { providerType, tint, modifier ->
            ProviderBrandIcon(
                providerType = providerType,
                tint = tint,
                modifier = modifier
            )
        },
        collectionThumbnailModel = collectionThumbnailModel,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )
}

/** MediaStore files URI model for collection cover thumbnails. */
private val collectionThumbnailModel: CollectionThumbnailModel = { thumbnailMediaId ->
    ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), thumbnailMediaId)
}

/**
 * App-side [AlbumThumbnailActions]: wires the "custom album cover" flow to
 * [com.dot.gallery.core.MediaHandler] and the in-app image picker. One picker
 * launcher is shared per call site; the target album is tracked until the
 * picker result arrives.
 */
@Composable
internal fun rememberAlbumThumbnailActions(): AlbumThumbnailActions {
    val handler = LocalMediaHandler.current
    val scope = rememberCoroutineScope()
    val pendingThumbnailAlbum = remember { mutableStateOf<Album?>(null) }
    val pickerLauncher = rememberLauncherForActivityResult(
        PickerActivityContract(
            allowMultiple = false,
            mediaType = "image/*"
        )
    ) { uriList ->
        val album = pendingThumbnailAlbum.value
        pendingThumbnailAlbum.value = null
        scope.launch {
            val newThumbnailUri = uriList.map { it.toUri() }.firstOrNull()
            if (album != null && newThumbnailUri != null) {
                handler.updateAlbumThumbnail(album.id, newThumbnailUri)
            } else if (album != null) {
                printError("No thumbnail selected for album: ${album.label}")
            }
        }
    }
    return remember(handler) {
        AlbumThumbnailActions(
            hasThumbnail = { albumId -> handler.hasAlbumThumbnail(albumId) },
            onPickThumbnail = { album ->
                pendingThumbnailAlbum.value = album
                pickerLauncher.launch(Unit)
            },
            onDeleteThumbnail = { albumId ->
                scope.launch { handler.deleteAlbumThumbnail(albumId) }
            },
        )
    }
}
